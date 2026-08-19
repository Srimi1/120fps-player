package dev.fps.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A single dark scheme, deliberately not following the system light/dark setting.
 *
 * A video player has one correct background and it is black: any lift in the letterbox
 * bars is visible against the picture, and on the OLED panels this project targets,
 * true black costs no power. The previous theme was `Theme.Material.Light`, which put
 * a white page behind the video.
 */
private val PlayerColors = darkColorScheme(
    primary = Color(0xFF5AD1FF),
    onPrimary = Color(0xFF00202E),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF1B1035),
    tertiary = Color(0xFFFFB020),
    onTertiary = Color(0xFF2A1A00),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF3A0A0A),
    background = Color.Black,
    onBackground = Color(0xFFE8ECF2),
    surface = Color(0xFF0B0E12),
    onSurface = Color(0xFFE8ECF2),
    surfaceVariant = Color(0xFF1A1F26),
    onSurfaceVariant = Color(0xFF9AA4B2),
    outline = Color(0xFF39414D),
)

/** Colours the HUD uses that are not Material roles. */
object HudColors {
    val Good = Color(0xFF4ADE80)
    val Warn = Color(0xFFFFB020)
    val Bad = Color(0xFFFF6B6B)
    val Idle = Color(0xFF9AA4B2)
    val Scrim = Color(0xCC05070A)
}

private val PlayerTypography = Typography().let { base ->
    base.copy(
        // Diagnostics numbers are read while they change. A monospaced face stops the
        // whole row from reflowing every time a digit width changes.
        labelSmall = base.labelSmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            letterSpacing = 0.sp,
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.sp,
        ),
    )
}

@Composable
fun PlayerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PlayerColors,
        typography = PlayerTypography,
        content = content,
    )
}
