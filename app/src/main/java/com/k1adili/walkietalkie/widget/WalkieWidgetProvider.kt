package com.k1adili.walkietalkie.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.k1adili.walkietalkie.MainActivity
import com.k1adili.walkietalkie.R
import com.k1adili.walkietalkie.WalkieApp
import com.k1adili.walkietalkie.engine.Protocol
import com.k1adili.walkietalkie.service.WalkieService

/**
 * Home-screen widget: status, channel +/- and a tap-to-talk button.
 *
 * A widget can't detect "press and hold", so its talk button toggles (tap to start, tap to stop).
 * It only works while Walkie Talkie is on; when it is off, the talk and power buttons open the app
 * (Android only lets an app turn the microphone service on while the app is visible).
 */
class WalkieWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetManager.updateAppWidget(appWidgetIds, buildViews(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val engine = (context.applicationContext as WalkieApp).engine
        when (intent.action) {
            ACTION_PREV -> engine.setChannel(wrap(engine.state.value.channel - 1))
            ACTION_NEXT -> engine.setChannel(wrap(engine.state.value.channel + 1))
            ACTION_TALK -> engine.toggleTalk()
            ACTION_POWER -> {
                context.stopService(Intent(context, WalkieService::class.java))
                engine.stop()
            }
            else -> return
        }
        updateAll(context)
    }

    companion object {
        private const val ACTION_PREV = "com.k1adili.walkietalkie.widget.PREV"
        private const val ACTION_NEXT = "com.k1adili.walkietalkie.widget.NEXT"
        private const val ACTION_TALK = "com.k1adili.walkietalkie.widget.TALK"
        private const val ACTION_POWER = "com.k1adili.walkietalkie.widget.POWER"

        private fun wrap(channel: Int): Int =
            ((channel - 1 + Protocol.MAX_CHANNEL) % Protocol.MAX_CHANNEL) + 1

        fun updateAll(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, WalkieWidgetProvider::class.java))
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, buildViews(app))
        }

        private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, WalkieWidgetProvider::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun openApp(context: Context, code: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                code,
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        private fun buildViews(context: Context): RemoteViews {
            val st = (context.applicationContext as WalkieApp).engine.state.value
            val here = st.peers.count { it.channel == st.channel }
            val views = RemoteViews(context.packageName, R.layout.widget_walkie)

            val status = when {
                !st.running -> "Off · tap power to open"
                st.transmitting -> "You're on air"
                st.talkerName != null -> "${st.talkerName} is talking"
                st.localIp == null -> "No local network"
                here == 0 -> "Online · nobody here yet"
                else -> "Online · $here on this channel"
            }
            views.setTextViewText(R.id.w_status, status)
            views.setTextViewText(R.id.w_channel, "CH ${st.channel}")

            val talkBg = when {
                st.transmitting -> R.drawable.widget_talk_on
                st.talkerName != null -> R.drawable.widget_talk_rx
                else -> R.drawable.widget_talk_idle
            }
            views.setInt(R.id.w_talk, "setBackgroundResource", talkBg)
            views.setContentDescription(
                R.id.w_talk,
                if (st.transmitting) "Stop talking" else "Start talking",
            )

            views.setOnClickPendingIntent(R.id.w_header, openApp(context, 10))
            views.setOnClickPendingIntent(R.id.w_prev, broadcast(context, ACTION_PREV, 11))
            views.setOnClickPendingIntent(R.id.w_next, broadcast(context, ACTION_NEXT, 12))
            if (st.running) {
                views.setOnClickPendingIntent(R.id.w_talk, broadcast(context, ACTION_TALK, 13))
                views.setOnClickPendingIntent(R.id.w_power, broadcast(context, ACTION_POWER, 14))
            } else {
                views.setOnClickPendingIntent(R.id.w_talk, openApp(context, 13))
                views.setOnClickPendingIntent(R.id.w_power, openApp(context, 14))
            }
            return views
        }
    }
}
