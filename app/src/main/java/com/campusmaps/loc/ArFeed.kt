package com.campusmaps.loc

import com.campusmaps.data.Anchor
import com.campusmaps.data.Building as CoreBuilding

/**
 * The link between the AR view (ui/ar/ArGuidanceView, composed by GuidanceScreen) and the guidance session's
 * [ArPositionProvider], without changing the screens: the controller attaches its provider and the building's anchors here,
 * the AR view sends camera samples and reads anchors.
 */
object ArFeed {
    /** Set at app start from ArCoreApk availability (AppContainer). When true, S2 starts with the simulated walker paused. */
    @Volatile var arExpected: Boolean = false

    /** Anchors of the building being guided in, by id (for example "CS-A01"). */
    @Volatile var anchors: Map<String, Anchor> = emptyMap()
        private set
    @Volatile var floorHeightM: Double = 3.8
        private set

    /** Building origin (core Origin): lat / lng for the magnetic declination, headingDeg = compass bearing of building +y. */
    @Volatile var originLat: Double = 0.0
        private set
    @Volatile var originLng: Double = 0.0
        private set
    @Volatile var originHeadingDeg: Double = 0.0
        private set

    @Volatile private var sink: ArPositionProvider? = null

    fun setBuilding(core: CoreBuilding) {
        anchors = core.anchors.associateBy { it.id }
        floorHeightM = core.floorHeightM
        originLat = core.origin.lat
        originLng = core.origin.lng
        originHeadingDeg = core.origin.headingDeg
    }

    fun attach(p: ArPositionProvider) { sink = p }

    fun detach(p: ArPositionProvider) { if (sink === p) sink = null }

    fun sample(s: CameraSample) { sink?.onSample(s) }

    /** The AR view left the screen. */
    fun viewGone() { sink?.markGone() }
}
