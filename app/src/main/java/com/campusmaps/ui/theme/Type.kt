package com.campusmaps.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.campusmaps.R

// The one and only font family in the app.
val Sora = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_medium, FontWeight.Medium),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold),
    Font(R.font.sora_extrabold, FontWeight.ExtraBold),
)

// Map Sora onto the Material 3 type scale.
val AppTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp), // "Where to?", "To Room 608"
    headlineMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),   // App bar title
    titleMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),  // Room name, entrance name
    bodyLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),  // Switch labels
    bodyMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp),   // Primary buttons
    labelMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 18.sp),  // Section labels
    labelSmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 14.sp),   // "Fastest" tag, badges
)

// Extra styles that are bigger than the M3 scale. Use these by name.
object AppTextStyles {
    val etaBest = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-1).sp)   // S1b best card ETA
    val etaOther = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = (-0.8).sp)// S1b other card ETA (minimum 28)
    val arInstruction = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp)  // S2 banner (owner 2026-09-26: 28 was too big)
    val arDistance = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)                           // "in 16 m"
    val arThen = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 15.sp)                                 // "Then: ..."
    val arrivedHeadline = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
    val glassesInstruction = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp) // S3 (minimum 30)
    val glassesNext = TextStyle(fontFamily = Sora, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 29.sp)
    val floorBadge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
    val debugMono = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
}
