package com.k1adili.walkietalkie.engine

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "WalkieEngine"
private const val BEACON_EVERY_TICKS = 8 // 8 x 250 ms = every 2 s
private const val TICK_MS = 250L
private const val TALK_TIMEOUT_MS = 600L
private const val PEER_TIMEOUT_MS = 7_000L
private const val PLAYBACK_TAIL_MS = 500L
private const val TX_GAIN = 1.8f
private const val SOFT_KNEE = 20_000f

/** Applies gain, then gently rounds off peaks above the knee instead of hard-clipping them. */
private fun softGain(sample: Int, gain: Float): Short {
    val v = sample * gain
    val a = abs(v)
    val out = if (a <= SOFT_KNEE) {
        a
    } else {
        SOFT_KNEE + (32767f - SOFT_KNEE) * tanh((a - SOFT_KNEE) / (32767f - SOFT_KNEE))
    }
    return (if (v < 0f) -out else out).toInt().toShort()
}

private data class PeerInfo(
    val id: Int,
    val name: String,
    val channel: Int,
    val address: InetAddress,
    val lastSeen: Long,
    val lastAudio: Long,
    val talking: Boolean,
)

/**
 * Owns the UDP socket, peer discovery, microphone capture and playback.
 * Lives in the Application; [com.k1adili.walkietalkie.service.WalkieService] starts/stops it
 * so it keeps running as a foreground service.
 */
class WalkieEngine(context: Context, private val settings: AppSettings) {

    private val appContext = context.applicationContext
    private val myId = settings.deviceId

    private val _state = MutableStateFlow(
        UiState(
            name = settings.name,
            channel = settings.channel,
            tapToggle = settings.tapToggle,
            keepScreenOn = settings.keepScreenOn,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _level = MutableStateFlow(0f)

    /** 0..1 loudness of whatever is currently heard/transmitted; drives the button animation. */
    val level: StateFlow<Float> = _level.asStateFlow()

    private val publishLock = Any()
    private val peers = ConcurrentHashMap<Int, PeerInfo>()
    private val buffers = ConcurrentHashMap<Int, JitterBuffer>()

    // Socket sends must never happen on the main thread.
    private val io: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "walkie-io").apply { isDaemon = true }
    }

    @Volatile private var routeVersion = 0
    @Volatile private var rxGain = settings.rxGain
    @Volatile private var useHeadset = settings.useHeadset

    private val router = AudioRouter(appContext) {
        routeVersion++
        publish()
    }

    @Volatile private var running = false
    @Volatile private var transmitting = false
    @Volatile private var txSession = 0
    @Volatile private var channel = settings.channel
    @Volatile private var name = settings.name
    @Volatile private var tapToggle = settings.tapToggle
    @Volatile private var keepScreenOn = settings.keepScreenOn
    @Volatile private var net = NetInfo.EMPTY
    @Volatile private var lastRxAt = 0L
    @Volatile private var socket: DatagramSocket? = null

    private var rxThread: Thread? = null
    private var playThread: Thread? = null
    private var tickThread: Thread? = null
    @Volatile private var micThread: Thread? = null

    private var multicastLock: WifiManager.MulticastLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // ---------------------------------------------------------------- lifecycle

