package com.campusmaps.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),   // Small tags ("Fastest", simulated time chip)
    small = RoundedCornerShape(12.dp),       // Icon tiles
    medium = RoundedCornerShape(16.dp),      // Destination rows, banners
    large = RoundedCornerShape(20.dp),       // Route cards, AR banner
    extraLarge = RoundedCornerShape(28.dp),  // Bottom sheet, pill buttons
)

// Spacing tokens. Use these instead of random numbers.
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp   // Screen side padding on S1 and S1b
    val xxl = 28.dp  // Bottom padding above the primary button
}
