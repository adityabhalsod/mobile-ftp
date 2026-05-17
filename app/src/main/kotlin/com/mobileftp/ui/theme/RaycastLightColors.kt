package com.mobileftp.ui.theme

import androidx.compose.ui.graphics.Color

object RaycastLightColors : RaycastColorPalette {
    override val isDark: Boolean = false

    override val Canvas: Color          = Color(0xFFF5F5F7)
    override val Surface: Color         = Color(0xFFFFFFFF)
    override val SurfaceElevated: Color = Color(0xFFF0F0F2)
    override val SurfaceCard: Color     = Color(0xFFE8E8EC)
    override val SurfaceOverlay: Color  = Color(0xFFE0E0E5)

    override val BorderDefault: Color   = Color(0xFFDDDDE0)
    override val BorderSubtle: Color    = Color(0xFFEAEAEC)
    override val BorderFocus: Color     = Color(0xFFAAAAAF)

    override val TextPrimary: Color     = Color(0xFF111214)
    override val TextSecondary: Color   = Color(0xFF6A6B70)
    override val TextTertiary: Color    = Color(0xFFAAAAAF)
    override val TextInverse: Color     = Color(0xFFE8E8E8)

    override val AccentStart: Color     = Color(0xFFE8503A)
    override val AccentEnd: Color       = Color(0xFFCC3300)
    override val AccentBlue: Color      = Color(0xFF1A7EE0)
    override val AccentGreen: Color     = Color(0xFF1BA85E)
    override val AccentAmber: Color     = Color(0xFFD4900A)
    override val AccentPurple: Color    = Color(0xFF7A4FD6)

    override val PrimaryButton: Color       = Color(0xFF111214)
    override val PrimaryButtonText: Color   = Color(0xFFE8E8E8)

    override val GlassSurface: Color    = Color(0x1A000000)
    override val GlassBorder: Color     = Color(0x26000000)
}
