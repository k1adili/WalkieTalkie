package com.k1adili.walkietalkie.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.k1adili.walkietalkie.engine.AppSettings
import com.k1adili.walkietalkie.engine.UiState

private fun versionName(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        .getOrNull() ?: ""

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: UiState,
    btGranted: Boolean,
    onBack: () -> Unit,
    onName: (String) -> Unit,
    onTapToggle: (Boolean) -> Unit,
    onRxGain: (Float) -> Unit,
    onUseHeadset: (Boolean) -> Unit,
    onRequestBluetooth: () -> Unit,
    onKeepScreenOn: (Boolean) -> Unit,
    onTurnOff: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val version = remember { versionName(context) }
    var nameField by rememberSaveable { mutableStateOf(state.name) }

    Scaffold(
        containerColor = scheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .consumeWindowInsets(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Your name")
                OutlinedTextField(
                    value = nameField,
                    onValueChange = {
                        if (it.length <= AppSettings.MAX_NAME_LENGTH) {
                            nameField = it
                            onName(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Display name") },
                    supportingText = { Text("Others see this on their screen.") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(16.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("Talk button")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !state.tapToggle,
                        onClick = { onTapToggle(false) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) { Text("Hold to talk") }
                    SegmentedButton(
                        selected = state.tapToggle,
                        onClick = { onTapToggle(true) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    ) { Text("Tap to toggle") }
                }
                Text(
                    if (state.tapToggle) {
                        "Tap once to start talking, tap again to stop."
                    } else {
                        "Talk while you hold the button down. Release to listen."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { SectionTitle("Incoming volume boost") }
                    Text(
                        "${(state.rxGain * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = scheme.primary,
                    )
                }
                Slider(
                    value = state.rxGain,
                    onValueChange = onRxGain,
                    valueRange = AppSettings.MIN_GAIN..AppSettings.MAX_GAIN,
                )
                Text(
                    "Makes other people louder on top of your phone's volume. " +
                        "If it sounds distorted, lower it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SectionTitle("Use headset microphone")
                        Text(
                            "Audio route: ${state.route}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.primary,
                        )
                    }
                    Switch(checked = state.useHeadset, onCheckedChange = onUseHeadset)
                }
                Text(
                    "When a Bluetooth headset is connected, talk and listen through it instead of " +
                        "the phone. Bluetooth switches to call-quality audio while Walkie Talkie is on.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                if (state.useHeadset && !btGranted) {
                    TextButton(onClick = onRequestBluetooth) {
                        Text("Allow Bluetooth access", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    SectionTitle("Keep screen on")
                    Text(
                        "Stops the display from sleeping while the app is open.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.keepScreenOn, onCheckedChange = onKeepScreenOn)
            }

            Surface(shape = RoundedCornerShape(20.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionTitle("About")
                    Text(
                        "Works over Wi-Fi and phone hotspots. Audio only travels inside your local network.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Version $version", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                    Text("Made by Keyvan Adili", style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
                }
            }

            if (state.running) {
                OutlinedButton(
                    onClick = onTurnOff,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = scheme.error),
                ) { Text("Turn off Walkie Talkie") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
