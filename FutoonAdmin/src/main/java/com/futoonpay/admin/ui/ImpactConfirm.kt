package com.futoonpay.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.ui.theme.Danger
import com.futoonpay.admin.ui.theme.Line
import com.futoonpay.admin.ui.theme.Muted
import com.futoonpay.admin.ui.theme.Success
import kotlinx.coroutines.delay

/** Where a typed confirmation stands; the same wording is used by every high-impact dialog. */
internal enum class TypedState { EMPTY, MISMATCH, MATCHED }

internal fun typedState(input: String, target: String): TypedState = when {
    input.isBlank() -> TypedState.EMPTY
    input.trim() == target -> TypedState.MATCHED
    else -> TypedState.MISMATCH
}

/** Merchant actions that need a typed PID before the button wakes up: the ones that cut off money or access. */
internal fun requiresTypedPid(action: String, accountActive: Boolean, paymentOpen: Boolean, settlementOpen: Boolean): Boolean = when (action) {
    "toggleAccount" -> accountActive          // 封禁; unbanning is the safe direction
    "togglePay" -> paymentOpen                // 关闭支付
    "toggleSettle" -> settlementOpen          // 关闭结算
    "group" -> true
    else -> false
}

/** What the action will actually do, in the words the confirm dialog shows under 「影响范围」. Only states what the backend really does. */
internal fun merchantImpact(action: String, name: String, balance: String): String = when (action) {
    "toggleAccount" -> "$name 将立即无法下单和登录；账户余额${if (balance.isBlank()) "" else " ¥$balance"}保持不变。解封后恢复。"
    "togglePay" -> "$name 将无法再发起新的支付，直到重新开启。已有订单照常处理。"
    "toggleSettle" -> "$name 将无法再发起新的结算申请，直到重新开启。已提交的申请不受影响。"
    "group" -> "$name 的费率与权限将按新用户组执行；可随时改回。"
    else -> "此操作会立即改变商户业务权限。"
}

@Composable
internal fun ImpactBlock(text: String, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Text("⚠", fontSize = 13.sp); Text("影响范围", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            Text(text, fontSize = 14.sp)
        }
    }
}

/** Typed-confirmation field with the helper line from the design: neutral, red on mismatch, green when matched. */
@Composable
internal fun TypedConfirmField(label: String, target: String, value: String, onChange: (String) -> Unit, enabled: Boolean = true) {
    val state = typedState(value, target)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Muted, fontSize = 12.sp)
        OutlinedTextField(value, onChange, singleLine = true, enabled = enabled, isError = state == TypedState.MISMATCH, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            colors = ChatFieldColors())
        when (state) {
            TypedState.EMPTY -> Text("输入完全一致后才能确认", color = Muted, fontSize = 12.sp)
            TypedState.MISMATCH -> Text("和「$target」不一致，确认按钮不可点", color = Danger, fontSize = 12.sp)
            TypedState.MATCHED -> Text("已匹配", color = Success, fontSize = 12.sp)
        }
    }
}

/**
 * Seconds left before a matched confirmation can be sent. A cooldown of 0 means ready as soon as the text matches; it restarts
 * whenever the text stops matching so a pasted value cannot skip the pause.
 */
@Composable
internal fun rememberCooldown(matched: Boolean, seconds: Int): Int {
    var left by remember(matched) { mutableIntStateOf(if (matched) seconds else 0) }
    LaunchedEffect(matched) { while (left > 0) { delay(1000); left-- } }
    return if (matched) left else seconds
}

/** The label the confirm button shows for each stage, so every dialog speaks the same way. */
internal fun confirmLabel(ready: String, busy: String, submitting: Boolean, cooldownLeft: Int, matched: Boolean): String = when {
    submitting -> busy
    matched && cooldownLeft > 0 -> "${cooldownLeft} 秒后可确认"
    else -> ready
}
