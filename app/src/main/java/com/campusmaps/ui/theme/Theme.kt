package com.campusmaps.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// Wrap the light screens in this. With a [campus] palette the light scheme is the campus skin (CampusTheme.kt);
// dark keeps the clay dark scheme. S2, S3 and the debug card ignore it and use ArOverlayColors directly.
@Composable
fun CampusMapsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    campus: CampusPalette? = null,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        darkTheme -> DarkColors
        campus != null -> campusColorScheme(campus)
        else -> LightColors
    }
    CompositionLocalProvider(LocalCampusPalette provides (campus ?: CampusPalettes.GT)) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
