package com.mobileftp.ui.theme

import androidx.compose.ui.graphics.Color

object RaycastDarkColors : RaycastColorPalette {
    override val isDark: Boolean = true

    override val Canvas: Color          = Color(0xFF040506)
    override val Surface: Color         = Color(0xFF07080A)
    override val SurfaceElevated: Color = Color(0xFF0D0E10)
    override val SurfaceCard: Color     = Color(0xFF111214)
    override val SurfaceOverlay: Color  = Color(0xFF1A1B1E)

    override val BorderDefault: Color   = Color(0xFF1E2022)
    override val BorderSubtle: Color    = Color(0xFF141517)
    override val BorderFocus: Color     = Color(0xFF3B3C3F)

    override val TextPrimary: Color     = Color(0xFFE8E8E8)
    override val TextSecondary: Color   = Color(0xFF9C9C9D)
    override val TextTertiary: Color    = Color(0xFF5A5B5E)
    override val TextInverse: Color     = Color(0xFF111214)

    override val AccentStart: Color     = Color(0xFFFF6363)
    override val AccentEnd: Color       = Color(0xFFFF4500)
    override val AccentBlue: Color      = Color(0xFF4A9EFF)
    override val AccentGreen: Color     = Color(0xFF2ECC71)
    override val AccentAmber: Color     = Color(0xFFFFB547)
    override val AccentPurple: Color    = Color(0xFF9B6DFF)

    override val PrimaryButton: Color       = Color(0xFFE8E8E8)
    override val PrimaryButtonText: Color   = Color(0xFF111214)

    override val GlassSurface: Color    = Color(0x1AFFFFFF)
    override val GlassBorder: Color     = Color(0x26FFFFFF)
}
