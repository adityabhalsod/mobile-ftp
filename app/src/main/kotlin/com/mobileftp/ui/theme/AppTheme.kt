package com.mobileftp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

enum class ThemePreference { SYSTEM, LIGHT, DARK }

@Composable
fun MobileFtpTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemInDark = isSystemInDarkTheme()
    val isDark = when (themePreference) {
        ThemePreference.SYSTEM -> systemInDark
        ThemePreference.DARK -> true
        ThemePreference.LIGHT -> false
    }
    val colors: RaycastColorPalette = if (isDark) RaycastDarkColors else RaycastLightColors

    CompositionLocalProvider(LocalRaycastColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterial3Scheme(),
            typography = androidx.compose.material3.Typography(
                displayLarge = RaycastType.DisplayLarge,
                headlineMedium = RaycastType.HeadlineMedium,
                titleSmall = RaycastType.TitleSmall,
                bodyMedium = RaycastType.BodyMedium,
                bodySmall = RaycastType.BodySmall,
                labelSmall = RaycastType.LabelMono
            ),
            content = content
        )
    }
}
