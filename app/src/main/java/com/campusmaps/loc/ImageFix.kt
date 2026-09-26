package com.campusmaps.loc

import com.campusmaps.data.Anchor
import com.campusmaps.data.Geo
import kotlin.math.hypot

/** Sign snap (docs/03 section 1): one tracked sign image gives the whole building-to-world transform. JVM-tested. */
object ImageFix {
    /** Centre height of a sign above its floor when the building file does not say. */
    const val DEFAULT_SIGN_HEIGHT_M = 1.5

    /**
     * [cx], [cy], [cz]: the image centre in ARCore world. [nx], [nz]: horizontal part of the image normal (ARCore's
     * AugmentedImage centre pose +Y axis, which points out of the image face). The anchor's `facing` is the same direction
     * in the building frame, its (x, y, floor) the same point, `heightM` the centre height above that floor.
     * Returns null when the image is not on a wall (normal mostly vertical) or the anchor has no usable facing.
     */
    fun transform(anchor: Anchor, cx: Double, cy: Double, cz: Double, nx: Double, nz: Double, floorHeightM: Double): BuildingToWorld? {
        if (hypot(nx, nz) < 0.5) return null
        val (fx, fy) = Geo.facingVector(anchor.facing) ?: return null
        val h = anchor.heightM ?: DEFAULT_SIGN_HEIGHT_M
        return BuildingToWorld.fromCorrespondence(Vec3(cx, cy - h, cz), nx, nz, anchor.x, anchor.y, fx, fy, anchor.floor, floorHeightM)
    }
}
