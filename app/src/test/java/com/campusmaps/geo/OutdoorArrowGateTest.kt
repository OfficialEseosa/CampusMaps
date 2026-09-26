package com.campusmaps.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutdoorArrowGateTest {
    private val good = GeoState(tracking = EarthTracking.TRACKING, lat = 33.75, lng = -84.38, horizontalAccuracyM = 4.0, yawAccuracyDeg = 5.0)

    @Test fun drawsWhenTrackingAndAccurate() {
        assertTrue(OutdoorArrowGate.shouldDrawArrows(good))
        assertEquals("AR tracking on", OutdoorArrowGate.chipText(good))
    }

    @Test fun noArrowsUnlessTracking() {
        for (t in listOf(EarthTracking.OFF, EarthTracking.PAUSED, EarthTracking.UNAVAILABLE)) {
            assertFalse(t.name, OutdoorArrowGate.shouldDrawArrows(good.copy(tracking = t)))
            assertEquals("Finding your position", OutdoorArrowGate.chipText(good.copy(tracking = t)))
        }
    }

    @Test fun noArrowsAt10mOrWorse() {
        assertTrue(OutdoorArrowGate.shouldDrawArrows(good.copy(horizontalAccuracyM = 9.99)))
        assertFalse(OutdoorArrowGate.shouldDrawArrows(good.copy(horizontalAccuracyM = 10.0)))
        assertFalse(OutdoorArrowGate.shouldDrawArrows(good.copy(horizontalAccuracyM = 25.0)))
        assertFalse(OutdoorArrowGate.shouldDrawArrows(good.copy(horizontalAccuracyM = null)))
    }

    @Test fun badYawBlocksWhenKnown() {
        assertFalse(OutdoorArrowGate.shouldDrawArrows(good.copy(yawAccuracyDeg = 20.0)))
        assertTrue(OutdoorArrowGate.shouldDrawArrows(good.copy(yawAccuracyDeg = null)))
    }

    @Test fun fakeApproachOpensTheGateAfterLocalizing() {
        val fake = FakeGeospatialProvider(from = LatLng(33.7520, -84.3877), to = LatLng(33.75288, -84.38769), localizeSteps = 3)
        fake.start()
        repeat(3) { fake.step(); assertFalse(OutdoorArrowGate.shouldDrawArrows(fake.state.value)) }
        // Accuracy tightens from 18 m to 3 m over the first third of the walk.
        var opened = -1
        for (i in 1..80) { fake.step(); if (opened < 0 && OutdoorArrowGate.shouldDrawArrows(fake.state.value)) opened = i }
        assertTrue("gate never opened", opened > 0)
        assertTrue(fake.placeTerrainAnchor(33.75288, -84.38769))
        assertEquals(AnchorStatus.READY, fake.state.value.anchor)
        fake.stop()
        assertEquals(EarthTracking.OFF, fake.state.value.tracking)
    }

    @Test fun outdoorChainPointsAtTheAnchor() {
        // Camera at the origin, anchor 20 m straight ahead (-Z): first chevron 2 m out, every 1.5 m, capped at 15 m.
        val c = com.campusmaps.ui.ar.OutdoorChain.chain(camX = 0.0, camZ = 0.0, anchorX = 0.0, anchorZ = -20.0)
        assertEquals(2.0, -c.first().z, 1e-9)
        assertTrue(c.all { -it.z <= 15.0 + 1e-9 })
        assertEquals(0.0, c.first().yawDeg, 1e-9)
        assertEquals(9, c.size) // 2.0, 3.5, ... 14.0
        // Anchor 5 m to the east (+X): yaw -90 (local -Z turned to +X).
        val e = com.campusmaps.ui.ar.OutdoorChain.chain(0.0, 0.0, 5.0, 0.0)
        assertEquals(-90.0, e.first().yawDeg, 1e-9)
        assertEquals(3, e.size) // 2.0, 3.5, 5.0
        assertTrue(com.campusmaps.ui.ar.OutdoorChain.chain(0.0, 0.0, 0.5, 0.0).isEmpty())
    }
}
