package com.campusmaps.loc

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A point in ARCore world space (metres, +Y up). */
data class Vec3(val x: Double, val y: Double, val z: Double)

/**
 * Building frame -> ARCore world, yaw-only (docs/03 section 1). Both frames are gravity-aligned, so the
 * transform is one rotation about world +Y plus a translation.
 *
 * Conventions (do not change without changing the AR layer):
 * - Building: x east-ish, y north-ish, integer floors, metres.
 * - Building-local 3D ("local"): `(x, h, -y)`, so north is -Z and up is +Y (right-handed, like ARCore).
 * - World = Ry([yawRad]) * local + ([tx], [ty], [tz]). Ry is the standard right-handed rotation about +Y
 *   (it turns +Z toward +X), which is also what SceneView's `rotation = Float3(0, deg, 0)` does.
 * - [ty] is the world height of the floor of [refFloor]; other floors are offset by [floorHeightM].
 *
 * Pure Kotlin on purpose: JVM-tested in app/src/test (BuildingToWorldTest).
 */
data class BuildingToWorld(
    val yawRad: Double,
    val tx: Double,
    val ty: Double,
    val tz: Double,
    val refFloor: Int,
    val floorHeightM: Double = 3.8,
) {
    val yawDeg: Double get() = Math.toDegrees(yawRad)

    /** World position of building point ([x], [y]) on [floor], [heightM] above that floor. */
    fun toWorld(x: Double, y: Double, floor: Int = refFloor, heightM: Double = 0.0): Vec3 {
        val lx = x; val lz = -y
        val c = cos(yawRad); val s = sin(yawRad)
        return Vec3(lx * c + lz * s + tx, ty + (floor - refFloor) * floorHeightM + heightM, -lx * s + lz * c + tz)
    }

    /** Building (x, y) of a world point, ignoring height. Used for the phone's position on the minimap and for distance culling. */
    fun toBuilding(w: Vec3): Pair<Double, Double> {
        val qx = w.x - tx; val qz = w.z - tz
        val c = cos(yawRad); val s = sin(yawRad)
        val lx = qx * c - qz * s
        val lz = qx * s + qz * c
        return lx to -lz
    }

    /** Building-frame heading (radians, atan2(dy, dx), 0 = east, CCW) of a horizontal world direction. */
    fun buildingHeadingOf(worldDirX: Double, worldDirZ: Double): Double {
        val c = cos(yawRad); val s = sin(yawRad)
        val lx = worldDirX * c - worldDirZ * s
        val lz = worldDirX * s + worldDirZ * c
        return atan2(-lz, lx)
    }

    /** Linear blend toward [to] (yaw along the short way round), for the 300 ms re-snap slide. */
    fun lerp(to: BuildingToWorld, t: Double): BuildingToWorld {
        val dYaw = wrapPi(to.yawRad - yawRad)
        return to.copy(
            yawRad = wrapPi(yawRad + dYaw * t),
            tx = tx + (to.tx - tx) * t, ty = ty + (to.ty - ty) * t, tz = tz + (to.tz - tz) * t,
        )
    }

    companion object {
        /** Angle of a horizontal vector in the XZ plane, measured so that Ry(theta) adds theta: atan2(x, z). */
        fun yawOf(x: Double, z: Double): Double = atan2(x, z)

        fun wrapPi(a: Double): Double {
            var r = a % (2 * PI)
            if (r > PI) r -= 2 * PI
            if (r < -PI) r += 2 * PI
            return r
        }

        /**
         * The yaw-only fix: building point ([bx], [by]) on [floor] is at world point [world], and the building direction
         * ([bdx], [bdy]) points along world direction ([wdx], [wdz]) (horizontal components only; world y is ignored).
         *
         * Debug "Place route here": world = floor hit, building point = the current node, building direction = the route's
         * next edge, world direction = the phone's horizontal heading. Sign snap later: world = image centre, direction =
         * the image's wall normal vs the anchor's `facing`.
         */
        fun fromCorrespondence(
            world: Vec3, wdx: Double, wdz: Double,
            bx: Double, by: Double, bdx: Double, bdy: Double,
            floor: Int, floorHeightM: Double = 3.8,
        ): BuildingToWorld? {
            if (hypot(wdx, wdz) < 1e-6 || hypot(bdx, bdy) < 1e-6) return null
            // Building direction in local coordinates is (bdx, 0, -bdy).
            val theta = wrapPi(yawOf(wdx, wdz) - yawOf(bdx, -bdy))
            val c = cos(theta); val s = sin(theta)
            val lx = bx; val lz = -by
            val rx = lx * c + lz * s
            val rz = -lx * s + lz * c
            return BuildingToWorld(theta, world.x - rx, world.y, world.z - rz, floor, floorHeightM)
        }
    }
}
