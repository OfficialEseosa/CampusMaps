package com.campusmaps.ui.theme

import androidx.compose.ui.graphics.Color

// Colors for anything drawn over the camera or on the black glasses screen.
// These do NOT change with the system theme.
object ArOverlayColors {
    val scrim = Color(0xE6141110)          // Instruction banner, 90% opaque
    val scrimMap = Color(0xD1141110)       // Minimap background, about 82% opaque
    val text = Color(0xFFFFFFFF)           // Main instruction text
    val textMuted = Color(0xFFD7CCC4)      // Distance and "Then:" line
    val arrowCore = Color(0xFFFFB27A)      // Fill of AR chevrons, next turn arrow, route line on map
    val arrowEdge = Color(0xFFC0612A)      // Outline and glow of the same
    val user = Color(0xFF4C8DF6)           // User dot, floor badge
    val destination = Color(0xFFF9F2ED)    // Pin label background
    val destinationRing = Color(0xFFC67C4E)// Destination ring on map and in AR
    val arrived = Color(0xFF7BC98A)        // "You have arrived" banner, arrived pin
    val onArrived = Color(0xFF10301A)      // Text and icons on the arrived banner
    val attention = Color(0xFFFFC247)      // Locate prompt border, reroute chip
    val onAttention = Color(0xFF2A1E00)
    val stop = Color(0xFFD84A3A)           // S3 Stop button
    val glassesMuted = Color(0xFFCFD8DC)   // S3 secondary text (never darker than 0xFFB0BEC5)
    val glassesOutline = Color(0xFFB0BEC5)

    // Map drawing
    val mapCorridor = Color(0xFF6B625C)

    // Debug card
    val debugCard = Color(0xF2101010)      // 95% opaque
    val debugHeader = Color(0xFFFFD54F)
    val debugLink = Color(0xFF90CAF9)
}
