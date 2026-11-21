package com.k1adili.walkietalkie

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.k1adili.walkietalkie.engine.WalkieEngine
import com.k1adili.walkietalkie.service.WalkieService
import com.k1adili.walkietalkie.ui.WalkieRoot
import com.k1adili.walkietalkie.ui.theme.WalkieTheme
import com.k1adili.walkietalkie.widget.WalkieWidgetProvider

class MainActivity : ComponentActivity() {

    private val engine: WalkieEngine get() = (application as WalkieApp).engine

    private var micGranted by mutableStateOf(false)
    private var btGranted by mutableStateOf(false)
    private var permanentlyDenied by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            micGranted = hasMic()
            btGranted = hasBluetooth()
            permanentlyDenied = !micGranted &&
                !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            if (micGranted) startWalkieService()
            engine.refreshAudioRoute()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        micGranted = hasMic()
        btGranted = hasBluetooth()
        setContent {
            WalkieTheme {
                WalkieRoot(
                    engine = engine,
                    micGranted = micGranted,
                    btGranted = btGranted,
                    permanentlyDenied = permanentlyDenied,
                    onRequestPermission = ::askForPermissions,
                    onRequestBluetooth = ::askForBluetooth,
                    onOpenAppSettings = ::openAppSettings,
                    onStartService = ::startWalkieService,
                    onStopService = ::stopWalkieService,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        micGranted = hasMic()
        btGranted = hasBluetooth()
        if (micGranted) startWalkieService()
    }

    override fun onStop() {
        // Never leave the microphone open once the screen is gone.
        engine.stopTalk()
        WalkieWidgetProvider.updateAll(this)
        super.onStop()
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasBluetooth(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun askForPermissions() {
        val perms = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun askForBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }

    private fun startWalkieService() {
        try {
            ContextCompat.startForegroundService(this, Intent(this, WalkieService::class.java))
        } catch (e: Exception) {
            Log.e("MainActivity", "Could not start service", e)
        }
    }

    private fun stopWalkieService() {
        stopService(Intent(this, WalkieService::class.java))
        engine.stop()
    }
}
