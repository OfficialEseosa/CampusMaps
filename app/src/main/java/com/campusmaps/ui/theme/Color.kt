package com.campusmaps.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Light scheme: warm "clay" brand. Used by S1, S1b and Settings in light mode.
val LightColors = lightColorScheme(
    primary = Color(0xFFA85F33),              // Route button, Done, selected chips, switches
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6DDCC),     // Best route card, selected destination row
    onPrimaryContainer = Color(0xFF3A1A08),
    secondary = Color(0xFF6E5A4E),            // Method icon on other route cards
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF1E4DA),   // Icon tiles on non best cards
    onSecondaryContainer = Color(0xFF2B1D15),
    tertiary = Color(0xFF2F6B7A),             // Locked entrance banner icon
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3ECF2),    // Locked banner and simulated time chip background
    onTertiaryContainer = Color(0xFF0F3540),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF231A15),
    surface = Color(0xFFFFF8F4),              // Screen background
    onSurface = Color(0xFF231A15),
    surfaceVariant = Color(0xFFEFE2D9),
    onSurfaceVariant = Color(0xFF5A4F48),     // Secondary text, section labels
    surfaceContainer = Color(0xFFF5EBE4),     // Non best route cards, list rows
    surfaceContainerHigh = Color(0xFFEFE2D9), // Search field, switch track when off
    outline = Color(0xFF85746A),
    outlineVariant = Color(0xFFD8C8BE),       // Card borders
    error = Color(0xFFB3261E),                // "No route" text only
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    // Roles the handoff does not list but M3 components read (snackbar, sheets, menus, dialogs).
    // Left unset they fall back to the baseline purple-grey, so derive them from the clay tokens.
    inversePrimary = Color(0xFFFFB68C),         // Dark primary; snackbar action
    inverseSurface = Color(0xFF392E29),         // Snackbar container
    inverseOnSurface = Color(0xFFFFEDE5),       // Snackbar text
    surfaceBright = Color(0xFFFFF8F4),
    surfaceDim = Color(0xFFE8D7CE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1EA),    // Bottom sheets
    surfaceContainerHighest = Color(0xFFE9DCD3),
)

// Dark scheme: same roles, tuned for night recordings.
val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB68C),
    onPrimary = Color(0xFF512300),
    primaryContainer = Color(0xFF7E4424),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFDDC2B3),
    onSecondary = Color(0xFF3E2D23),
    secondaryContainer = Color(0xFF55443A),
    onSecondaryContainer = Color(0xFFFADDCD),
    tertiary = Color(0xFF9CD0DE),
    onTertiary = Color(0xFF003640),
    tertiaryContainer = Color(0xFF124D5A),
    onTertiaryContainer = Color(0xFFD3ECF2),
    background = Color(0xFF1A120E),
    onBackground = Color(0xFFF0DFD6),
    surface = Color(0xFF1A120E),
    onSurface = Color(0xFFF0DFD6),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B8),
    surfaceContainer = Color(0xFF271E19),
    surfaceContainerHigh = Color(0xFF322823),
    outline = Color(0xFFA08D82),
    outlineVariant = Color(0xFF52443C),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    // See LightColors: extra roles derived from the clay tokens so no baseline purple leaks in.
    inversePrimary = Color(0xFFA85F33),         // Light primary
    inverseSurface = Color(0xFFF0DFD6),         // Light onSurface
    inverseOnSurface = Color(0xFF392E29),
    surfaceBright = Color(0xFF42372F),
    surfaceDim = Color(0xFF1A120E),
    surfaceContainerLowest = Color(0xFF140D09),
    surfaceContainerLow = Color(0xFF231A15),
    surfaceContainerHighest = Color(0xFF3D322D),
)
