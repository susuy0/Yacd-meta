package com.futoonpay.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.futoonpay.admin.ui.theme.*

data class AppLaunchTarget(val tab: MainTab? = null, val channelId: String = "", val merchantId: String = "")

@Composable
internal fun BrandMark(size: androidx.compose.ui.unit.Dp = 48.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(Green), contentAlignment = Alignment.Center) {
        Text("富", color = Ink, fontWeight = FontWeight.Bold, fontSize = (size.value * .48f).sp)
    }
}
