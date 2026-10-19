package com.futoonpay.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .28f)).pointerInput(id) { detectTapGestures(onTap = { entry.dismiss() }) }, contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().pointerInput(id) { detectTapGestures(onTap = {}) }) { CompositionLocalProvider(com.futoonpay.admin.ui.LocalPayPanelChrome provides null) { entry.content() } }
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
