package com.k1adili.walkietalkie

import android.app.Application
import com.k1adili.walkietalkie.engine.AppSettings
import com.k1adili.walkietalkie.engine.WalkieEngine

class WalkieApp : Application() {
    val engine: WalkieEngine by lazy { WalkieEngine(this, AppSettings(this)) }
}
