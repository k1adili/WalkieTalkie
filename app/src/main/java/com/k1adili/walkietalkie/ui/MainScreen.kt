package com.k1adili.walkietalkie.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.k1adili.walkietalkie.engine.Peer
import com.k1adili.walkietalkie.engine.Protocol
import com.k1adili.walkietalkie.engine.UiState

@Composable
fun MainScreen(
    state: UiState,
    levelState: State<Float>,
    onChannel: (Int) -> Unit,
    onTalkStart: () -> Unit,
    onTalkStop: () -> Unit,
    onTalkToggle: () -> Unit,
    onOpenSettings: () -> Unit,
    onTurnOn: () -> Unit,
) {
    val peersHere = remember(state.peers, state.channel) {
        state.peers.filter { it.channel == state.channel }
    }
    val mode = when {
        !state.running -> PttMode.OFF
        state.transmitting -> PttMode.TX
        state.talkerName != null -> PttMode.RX
        else -> PttMode.IDLE
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { inner ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(inner)) {
            // Shrinks the talk button on short screens so nothing gets pushed off.
            val buttonArea: Dp = (maxHeight * 0.36f).coerceIn(200.dp, 300.dp)

            Column(Modifier.fillMaxSize()) {
                Header(state, onOpenSettings)
                Spacer(Modifier.height(12.dp))
                ChannelPicker(state, onChannel)
                Spacer(Modifier.height(20.dp))
                PeopleSection(state, peersHere, onTurnOn)
                Spacer(Modifier.weight(1f))
                StatusText(mode, state)
                Spacer(Modifier.height(8.dp))
                PushToTalkButton(
                    mode = mode,
                    tapToggle = state.tapToggle,
                    levelState = levelState,
                    onTalkStart = onTalkStart,
                    onTalkStop = onTalkStop,
                    onTalkToggle = onTalkToggle,
                    areaSize = buttonArea,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun Header(state: UiState, onOpenSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Walkie Talkie",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            StatusPill(state)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun StatusPill(state: UiState) {
    val scheme = MaterialTheme.colorScheme
    val (color, text) = when {
        !state.running -> scheme.outline to "Off"
        state.localIp == null -> scheme.tertiary to "No local network"
        else -> scheme.secondary to "Online · ${state.localIp}"
    }
    val dot by animateColorAsState(color, tween(300), label = "dot")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.5.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun ChannelPicker(state: UiState, onChannel: (Int) -> Unit) {
    Column {
        SectionLabel("CHANNEL", Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        val listState = rememberLazyListState()
        LaunchedEffect(Unit) {
            listState.scrollToItem((state.channel - 2).coerceAtLeast(0))
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(Protocol.MAX_CHANNEL) { index ->
                val ch = index + 1
                ChannelChip(
                    channel = ch,
                    selected = ch == state.channel,
                    hasPeers = state.peers.any { it.channel == ch },
                    talking = state.peers.any { it.channel == ch && it.talking },
                    onClick = { onChannel(ch) },
                )
            }
        }
    }
}

@Composable
private fun ChannelChip(
    channel: Int,
    selected: Boolean,
    hasPeers: Boolean,
    talking: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val label: @Composable () -> Unit = {
        Text("$channel", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
    if (hasPeers) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            leadingIcon = {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (talking) scheme.secondary else scheme.primary),
                )
            },
        )
    } else {
        FilterChip(selected = selected, onClick = onClick, label = label)
    }
}

@Composable
private fun PeopleSection(state: UiState, peersHere: List<Peer>, onTurnOn: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("ON THIS CHANNEL", Modifier.weight(1f))
            if (state.running && peersHere.isNotEmpty()) {
                SectionLabel("${peersHere.size + 1} ONLINE")
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            !state.running -> InfoCard(
                icon = Icons.Rounded.PowerSettingsNew,
                title = "Walkie Talkie is off",
                body = "Turn it on to find people nearby.",
                actionLabel = "Turn on",
                onAction = onTurnOn,
            )

            state.localIp == null -> InfoCard(
                icon = Icons.Rounded.WifiOff,
                title = "Not on a local network",
                body = "Join the same Wi-Fi as your friends, or turn on a hotspot and have them connect to it.",
            )

            peersHere.isEmpty() -> InfoCard(
                icon = Icons.Rounded.Groups,
                title = "Nobody here yet",
                body = "Open Walkie Talkie on another phone on this network. They'll show up automatically.",
            )

            else -> LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "me") {
                    PersonChip(name = state.name, talking = state.transmitting, isYou = true)
                }
                items(peersHere, key = { it.id }) { peer ->
                    PersonChip(name = peer.name, talking = peer.talking, isYou = false)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceContainer,
        modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(scheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                if (actionLabel != null && onAction != null) {
                    TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) {
                        Text(actionLabel, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

private val AvatarPalette = listOf(
    Color(0xFF4C7CF0),
    Color(0xFF8A5CF6),
    Color(0xFFD6538F),
    Color(0xFF2F9E6F),
    Color(0xFFD9772B),
    Color(0xFF1F9AA8),
)

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

@Composable
private fun PersonChip(name: String, talking: Boolean, isYou: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val ring by animateColorAsState(
        targetValue = if (talking) (if (isYou) scheme.tertiary else scheme.secondary) else Color.Transparent,
        animationSpec = tween(200),
        label = "ring",
    )
    val scale by rememberInfiniteTransition(label = "talk").animateFloat(
        initialValue = 1f,
        targetValue = if (talking) 1.08f else 1f,
        animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
        label = "talkScale",
    )
    val fill = if (isYou) scheme.primaryContainer else AvatarPalette[Math.floorMod(name.hashCode(), AvatarPalette.size)]
    val textColor = if (isYou) scheme.onPrimaryContainer else Color.White

    Column(Modifier.width(68.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(58.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .border(3.dp, ring, CircleShape)
                .padding(5.dp)
                .clip(CircleShape)
                .background(fill),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (isYou) "ME" else initials(name),
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (isYou) "You" else name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = scheme.onSurface,
        )
    }
}

@Composable
private fun StatusText(mode: PttMode, state: UiState) {
    val scheme = MaterialTheme.colorScheme
    val text = when (mode) {
        PttMode.OFF -> "Turned off"
        PttMode.TX -> "You're on air"
        PttMode.RX -> "${state.talkerName} is talking"
        PttMode.IDLE -> if (state.tapToggle) "Tap to talk" else "Hold to talk"
    }
    val color = when (mode) {
        PttMode.TX -> scheme.tertiary
        PttMode.RX -> scheme.secondary
        else -> scheme.onSurface
    }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Crossfade(targetState = text to color, label = "status") { (t, c) ->
            Text(
                t,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = c,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            if (state.route == "Phone") "Channel ${state.channel}" else "Channel ${state.channel} · ${state.route}",
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
        )
    }
}
