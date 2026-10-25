package com.futoonpay.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.DialogProperties
import java.util.UUID

internal data class PanelOverlay(val dismiss: () -> Unit, val content: @Composable () -> Unit)
internal class PanelOverlayStore { val items = mutableStateMapOf<String, PanelOverlay>(); val order = mutableStateListOf<String>() }
private val LocalOverlays = staticCompositionLocalOf<PanelOverlayStore?> { null }

@Composable internal fun PanelOverlayHost(content: @Composable () -> Unit) {
    val store = remember { PanelOverlayStore() }
    CompositionLocalProvider(LocalOverlays provides store) {
        Box(Modifier.fillMaxSize()) {
            content()
            store.order.toList().mapNotNull { id -> store.items[id]?.let { id to it } }.forEach { (id, entry) -> key(id) {
                BackHandler { entry.dismiss() }
                val motionEnabled = com.futoonpay.admin.appearance.ChatChrome.animationsEnabled(androidx.compose.ui.platform.LocalContext.current)
                val reveal = remember(id) { androidx.compose.animation.core.Animatable(if (motionEnabled) 0f else 1f) }
                LaunchedEffect(id, motionEnabled) { if (motionEnabled) reveal.animateTo(1f, androidx.compose.animation.core.tween(150)) else reveal.snapTo(1f) }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)).pointerInput(id) { detectTapGestures(onTap = { entry.dismiss() }) }, contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = reveal.value; scaleX = .98f + .02f * reveal.value; scaleY = scaleX }.pointerInput(id) { detectTapGestures(onTap = {}) }) { CompositionLocalProvider(com.futoonpay.admin.ui.LocalPayPanelChrome provides null) { entry.content() } }
                }
            } }
        }
    }
}

/** Original full-screen detail components are drawn within the floating panel bounds. */
@Composable internal fun PanelDialog(onDismissRequest: () -> Unit, properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false), content: @Composable () -> Unit) {
    val store = LocalOverlays.current
    if (store == null) { androidx.compose.ui.window.Dialog(onDismissRequest, properties = properties, content = content); return }
    val id = remember { UUID.randomUUID().toString() }
    val body by rememberUpdatedState(content)
    val dismiss by rememberUpdatedState(onDismissRequest)
    DisposableEffect(store, id) {
        store.items[id] = PanelOverlay({ dismiss() }, { body() })
        store.order.add(id)
        onDispose { store.order.remove(id); store.items.remove(id) }
    }
}

/** Keep merchant confirmations in the existing owned panel instead of a second window. */
@Composable internal fun PanelAlertDialog(
    shape: androidx.compose.ui.graphics.Shape,
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit,
) {
    if (LocalOverlays.current == null) {
        androidx.compose.material3.AlertDialog(onDismissRequest = onDismissRequest, shape = shape, title = title, text = text, confirmButton = confirmButton, dismissButton = dismissButton)
        return
    }
    PanelDialog(onDismissRequest) {
        Box(Modifier.fillMaxSize().imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth(), shape = shape, color = androidx.compose.material3.MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    androidx.compose.material3.ProvideTextStyle(androidx.compose.material3.MaterialTheme.typography.headlineSmall, content = title)
                    Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) { text() }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) { dismissButton(); confirmButton() }
                }
            }
        }
    }
}
