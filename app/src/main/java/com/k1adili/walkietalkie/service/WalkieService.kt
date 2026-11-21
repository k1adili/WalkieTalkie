package com.k1adili.walkietalkie.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.k1adili.walkietalkie.widget.WalkieWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.k1adili.walkietalkie.MainActivity
import com.k1adili.walkietalkie.R
import com.k1adili.walkietalkie.WalkieApp

private data class WidgetKey(
    val running: Boolean,
    val channel: Int,
    val hereCount: Int,
    val transmitting: Boolean,
    val talker: String?,
    val online: Boolean,
)

/** Keeps the engine alive (and the microphone allowed) while the app is in the background. */
class WalkieService : Service() {

    private val engine get() = (application as WalkieApp).engine
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Walkie Talkie status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while Walkie Talkie is listening on your local network"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        // Keep the home-screen widget in sync with what the engine is doing.
        scope.launch {
            engine.state
                .map { st ->
                    WidgetKey(
                        running = st.running,
                        channel = st.channel,
                        hereCount = st.peers.count { it.channel == st.channel },
                        transmitting = st.transmitting,
                        talker = st.talkerName,
                        online = st.localIp != null,
                    )
                }
                .distinctUntilChanged()
                .collect { WalkieWidgetProvider.updateAll(applicationContext) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            engine.stop()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } catch (e: Exception) {
            // e.g. microphone permission missing or started from the background
            Log.e(TAG, "Could not start foreground service", e)
            stopSelf()
            return START_NOT_STICKY
        }
        engine.start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        engine.stop()
        scope.cancel()
        WalkieWidgetProvider.updateAll(applicationContext)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, WalkieService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_walkie)
            .setContentTitle("Walkie Talkie is on")
            .setContentText("Listening on your local network")
            .setContentIntent(open)
            .addAction(0, "Turn off", stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val TAG = "WalkieService"
        private const val CHANNEL_ID = "walkie_status"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_STOP = "com.k1adili.walkietalkie.STOP"
    }
}
