package com.campusmaps.ui.ar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.campusmaps.loc.BuildingToWorld
import com.campusmaps.loc.Vec3
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARSceneScope

/**
 * Where the card door sign stands, in ARCore world. Pure, JVM-tested in CardDoorSignTest.
 * On the outdoor leg the entrance's terrain anchor wins (the sign floats at the door while the user is still outside);
 * after the hand-over the building transform places it on the entrance node. Neither: null, and the sign is parked.
 */
object CardDoorPlacement {
    /** Post height, metres (like the destination post). */
    const val POST_M = 1.3
    /** Label centre above the ground, metres. */
    const val LABEL_M = 1.6
    /** Where the sign waits, out of view, while nothing places it. It is never removed (see [CardDoorSignNodes]). */
    val PARKED = Vec3(0.0, -1000.0, 0.0)

    fun foot(sign: ArDoorSign, t: BuildingToWorld?, outdoorDoor: Vec3?): Vec3? =
        outdoorDoor ?: t?.toWorld(sign.x, sign.y, sign.floor)
}

// Amber like the next-turn arrow, red like the destination label; text white.
private val AMBER_BG = Color(0xF2E08A00)
private val RED_BG = Color(0xE6C62828)
private val AMBER_POST = Color(0xFFFFB300)
private val RED_POST = Color(0xFFEF5350)

/** Holds the last sign seen in this AR view, so a null input for a frame does not remove the text node. */
private class SignLatch { var sign: ArDoorSign? = null }

/**
 * The card door sign: a thin post [CardDoorPlacement.POST_M] tall and a camera-facing label at
 * [CardDoorPlacement.LABEL_M], at the entrance that is card-only now. Drawn in world space so the same node serves the
 * outdoor leg (terrain anchor) and the indoor leg (building transform).
 *
 * Filament rule (see the destination label in ArGuidanceView): a TextNode's texture must never be destroyed while the
 * scene runs, so once a sign has appeared in this view it stays composed for the life of the view. Distance, floor,
 * leg and tracking changes only move it; with nothing to place it, it is parked far below the floor.
 */
@Composable
fun ARSceneScope.CardDoorSignNodes(
    sign: ArDoorSign?, t: BuildingToWorld?, outdoorDoor: Vec3?, cameraWorld: () -> Float3,
) {
    val latch = remember { SignLatch() }
    if (sign != null) latch.sign = sign
    val s = latch.sign ?: return
    val red = s.tone == DoorSignTone.BLOCKED
    val post = remember(materialLoader, red) { materialLoader.createUnlitColorInstance(if (red) RED_POST else AMBER_POST) }
    // A sign the route no longer uses (sign == null now) is parked too.
    val foot = (if (sign != null) CardDoorPlacement.foot(s, t, outdoorDoor) else null) ?: CardDoorPlacement.PARKED
    val x = foot.x.toFloat(); val z = foot.z.toFloat()
    CubeNode(size = Float3(0.05f, CardDoorPlacement.POST_M.toFloat(), 0.05f), materialInstance = post,
        position = Float3(x, (foot.y + CardDoorPlacement.POST_M / 2).toFloat(), z))
    TextNode(
        text = s.label,
        // The label bitmap is 512 x 128 px; 40 px bold fits "Tap your PantherCard" on one line.
        fontSize = 40f,
        textColor = Color.White.toArgb(),
        backgroundColor = (if (red) RED_BG else AMBER_BG).toArgb(),
        widthMeters = 1.2f, heightMeters = 0.3f,
        position = Float3(x, (foot.y + CardDoorPlacement.LABEL_M).toFloat(), z),
        cameraPositionProvider = cameraWorld,
    )
}
