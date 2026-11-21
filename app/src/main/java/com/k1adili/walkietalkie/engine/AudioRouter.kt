package com.k1adili.walkietalkie.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Sends audio to / from a Bluetooth headset instead of the phone speaker and mic.
 *
 * Bluetooth headphones only have a microphone in "call" (SCO/HFP) mode, so while a headset is
 * connected the app puts the phone into communication mode and selects the headset. That makes the
 * headset's own mic the recording source too. Audio quality is call-grade in this mode (a Bluetooth
 * limitation); the user can turn the feature off in Settings.
 */
class AudioRouter(context: Context, private val onChanged: () -> Unit) {

    enum class Route(val label: String) {
        PHONE("Phone"),
        BLUETOOTH("Bluetooth headset"),
        WIRED("Wired headset"),
    }

    private val appContext = context.applicationContext
    private val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    var route: Route = Route.PHONE
        private set

    @Volatile private var enabled = true
    private var started = false
    private var btActive = false
    private var activeDeviceId = -1

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
    }

    @Synchronized
    fun start() {
        if (started) return
        started = true
        am.registerAudioDeviceCallback(callback, handler)
        refresh()
    }

    @Synchronized
    fun stop() {
        if (!started) return
        started = false
        am.unregisterAudioDeviceCallback(callback)
        deactivateBluetooth()
        updateRoute(Route.PHONE)
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        refresh()
    }

    /** The headset's microphone, used as the preferred recording device while routed to Bluetooth. */
    fun bluetoothInput(): AudioDeviceInfo? =
        am.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { isBluetoothSco(it) }

    @Synchronized
    fun refresh() {
        if (!started) return
        val bt = if (enabled && hasBluetoothPermission()) findBluetoothDevice() else null
        if (bt != null && activateBluetooth(bt)) {
            updateRoute(Route.BLUETOOTH)
            return
        }
        deactivateBluetooth()
        updateRoute(if (hasWiredHeadset()) Route.WIRED else Route.PHONE)
    }

    // ------------------------------------------------------------------ internals

    private fun isBluetoothSco(d: AudioDeviceInfo): Boolean =
        d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && d.type == AudioDeviceInfo.TYPE_BLE_HEADSET)

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun findBluetoothDevice(): AudioDeviceInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.availableCommunicationDevices.firstOrNull { isBluetoothSco(it) }
        } else {
            // Before Android 12 a headset may show up as SCO or only as A2DP; either means "headset present".
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
        }

    private fun hasWiredHeadset(): Boolean =
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }

    @Suppress("DEPRECATION")
    private fun activateBluetooth(device: AudioDeviceInfo): Boolean {
        if (btActive && activeDeviceId == device.id) return true
        return try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            val ok = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.setCommunicationDevice(device)
            } else {
                am.startBluetoothSco()
                am.isBluetoothScoOn = true
                true
            }
            if (!ok) {
                am.mode = AudioManager.MODE_NORMAL
                false
            } else {
                btActive = true
                activeDeviceId = device.id
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not switch to Bluetooth headset", e)
            runCatching { am.mode = AudioManager.MODE_NORMAL }
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun deactivateBluetooth() {
        if (!btActive) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.clearCommunicationDevice()
            } else {
                am.isBluetoothScoOn = false
                am.stopBluetoothSco()
            }
            am.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "Could not release Bluetooth headset", e)
        }
        btActive = false
        activeDeviceId = -1
    }

    private fun updateRoute(newRoute: Route) {
        if (route != newRoute) {
            route = newRoute
            onChanged()
        }
    }

    private companion object {
        const val TAG = "AudioRouter"
    }
}
