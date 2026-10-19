package com.futoonpay.admin.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

/** A snapshot of the hosting Telegram account's current theme, local to this view tree. */
data class FutoonPalette(
    val dark: Boolean = false,
    val surface: Color = Color(0xFFFFFFFF),
    val background: Color = Color(0xFFF1F3F5),
    val text: Color = Color(0xFF17212B),
    val secondaryText: Color = Color(0xFF657580),
    val accent: Color = Color(0xFF2481CC),
    val accentText: Color = Color(0xFF1671B8),
    val buttonText: Color = Color.White,
    val divider: Color = Color(0xFFE1E5E9),
    val error: Color = Color(0xFFCF3434),
    val success: Color = Color(0xFF26844B),
) {
    companion object {
        @JvmStatic fun parse(json: String): FutoonPalette {
            val value = JSONObject(json)
            val fallback = FutoonPalette()
            fun color(key: String, other: Color) = if (value.has(key)) Color(value.getLong(key).toInt()) else other
            return FutoonPalette(value.optBoolean("dark"), color("surface", fallback.surface), color("background", fallback.background),
                color("text", fallback.text), color("secondaryText", fallback.secondaryText), color("accent", fallback.accent),
                color("accentText", fallback.accentText), color("buttonText", fallback.buttonText), color("divider", fallback.divider),
                color("error", fallback.error), color("success", fallback.success))
        }
    }
}
val LocalFutoonPalette = staticCompositionLocalOf { FutoonPalette() }
val Ink: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val Green: Color @Composable get() = MaterialTheme.colorScheme.primary
val GreenDark: Color @Composable get() = LocalFutoonPalette.current.accentText
val Mist: Color @Composable get() = MaterialTheme.colorScheme.background
val Line: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
val Muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Danger: Color @Composable get() = MaterialTheme.colorScheme.error
// Pending/uncertain states also carry explicit text and icons; no fixed amber overrides a custom theme.
val Warning: Color @Composable get() = LocalFutoonPalette.current.accentText
val GreenTint: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val CardColor: Color @Composable get() = MaterialTheme.colorScheme.surface
val ButtonText: Color @Composable get() = MaterialTheme.colorScheme.onPrimary

private fun readableButtonText(accent: Color, proposed: Color): Color {
    fun contrast(a: Color, b: Color): Float = (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
    if (contrast(accent, proposed) >= 4.5f) return proposed
    return if (contrast(accent, Color.White) >= contrast(accent, Color.Black)) Color.White else Color.Black
}

@Composable
fun FutoonTheme(palette: FutoonPalette = LocalFutoonPalette.current, content: @Composable () -> Unit) {
    val colors = remember(palette) {
        val base = if (palette.dark) darkColorScheme() else lightColorScheme()
        base.copy(primary = palette.accent, onPrimary = readableButtonText(palette.accent, palette.buttonText),
            primaryContainer = lerp(palette.surface, palette.accent, if (palette.dark) .20f else .10f), onPrimaryContainer = palette.text,
            secondary = palette.accent, onSecondary = readableButtonText(palette.accent, palette.buttonText),
            background = palette.background, onBackground = palette.text, surface = palette.surface, onSurface = palette.text,
            surfaceVariant = palette.background, onSurfaceVariant = palette.secondaryText,
            surfaceContainer = palette.surface, surfaceContainerLow = palette.surface, surfaceContainerHigh = palette.surface,
            surfaceContainerHighest = palette.background, outline = palette.divider, outlineVariant = palette.divider, error = palette.error)
    }
    val accentContrast = (maxOf(palette.surface.luminance(), palette.accentText.luminance()) + .05f) / (minOf(palette.surface.luminance(), palette.accentText.luminance()) + .05f)
    val safePalette = if (accentContrast < 4.5f) palette.copy(accentText = palette.text) else palette
    CompositionLocalProvider(LocalFutoonPalette provides safePalette) {
        MaterialTheme(colorScheme = colors,
            typography = Typography(bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp), bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp), bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp)),
            shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(16.dp)),
            content = { CompositionLocalProvider(LocalContentColor provides colors.onSurface) { content() } })
    }
}
