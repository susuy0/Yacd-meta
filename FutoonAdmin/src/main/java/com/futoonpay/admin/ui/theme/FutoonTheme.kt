package com.futoonpay.admin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle

val Ink = Color(0xFF10231A)
val Green = Color(0xFF0B7A4B)
val GreenDark = Color(0xFF0B7A4B)
val Mist = Color(0xFFF3F6F4)
val Line = Color(0xFFDCE4DF)
val Muted = Color(0xFF5F7066)
val Danger = Color(0xFFC73535)
val Warning = Color(0xFF9A650B)
val GreenTint = Color(0xFFE3F3EA)

private val FutoonColors = lightColorScheme(
    primary = GreenDark,
    onPrimary = Color.White,
    primaryContainer = GreenTint,
    onPrimaryContainer = Ink,
    secondary = Green,
    onSecondary = Color.White,
    background = Mist,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Mist,
    surfaceContainer = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Mist,
    onSurfaceVariant = Muted,
    outline = Line,
    outlineVariant = Line,
    error = Danger,
)

@Composable
fun FutoonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FutoonColors,
        typography = Typography(bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp), bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(10.dp),
        ),
        content = content,
    )
}
