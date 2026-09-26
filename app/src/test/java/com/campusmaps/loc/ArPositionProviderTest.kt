package com.campusmaps.loc

import com.campusmaps.data.Anchor
import com.campusmaps.data.AnchorKind
import com.campusmaps.data.model.Point
import com.campusmaps.guidance.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ArPositionProviderTest {
    // Building (0, 0) floor 1 is at the world origin, building north (+y) is world -Z: world = (x, h, -y).
    private val north = BuildingToWorld.fromCorrespondence(Vec3(0.0, 0.0, 0.0), 0.0, -1.0, 0.0, 0.0, 0.0, 1.0, 1, 3.9)!!

    private fun sample(x: Double, y: Double, z: Double, fx: Double = 0.0, fz: Double = -1.0, t: Long = 0, tr: ArTracking = ArTracking.TRACKING) =
        CameraSample(tr, x, y, z, fx, fz, t)

    private val start = Pose(Point(0.0, 0.0), 1, 0.0, 0.92f)

    @Test fun cameraMapsToAppFrameWithYFlipped() {
        val a = PoseMath.toAppPose(north, sample(2.0, 1.3, -3.0))
        assertEquals(2.0, a.position.x, 1e-9)
        assertEquals(-3.0, a.position.y, 1e-9) // building y = 3 (north), app y grows south
        assertEquals(1, a.floor)
        assertEquals(-PI / 2, a.headingRad!!, 1e-9) // facing north = app heading -PI/2
    }

    @Test fun headingEastAndStraightDown() {
        assertEquals(0.0, PoseMath.toAppPose(north, sample(0.0, 1.3, 0.0, fx = 1.0, fz = 0.0)).headingRad!!, 1e-9)
        assertNull(PoseMath.toAppPose(north, sample(0.0, 1.3, 0.0, fx = 0.0, fz = 0.0)).headingRad)
    }

    @Test fun rotatedTransformRoundTrips() {
        val t = BuildingToWorld(yawRad = 1.1, tx = 4.0, ty = -0.7, tz = 9.0, refFloor = 2, floorHeightM = 3.9)
        for ((bx, by) in listOf(0.0 to 0.0, 12.5 to -3.0, -7.0 to 20.0)) {
            val w = t.toWorld(bx, by, 2, PoseMath.PHONE_HEIGHT_M)
            // Phone faces building direction 30 degrees (north of east).
            val h = Math.toRadians(30.0)
            val w2 = t.toWorld(bx + cos(h), by + sin(h), 2, PoseMath.PHONE_HEIGHT_M)
            val a = PoseMath.toAppPose(t, sample(w.x, w.y, w.z, w2.x - w.x, w2.z - w.z))
            assertEquals(bx, a.position.x, 1e-9)
            assertEquals(-by, a.position.y, 1e-9)
            assertEquals(2, a.floor)
            assertEquals(-h, a.headingRad!!, 1e-9)
        }
    }

    @Test fun floorFollowsCameraHeight() {
        assertEquals(2, PoseMath.toAppPose(north, sample(0.0, 1.3 + 3.9, 0.0)).floor)
        assertEquals(0, PoseMath.toAppPose(north, sample(0.0, 1.3 - 3.9, 0.0)).floor)
        assertEquals(1, PoseMath.toAppPose(north, sample(0.0, 2.5, 0.0)).floor) // arm raised, same floor
    }

    @Test fun noTransformNoPosition() {
        val p = ArPositionProvider({ null }, start)
        p.onSample(sample(5.0, 1.3, 5.0))
        assertTrue(p.live.value)
        assertFalse(p.hasFix)
        assertEquals(start, p.pose.value)
    }

    @Test fun throttledToFiveHertz() {
        val p = ArPositionProvider({ north }, start)
        p.onSample(sample(1.0, 1.3, 0.0, t = 1000))
        assertEquals(1.0, p.pose.value.position.x, 1e-9)
        assertEquals(1f, p.pose.value.confidence)
        p.onSample(sample(2.0, 1.3, 0.0, t = 1100))
        assertEquals(1.0, p.pose.value.position.x, 1e-9)
        p.onSample(sample(3.0, 1.3, 0.0, t = 1210))
        assertEquals(3.0, p.pose.value.position.x, 1e-9)
    }

    @Test fun confidenceDecaysWhenPausedAndDropsWhenStopped() {
        val p = ArPositionProvider({ north }, start)
        p.onSample(sample(1.0, 1.3, 0.0, t = 0))
        p.onSample(sample(9.0, 1.3, 0.0, t = 1000, tr = ArTracking.PAUSED))
        assertEquals(0.75f, p.pose.value.confidence, 1e-6f)
        assertEquals(1.0, p.pose.value.position.x, 1e-9) // position held while lost
        p.onSample(sample(9.0, 1.3, 0.0, t = 3000, tr = ArTracking.PAUSED))
        assertEquals(0.25f, p.pose.value.confidence, 1e-6f)
        p.onSample(sample(9.0, 1.3, 0.0, t = 3100, tr = ArTracking.STOPPED))
        assertEquals(0f, p.pose.value.confidence)
        p.onSample(sample(2.0, 1.3, 0.0, t = 3200))
        assertEquals(1f, p.pose.value.confidence)
        p.markGone()
        assertFalse(p.live.value)
    }

    private fun anchor(facing: String?, heightM: Double? = 2.0) =
        Anchor(id = "T-A01", node = "H1", kind = AnchorKind.IMAGE, x = 10.0, y = 5.0, floor = 2, heightM = heightM, facing = facing)

    @Test fun signFixPutsTheSignWhereArCoreSeesIt() {
        // The sign faces building north; ARCore sees its face pointing world +X, centre at (1, 3, 1).
        val t = ImageFix.transform(anchor("north"), 1.0, 3.0, 1.0, 1.0, 0.0, 3.9)
        assertNotNull(t)
        t!!
        assertEquals(2, t.refFloor)
        assertEquals(1.0, t.ty, 1e-9) // floor is 2 m below the sign centre
        val w = t.toWorld(10.0, 5.0, 2, 2.0)
        assertEquals(1.0, w.x, 1e-9); assertEquals(3.0, w.y, 1e-9); assertEquals(1.0, w.z, 1e-9)
        assertEquals(PI / 2, t.buildingHeadingOf(1.0, 0.0), 1e-9) // world +X is building north
        // Standing 2 m in front of the sign (world +X), phone at 1.3 m: building (10, 7), app (10, -7).
        val a = PoseMath.toAppPose(t, sample(3.0, 1.0 + 1.3, 1.0, fx = -1.0, fz = 0.0))
        assertEquals(10.0, a.position.x, 1e-9)
        assertEquals(-7.0, a.position.y, 1e-9)
        assertEquals(2, a.floor)
        assertEquals(PI / 2, a.headingRad!!, 1e-9) // looking at the sign = building south = app +PI/2
    }

    @Test fun signFixRejectsFlatImagesAndMissingFacing() {
        assertNull(ImageFix.transform(anchor("north"), 0.0, 0.0, 0.0, 0.1, 0.1, 3.9))
        assertNull(ImageFix.transform(anchor(null), 0.0, 0.0, 0.0, 1.0, 0.0, 3.9))
        assertEquals(1.5, ImageFix.transform(anchor("east", heightM = null), 0.0, 1.5, 0.0, 1.0, 0.0, 3.9)!!.let { -it.ty + 1.5 }, 1e-9)
    }
}
