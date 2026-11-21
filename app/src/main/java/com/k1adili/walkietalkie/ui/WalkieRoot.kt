package com.k1adili.walkietalkie.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.k1adili.walkietalkie.engine.WalkieEngine

private enum class Screen { MAIN, SETTINGS }

@Composable
fun WalkieRoot(
    engine: WalkieEngine,
    micGranted: Boolean,
    btGranted: Boolean,
    permanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onRequestBluetooth: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
) {
    val state by engine.state.collectAsStateWithLifecycle()
    val levelState = engine.level.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }

    val view = LocalView.current
    DisposableEffect(state.keepScreenOn, micGranted) {
        view.keepScreenOn = state.keepScreenOn && micGranted
        onDispose { view.keepScreenOn = false }
    }

    if (!micGranted) {
        PermissionScreen(
            permanentlyDenied = permanentlyDenied,
            onAllow = onRequestPermission,
            onOpenSettings = onOpenAppSettings,
        )
    } else {
        BackHandler(enabled = screen == Screen.SETTINGS) { screen = Screen.MAIN }
        Crossfade(targetState = screen, label = "screen") { current ->
            when (current) {
                Screen.MAIN -> MainScreen(
                    state = state,
                    levelState = levelState,
                    onChannel = engine::setChannel,
                    onTalkStart = engine::startTalk,
                    onTalkStop = engine::stopTalk,
                    onTalkToggle = engine::toggleTalk,
                    onOpenSettings = { screen = Screen.SETTINGS },
                    onTurnOn = onStartService,
                )

                Screen.SETTINGS -> SettingsScreen(
                    state = state,
                    btGranted = btGranted,
                    onBack = { screen = Screen.MAIN },
                    onName = engine::setName,
                    onTapToggle = engine::setTapToggle,
                    onRxGain = engine::setRxGain,
                    onUseHeadset = engine::setUseHeadset,
                    onRequestBluetooth = onRequestBluetooth,
                    onKeepScreenOn = engine::setKeepScreenOn,
                    onTurnOff = {
                        onStopService()
                        screen = Screen.MAIN
                    },
                )
            }
        }
    }
}
