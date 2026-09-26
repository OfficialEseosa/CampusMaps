package com.campusmaps.ui.ar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusmaps.ui.theme.Sora
import com.google.android.filament.MaterialInstance
import dev.romainguy.kotlin.math.Float2
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARSceneScope
import kotlin.math.atan2
import kotlin.math.hypot

/** One outdoor chevron in ARCore world space (y is set by the caller: the ground under the camera). */
data class WorldArrow(val x: Double, val z: Double, val yawDeg: Double)

/**
 * The outdoor chevron chain (LEG 2): a straight line on the ground from just ahead of the phone toward the entrance's
 * Terrain anchor. Outdoors there is no route graph, only "walk to that door", so a straight chain is the honest drawing.
 * Pure, JVM-tested in OutdoorArrowGateTest.
 */
object OutdoorChain {
    const val FIRST_M = 2.0
    const val SPACING_M = 1.5
    const val MAX_M = 15.0

    /** Chevrons from the camera (x, z) toward the anchor (x, z), every [SPACING_M] from [FIRST_M] to min(distance, [MAX_M]). */
    fun chain(camX: Double, camZ: Double, anchorX: Double, anchorZ: Double): List<WorldArrow> {
        val dx = anchorX - camX; val dz = anchorZ - camZ
        val d = hypot(dx, dz)
        if (d < FIRST_M) return emptyList()
        val ux = dx / d; val uz = dz / d
        // Node yaw about +Y that turns the chevron's local -Z onto (ux, uz); same convention as RouteArrows.
        val yaw = Math.toDegrees(atan2(-ux, -uz))
        val out = mutableListOf<WorldArrow>()
        var s = FIRST_M
        val end = minOf(d, MAX_M)
        while (s <= end + 1e-9) { out += WorldArrow(camX + ux * s, camZ + uz * s, yaw); s += SPACING_M }
        return out
    }
}

/** Same chevron outline as the indoor chain (ArGuidanceView). */
private val OUTDOOR_CHEVRON = listOf(
    Float2(0f, 0.25f), Float2(-0.15f, -0.05f), Float2(-0.15f, -0.25f),
    Float2(0f, -0.05f), Float2(0.15f, -0.25f), Float2(0.15f, -0.05f),
)

/** Phone height above the ground used to put outdoor chevrons on the pavement (no plane finding outdoors). */
const val OUTDOOR_GROUND_BELOW_CAMERA_M = 1.4

/** Draws [arrows] inside an ARScene at height [groundY], scaled up 1.6x (outdoor distances are longer). */
@Composable
fun ARSceneScope.OutdoorArrowNodes(arrows: List<WorldArrow>, groundY: Float, material: MaterialInstance) {
    arrows.forEachIndexed { i, a ->
        key(i) {
            Node(position = Float3(a.x.toFloat(), groundY, a.z.toFloat()), rotation = Float3(0f, a.yawDeg.toFloat(), 0f), scale = Float3(1.6f)) {
                ShapeNode(polygonPath = OUTDOOR_CHEVRON, materialInstance = material, rotation = Float3(-90f, 0f, 0f))
            }
        }
    }
}

// Board 04 colours.
private val Clay = Color(0xFFC67C4E)
private val ChipScrim = Color(0xE61F1B18)

/**
 * The chip at the top of S2 (board 04): "AR tracking on" with a clay dot while the outdoor arrows are drawn,
 * "Finding your position" otherwise; indoors the host passes the current node name instead.
 */
@Composable
fun TrackingChip(text: String, live: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(ChipScrim)
            .padding(horizontal = 14.dp)
            .testTag("trackingChip"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(if (live) Clay else Color.White.copy(alpha = 0.45f)))
        Text(text, color = Color.White, fontFamily = Sora, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
