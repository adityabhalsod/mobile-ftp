package com.mobileftp.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Generic palette interface — both Dark and Light implement these tokens. */
interface RaycastColorPalette {
    val isDark: Boolean

    val Canvas: Color
    val Surface: Color
    val SurfaceElevated: Color
    val SurfaceCard: Color
    val SurfaceOverlay: Color

    val BorderDefault: Color
    val BorderSubtle: Color
    val BorderFocus: Color

    val TextPrimary: Color
    val TextSecondary: Color
    val TextTertiary: Color
    val TextInverse: Color

    val AccentStart: Color
    val AccentEnd: Color
    val AccentBlue: Color
    val AccentGreen: Color
    val AccentAmber: Color
    val AccentPurple: Color

    val PrimaryButton: Color
    val PrimaryButtonText: Color

    val GlassSurface: Color
    val GlassBorder: Color
}

/** Provided by AppTheme — gives screens access to the active palette. */
val LocalRaycastColors = staticCompositionLocalOf<RaycastColorPalette> { RaycastDarkColors }

/** Convert palette → Material 3 ColorScheme so Material widgets inherit consistent colors. */
fun RaycastColorPalette.toMaterial3Scheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = AccentStart,
        onPrimary = TextInverse,
        secondary = AccentBlue,
        onSecondary = TextInverse,
        tertiary = AccentPurple,
        background = Surface,
        onBackground = TextPrimary,
        surface = SurfaceElevated,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceCard,
        onSurfaceVariant = TextSecondary,
        error = AccentStart,
        onError = TextInverse,
        outline = BorderDefault,
        outlineVariant = BorderSubtle
    )
} else {
    lightColorScheme(
        primary = AccentStart,
        onPrimary = Color.White,
        secondary = AccentBlue,
        onSecondary = Color.White,
        tertiary = AccentPurple,
        background = Canvas,
        onBackground = TextPrimary,
        surface = Surface,
        onSurface = TextPrimary,
        surfaceVariant = SurfaceCard,
        onSurfaceVariant = TextSecondary,
        error = AccentStart,
        onError = Color.White,
        outline = BorderDefault,
        outlineVariant = BorderSubtle
    )
}

/** Flame gradient — Raycast's signature accent. */
fun RaycastColorPalette.flameGradient(): Brush =
    Brush.horizontalGradient(listOf(AccentStart, AccentEnd))

fun RaycastColorPalette.progressGradient(): Brush =
    Brush.horizontalGradient(listOf(AccentBlue, AccentPurple))

/**
 * Inter + JetBrains Mono with ss03 stylistic set rendering.
 *
 * Fonts default to system (FontFamily.Default / FontFamily.Monospace) so the
 * project builds without binary .ttf assets. To upgrade visuals, drop these
 * files into `app/src/main/res/font/` and swap the families below to:
 *
 *     FontFamily(
 *         Font(R.font.inter_regular,  FontWeight.Normal),
 *         Font(R.font.inter_medium,   FontWeight.Medium),
 *         Font(R.font.inter_semibold, FontWeight.SemiBold),
 *         Font(R.font.inter_bold,     FontWeight.Bold)
 *     )
 *
 * See `docs/fonts.md` for download links and exact filenames.
 */
object RaycastType {
    val fontFamily: FontFamily = FontFamily.Default
    val monoFamily: FontFamily = FontFamily.Monospace

    val DisplayLarge = TextStyle(
        fontFamily = fontFamily, fontSize = 28.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp, lineHeight = 34.sp
    )
    val HeadlineMedium = TextStyle(
        fontFamily = fontFamily, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp, lineHeight = 24.sp
    )
    val TitleSmall = TextStyle(
        fontFamily = fontFamily, fontSize = 13.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp, lineHeight = 18.sp
    )
    val BodyMedium = TextStyle(
        fontFamily = fontFamily, fontSize = 14.sp, fontWeight = FontWeight.Normal,
        letterSpacing = 0.1.sp, lineHeight = 22.sp
    )
    val BodySmall = TextStyle(
        fontFamily = fontFamily, fontSize = 12.sp, fontWeight = FontWeight.Normal,
        letterSpacing = 0.15.sp, lineHeight = 18.sp
    )
    val LabelMono = TextStyle(
        fontFamily = monoFamily, fontSize = 11.sp, fontWeight = FontWeight.Medium,
        letterSpacing = 0.3.sp, lineHeight = 16.sp
    )
    val MetaMono = TextStyle(
        fontFamily = monoFamily, fontSize = 10.sp, fontWeight = FontWeight.Normal,
        letterSpacing = 0.4.sp, lineHeight = 14.sp
    )
}

object RaycastShapes {
    val CardRadius = 10.dp
    val InputRadius = 8.dp
    val ButtonRadius = 8.dp
    val PillRadius = 100.dp
    val ChipRadius = 6.dp
    val DialogRadius = 14.dp
}

object RaycastSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val section = 48.dp
}

object RaycastMotion {
    val Quick = tween<Float>(durationMillis = 120, easing = FastOutSlowInEasing)
    val Normal = tween<Float>(durationMillis = 220, easing = FastOutSlowInEasing)
    val Slow = tween<Float>(durationMillis = 380, easing = FastOutSlowInEasing)
    val SpringBounce = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )
}
