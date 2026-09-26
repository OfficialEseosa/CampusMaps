package com.campusmaps.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.ui.theme.LocalCampusPalette

// S2: under the instruction while walking to a card-only door the user can open ("Tap your PantherCard at this door").
// Campus skin colour, card icon. The spoken line is the step's approach text (CoreRouter).
@Composable
fun CardDoorBanner(cardName: String) {
    val palette = LocalCampusPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.line)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("cardDoorBanner"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.CreditCard, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Text("Tap your $cardName at this door", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
