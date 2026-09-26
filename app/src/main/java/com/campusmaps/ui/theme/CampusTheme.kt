package com.campusmaps.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Campus skin from the CampusMaps redesign (claude.ai/design, CampusMaps.dc.html, THEMES).
// Georgia Tech is gold on navy, Georgia State is blue on white. The whole light flow (S0 campus, S0b buildings,
// S1, S1b, Settings) takes these colours; S2, S3 and the 3D preview stay dark and use ArOverlayColors + [glow].
data class CampusPalette(
    val accent: Color,      // Header, primary button, selected mode tile
    val onAccent: Color,
    val headerText: Color,  // Text on the accent header
    val ring: Color,        // Concentric rings on the accent header
    val ghost: Color,       // Big faded campus code behind the header
    val chip: Color,        // Round buttons and pills sitting on the accent
    val surface: Color,     // Screen background
    val card: Color,        // Rows and cards
    val cardBorder: Color,
    val soft: Color,        // Selected row, best route card
    val ink: Color,         // Main text
    val muted: Color,       // Secondary text, section labels
    val line: Color,        // Selected borders, "Fastest" tag, switch track on
    val deep: Color,        // Building tile background
    val bar: Color,         // Floor bars on the building tile
    val glow: Color,        // Destination floor in the 3D preview, AR accents
    val glowFill: Color,
)

object CampusPalettes {
    val GT = CampusPalette(
        accent = Color(0xFFEAAA00), onAccent = Color(0xFF003057), headerText = Color(0xFF003057),
        ring = Color(0x1F003057), ghost = Color(0x17003057), chip = Color(0x1F003057),
        surface = Color(0xFFFFFAF0), card = Color(0xFFF7EFDF), cardBorder = Color(0xFFE9DDC3), soft = Color(0xFFFCEFC7),
        ink = Color(0xFF1D1A14), muted = Color(0xFF665D4C), line = Color(0xFF003057), deep = Color(0xFF003057), bar = Color(0xFFEAAA00),
        glow = Color(0xFFF5C542), glowFill = Color(0x38F5C542),
    )
    val GSU = CampusPalette(
        accent = Color(0xFF0039A6), onAccent = Color(0xFFFFFFFF), headerText = Color(0xFFFFFFFF),
        ring = Color(0x21FFFFFF), ghost = Color(0x14FFFFFF), chip = Color(0x29FFFFFF),
        surface = Color(0xFFF6F8FD), card = Color(0xFFEDF1FA), cardBorder = Color(0xFFD6DFF1), soft = Color(0xFFDFE8FC),
        ink = Color(0xFF0D1733), muted = Color(0xFF4C5876), line = Color(0xFF0039A6), deep = Color(0xFF00205B), bar = Color(0xFF9DB9FF),
        glow = Color(0xFF7EA6FF), glowFill = Color(0x387EA6FF),
    )

    fun of(id: com.campusmaps.data.campus.CampusId): CampusPalette =
        if (id == com.campusmaps.data.campus.CampusId.GT) GT else GSU

    // Switch track when off (same on both campuses).
    val switchOff = Color(0xFFCFC8BB)

    // The neutral campus picker (S0) sits on this, before a campus is chosen.
    val pickerSurface = Color(0xFFFAF7F1)
    val pickerInk = Color(0xFF1A1712)
    val pickerMuted = Color(0xFF5E574C)
}

// The palette of the campus in use. Screens read colours that have no Material role (accent header, glow) from here.
val LocalCampusPalette = staticCompositionLocalOf { CampusPalettes.GT }

// Material roles from a campus palette, so stock M3 parts (Switch, TextField, sheets, menus) follow the campus.
fun campusColorScheme(p: CampusPalette): ColorScheme = lightColorScheme(
    primary = p.accent,
    onPrimary = p.onAccent,
    primaryContainer = p.soft,
    onPrimaryContainer = p.ink,
    secondary = p.line,
    onSecondary = Color.White,
    secondaryContainer = p.cardBorder,
    onSecondaryContainer = p.ink,
    tertiary = LightColors.tertiary,
    onTertiary = LightColors.onTertiary,
    tertiaryContainer = LightColors.tertiaryContainer,
    onTertiaryContainer = LightColors.onTertiaryContainer,
    background = p.surface,
    onBackground = p.ink,
    surface = p.surface,
    onSurface = p.ink,
    surfaceVariant = p.card,
    onSurfaceVariant = p.muted,
    surfaceContainer = p.card,
    surfaceContainerHigh = p.card,
    surfaceContainerHighest = p.cardBorder,
    surfaceContainerLow = p.surface,
    surfaceContainerLowest = Color.White,
    surfaceBright = p.surface,
    surfaceDim = p.cardBorder,
    outline = p.muted,
    outlineVariant = p.cardBorder,
    error = LightColors.error,
    onError = LightColors.onError,
    errorContainer = LightColors.errorContainer,
    onErrorContainer = LightColors.onErrorContainer,
    inversePrimary = p.glow,
    inverseSurface = p.ink,
    inverseOnSurface = p.surface,
)
