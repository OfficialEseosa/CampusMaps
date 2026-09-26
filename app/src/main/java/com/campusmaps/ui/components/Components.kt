package com.campusmaps.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.ui.icons.AppIcons

// App bar used by S1, S1b and S4: 64 dp tall, sits under the status bar.
// Reset and Settings stay quiet (onSurfaceVariant) on purpose so judges do not tap them.
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    tags: List<String> = emptyList(),
    onBack: (() -> Unit)? = null,
    onReset: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onTitleLongPress: (() -> Unit)? = null,
    // Optional third icon after Settings (S1's "Campus map"). With it the start gap shrinks so "DEMO" still fits at 411 dp.
    onMap: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(start = 8.dp, end = if (onMap != null) 0.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(AppIcons.arrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            Spacer(Modifier.width(if (onMap != null) 4.dp else 12.dp))
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .then(
                    // The only hidden gesture in the app: long press toggles the debug overlay.
                    if (onTitleLongPress != null) {
                        Modifier.pointerInput(onTitleLongPress) { detectTapGestures(onLongPress = { onTitleLongPress() }) }
                    } else {
                        Modifier
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            tags.forEachIndexed { index, tag ->
                SmallTag(
                    text = tag,
                    container = if (index == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    content = if (index == 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        if (onReset != null) {
            IconButton(onClick = onReset, modifier = Modifier.size(48.dp)) {
                Icon(AppIcons.restartAlt, contentDescription = "Reset", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onSettings != null) {
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                Icon(AppIcons.settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onMap != null) {
            IconButton(onClick = onMap, modifier = Modifier.size(48.dp).testTag("s1MapButton")) {
                Icon(AppIcons.map, contentDescription = "Campus map", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// Small rounded tag: building code, "DEMO", "Fastest", "Student shortcut".
@Composable
fun SmallTag(
    text: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(13.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
    }
}

// Section labels like "Recent and demo destinations".
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

// Square tile with an icon in the middle (destination rows, route cards).
@Composable
fun IconTile(
    icon: ImageVector,
    container: Color,
    content: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 12.dp,
    iconSize: Dp = size * 0.5f,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = content, modifier = Modifier.size(iconSize))
    }
}

// "Avoid stairs" row: icon, label and an M3 Switch. The whole row is the tap target.
@Composable
fun AvoidStairsRow(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, height: Dp = 56.dp) {
    SwitchRow(
        icon = AppIcons.accessible,
        label = "Avoid stairs",
        checked = checked,
        onChange = onChange,
        modifier = modifier,
        height = height,
    )
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    supporting: String? = null,
    height: Dp = 56.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // onCheckedChange = null: the row handles the tap, so there is one target, not two.
        // Off track is surfaceContainerHigh per the handoff; the M3 default (surfaceContainerHighest)
        // is not in our scheme and would fall back to the baseline lavender grey.
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        )
    }
}

// Full width, fully rounded primary button (58 dp on S1, 56 dp on S4).
@Composable
fun PrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 58.dp,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// A tappable 56 dp text row with a leading icon and a trailing chevron ("Add a shortcut").
@Composable
fun NavigationRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Icon(AppIcons.chevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Status pill for shortcut submissions (section 16.3): 28 dp tall, fully rounded, 11 sp bold.
@Composable
fun StatusPill(text: String, icon: ImageVector, container: Color, content: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(28.dp)
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = content, maxLines = 1, style = MaterialTheme.typography.labelSmall)
    }
}

// A thin outlined border used by several rows.
@Composable
fun outlineBorder(width: Dp = 1.dp, color: Color = MaterialTheme.colorScheme.outlineVariant) = BorderStroke(width, color)