    @Synchronized
    fun start() {
        if (running) return
        val sock = try {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(Protocol.PORT))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not open UDP socket", e)
            return
        }
        socket = sock
        running = true
        acquireLocks()
        router.setEnabled(useHeadset)
        router.start()
        net = scanNetwork()
        rxThread = thread(name = "walkie-rx", isDaemon = true) { rxLoop(sock) }
        playThread = thread(name = "walkie-play", isDaemon = true) { playLoop() }
        tickThread = thread(name = "walkie-tick", isDaemon = true) { tickLoop() }
        publish()
        io.execute { sendBeacon() }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        transmitting = false
        running = false
        val sock = socket
        socket = null
        val bye = Protocol.bye(myId, channel)
        val byeTargets = net.broadcasts + peers.values.map { it.address }
        io.execute {
            try {
                for (to in byeTargets) send(bye, to, sock)
            } finally {
                sock?.close()
            }
        }
        rxThread?.interrupt()
        playThread?.interrupt()
        tickThread?.interrupt()
        router.stop()
        releaseLocks()
        peers.clear()
        buffers.clear()
        _level.value = 0f
        publish()
    }

    // ---------------------------------------------------------------- UI actions

    fun setChannel(newChannel: Int) {
        val ch = newChannel.coerceIn(1, Protocol.MAX_CHANNEL)
        if (ch == channel) return
        channel = ch
        settings.channel = ch
        buffers.clear()
        publish()
        io.execute { sendBeacon() }
    }

    fun setName(raw: String) {
        val n = raw.trim().take(AppSettings.MAX_NAME_LENGTH).ifEmpty { AppSettings.defaultName() }
        if (n == name) return
        name = n
        settings.name = n
        publish()
        io.execute { sendBeacon() }
    }

    fun setTapToggle(enabled: Boolean) {
        tapToggle = enabled
        settings.tapToggle = enabled
        publish()
    }

    fun setKeepScreenOn(enabled: Boolean) {
        keepScreenOn = enabled
        settings.keepScreenOn = enabled
        publish()
    }

    fun setRxGain(gain: Float) {
        val g = gain.coerceIn(AppSettings.MIN_GAIN, AppSettings.MAX_GAIN)
        rxGain = g
        settings.rxGain = g
        publish()
    }

    fun setUseHeadset(enabled: Boolean) {
        useHeadset = enabled
        settings.useHeadset = enabled
        router.setEnabled(enabled)
        publish()
    }

    /** Call after the Bluetooth permission was granted so a connected headset is picked up. */
    fun refreshAudioRoute() = router.refresh()

    fun startTalk() {
        if (!running || transmitting) return
        val granted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        transmitting = true
        buffers.clear()
        val session = ++txSession
        val txChannel = channel
        val previous = micThread
        micThread = thread(name = "walkie-mic", isDaemon = true) {
            try {
                previous?.join(500)
            } catch (e: InterruptedException) {
                return@thread
            }
            recordLoop(session, txChannel)
        }
        publish()
    }

    fun stopTalk() {
        if (!transmitting) return
        transmitting = false
        publish()
    }

    fun toggleTalk() {
        if (transmitting) stopTalk() else startTalk()
    }

    // ---------------------------------------------------------------- receive

    private fun rxLoop(sock: DatagramSocket) {
        val buf = ByteArray(2048)
        val dp = DatagramPacket(buf, buf.size)
        while (running) {
            try {
                dp.length = buf.size
                sock.receive(dp)
                handle(dp)
            } catch (e: SocketException) {
                if (!running) break
                Log.w(TAG, "receive failed", e)
                try {
                    Thread.sleep(200)
                } catch (ie: InterruptedException) {
                    break
                }
            } catch (e: IOException) {
                if (!running) break
                Log.w(TAG, "receive failed", e)
            } catch (e: RuntimeException) {
                Log.w(TAG, "bad packet", e)
            }
        }
    }

    private fun handle(dp: DatagramPacket) {
        val pkt = Protocol.parse(dp.data, dp.offset, dp.length) ?: return
        if (pkt.id == myId) return
        val from = dp.address ?: return
        val now = SystemClock.elapsedRealtime()

        when (pkt.type) {
            Protocol.TYPE_HELLO -> {
                val peerName = Protocol.parseHello(pkt)?.ifBlank { null } ?: "Someone"
                var isNew = false
                peers.compute(pkt.id) { _, old ->
                    if (old == null) isNew = true
                    PeerInfo(
                        id = pkt.id,
                        name = peerName,
                        channel = pkt.channel,
                        address = from,
                        lastSeen = now,
                        lastAudio = old?.lastAudio ?: 0L,
                        talking = old?.talking ?: false,
                    )
                }
                // Answer new arrivals right away so both sides see each other without waiting 2 s.
                if (isNew) {
                    val reply = Protocol.hello(myId, channel, name)
                    io.execute { send(reply, from) }
                }
                publish()
            }

            Protocol.TYPE_AUDIO -> {
                val payloadLen = pkt.length - Protocol.HEADER - 2
                if (payloadLen != Protocol.FRAME_SAMPLES) return
                var changed = false
                peers.compute(pkt.id) { _, old ->
                    if (old == null || !old.talking || old.channel != pkt.channel) changed = true
                    PeerInfo(
                        id = pkt.id,
                        name = old?.name ?: "Someone",
                        channel = pkt.channel,
                        address = from,
                        lastSeen = now,
                        lastAudio = now,
                        talking = true,
                    )
                }
                if (changed) publish()
                // Half duplex: nothing is played while I am talking, and other channels are ignored.
                if (pkt.channel != channel || transmitting) return
                val start = pkt.offset + Protocol.HEADER + 2
                val frame = ShortArray(Protocol.FRAME_SAMPLES) { MuLaw.decode(pkt.data[start + it]) }
                buffers.getOrPut(pkt.id) { JitterBuffer() }.put(frame)
                lastRxAt = now
            }

            Protocol.TYPE_END -> {
                peers.computeIfPresent(pkt.id) { _, old -> old.copy(talking = false, lastSeen = now) }
                buffers[pkt.id]?.flush()
                publish()
            }

            Protocol.TYPE_BYE -> {
                peers.remove(pkt.id)
                buffers.remove(pkt.id)
                publish()
            }
        }
    }

    // ---------------------------------------------------------------- housekeeping

    private fun tickLoop() {
        var ticks = 0
        try {
            while (running) {
                Thread.sleep(TICK_MS)
                ticks++
                val now = SystemClock.elapsedRealtime()
                if (ticks % BEACON_EVERY_TICKS == 0) {
                    net = scanNetwork()
                    sendBeacon()
                }
                for (p in peers.values) {
                    if (p.talking && now - p.lastAudio > TALK_TIMEOUT_MS) {
                        peers.replace(p.id, p, p.copy(talking = false))
                    }
                    if (now - maxOf(p.lastSeen, p.lastAudio) > PEER_TIMEOUT_MS) {
                        peers.remove(p.id, p)
                        buffers.remove(p.id)
                    }
                }
                publish()
            }
        } catch (e: InterruptedException) {
            // stopping
        }
    }

    /** Presence beacon: broadcast for discovery + unicast to known peers (works where broadcast is filtered). */
    private fun sendBeacon() {
        if (!running) return
        val data = Protocol.hello(myId, channel, name)
        broadcast(data)
        for (p in peers.values) send(data, p.address)
    }

    private fun broadcast(data: ByteArray) {
        val targets = net.broadcasts
        if (targets.isEmpty()) {
            send(data, InetAddress.getByAddress(byteArrayOf(-1, -1, -1, -1)))
        } else {
            for (t in targets) send(data, t)
        }
    }

    private fun send(data: ByteArray, to: InetAddress, sock: DatagramSocket? = socket) {
        try {
            sock?.send(DatagramPacket(data, data.size, to, Protocol.PORT))
        } catch (e: IOException) {
            // Network hiccup or interface went away: the next beacon/frame simply tries again.
        } catch (e: SecurityException) {
            // No network permission/route right now.
        }
    }

    // ---------------------------------------------------------------- microphone

    @SuppressLint("MissingPermission")
    private fun recordLoop(session: Int, txChannel: Int) {
        var rec: AudioRecord? = null
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            val frameBytes = Protocol.FRAME_SAMPLES * 2
            val minBuf = AudioRecord.getMinBufferSize(
                Protocol.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuf <= 0) {
                Log.e(TAG, "AudioRecord not supported: $minBuf")
                return
            }
            rec = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                Protocol.SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, frameBytes * 4),
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialise")
                return
            }
            if (router.route == AudioRouter.Route.BLUETOOTH) {
                router.bluetoothInput()?.let { rec.setPreferredDevice(it) }
            }
            rec.startRecording()

            val pcm = ShortArray(Protocol.FRAME_SAMPLES)
            val payload = ByteArray(Protocol.FRAME_SAMPLES)
            var seq = 0
            while (running && transmitting && txSession == session) {
                var filled = 0
                while (filled < pcm.size && transmitting && txSession == session) {
                    val n = rec.read(pcm, filled, pcm.size - filled)
                    if (n < 0) throw IOException("AudioRecord.read failed: $n")
                    filled += n
                }
                if (filled < pcm.size) break
                for (i in pcm.indices) {
                    pcm[i] = softGain(pcm[i].toInt(), TX_GAIN)
                    payload[i] = MuLaw.encode(pcm[i])
                }
                emitLevel(rms(pcm))
                val packet = Protocol.audio(myId, txChannel, seq++ and 0xFFFF, payload)
                for (p in peers.values) {
                    if (p.channel == txChannel) send(packet, p.address)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "record loop ended", e)
        } finally {
            try {
                rec?.stop()
            } catch (e: IllegalStateException) {
                // never started
            }
            rec?.release()
            val end = Protocol.end(myId, txChannel)
            repeat(3) {
                for (p in peers.values) {
                    if (p.channel == txChannel) send(end, p.address)
                }
            }
            if (txSession == session) {
                transmitting = false
                _level.value = 0f
                publish()
            }
        }
    }

    // ---------------------------------------------------------------- playback

    private fun buildTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            Protocol.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val size = maxOf(minBuf, Protocol.FRAME_SAMPLES * 2 * 3) // at least 60 ms
        // Through a Bluetooth headset audio must use the call path; on the phone, media volume is louder.
        val usage = if (router.route == AudioRouter.Route.BLUETOOTH) {
            AudioAttributes.USAGE_VOICE_COMMUNICATION
        } else {
            AudioAttributes.USAGE_MEDIA
        }
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Protocol.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(size)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    private fun createTrack(): AudioTrack? {
        return try {
            val t = buildTrack()
            if (t.state == AudioTrack.STATE_INITIALIZED) {
                t
            } else {
                t.release()
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not create AudioTrack", e)
            null
        }
    }

    /**
     * While anyone is talking this loop writes exactly one 20 ms frame per iteration (silence if
     * the jitter buffer has nothing). The blocking AudioTrack.write paces it in real time, which
     * is what lets the jitter buffer actually fill up instead of being drained instantly.
     * When the audio route changes (headset connected/removed) the track is rebuilt while idle.
     */
    private fun playLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        var track: AudioTrack = createTrack() ?: return
        var builtVersion = routeVersion
        val acc = IntArray(Protocol.FRAME_SAMPLES)
        val mix = ShortArray(Protocol.FRAME_SAMPLES)
        var playing = false
        try {
            while (running) {
                val now = SystemClock.elapsedRealtime()
                val active = now - lastRxAt < PLAYBACK_TAIL_MS || buffers.values.any { it.size() > 0 }
                if (!active) {
                    if (playing) {
                        track.pause()
                        track.flush()
                        playing = false
                        if (!transmitting) _level.value = 0f
                    }
                    if (builtVersion != routeVersion) {
                        track.release()
                        track = createTrack() ?: return
                        builtVersion = routeVersion
                    }
                    Thread.sleep(10)
                    continue
                }
                if (!playing) {
                    track.play()
                    playing = true
                }
                acc.fill(0)
                for (b in buffers.values) {
                    val f = b.poll() ?: continue
                    for (i in acc.indices) acc[i] += f[i]
                }
                val gain = rxGain
                for (i in mix.indices) mix[i] = softGain(acc[i], gain)
                val written = track.write(mix, 0, mix.size)
                if (written < 0) {
                    Log.w(TAG, "AudioTrack.write failed: $written")
                    Thread.sleep(20)
                }
                if (!transmitting) emitLevel(rms(mix))
            }
        } catch (e: InterruptedException) {
            // stopping
        } finally {
            try {
                track.stop()
            } catch (e: IllegalStateException) {
                // already stopped or released
            }
            track.release()
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun rms(frame: ShortArray): Float {
        var sum = 0.0
        for (s in frame) sum += s.toDouble() * s
        val r = sqrt(sum / frame.size) / 32768.0
        return (r * 5.0).toFloat().coerceIn(0f, 1f)
    }

    private fun emitLevel(value: Float) {
        _level.value = _level.value * 0.55f + value * 0.45f
    }

    private fun publish() {
        synchronized(publishLock) {
            val list = peers.values
                .map { Peer(it.id, it.name, it.channel, it.talking) }
                .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
            val talker = list.firstOrNull { it.talking && it.channel == channel }?.name
            _state.value = UiState(
                running = running,
                name = name,
                channel = channel,
                peers = list,
                transmitting = transmitting,
                talkerName = talker,
                localIp = net.localIps.firstOrNull(),
                tapToggle = tapToggle,
                keepScreenOn = keepScreenOn,
                route = router.route.label,
                useHeadset = useHeadset,
                rxGain = rxGain,
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        try {
            val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("walkietalkie:multicast").apply {
                setReferenceCounted(false)
                acquire()
            }
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            } else {
                WifiManager.WIFI_MODE_FULL_HIGH_PERF
            }
            wifiLock = wifi.createWifiLock(mode, "walkietalkie:wifi").apply {
                setReferenceCounted(false)
                acquire()
            }
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "walkietalkie:cpu").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not acquire power/Wi-Fi locks", e)
        }
    }

    private fun releaseLocks() {
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
        wifiLock = null
        wakeLock = null
    }
}
