package com.k1adili.walkietalkie.engine

import android.content.Context
import android.os.Build
import kotlin.random.Random

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("walkie_settings", Context.MODE_PRIVATE)

    // Kept in its own file so backup rules can exclude it (ids must be unique per device).
    private val identity = context.getSharedPreferences("device_identity", Context.MODE_PRIVATE)

    val deviceId: Int = identity.getInt("id", 0).let { existing ->
        if (existing != 0) {
            existing
        } else {
            Random.nextInt(1, Int.MAX_VALUE).also { identity.edit().putInt("id", it).apply() }
        }
    }

    var name: String
        get() = prefs.getString("name", null)?.takeIf { it.isNotBlank() } ?: defaultName()
        set(value) {
            prefs.edit().putString("name", value).apply()
        }

    var channel: Int
        get() = prefs.getInt("channel", 1).coerceIn(1, Protocol.MAX_CHANNEL)
        set(value) {
            prefs.edit().putInt("channel", value).apply()
        }

    var tapToggle: Boolean
        get() = prefs.getBoolean("tap_toggle", false)
        set(value) {
            prefs.edit().putBoolean("tap_toggle", value).apply()
        }

    var keepScreenOn: Boolean
        get() = prefs.getBoolean("keep_screen_on", false)
        set(value) {
            prefs.edit().putBoolean("keep_screen_on", value).apply()
        }

    companion object {
        const val MAX_NAME_LENGTH = 20

        fun defaultName(): String = (Build.MODEL ?: "Phone").take(MAX_NAME_LENGTH)
    }
}
