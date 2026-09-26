package com.campusmaps.ui.ar

import com.campusmaps.guidance.GuidanceState
import com.campusmaps.route.StepKind

/**
 * The only file in ui/ar that knows the teammate's guidance types (replaces Raphael's ArInputs.kt, docs/05 "Porting"):
 * turns a GuidanceState into the neutral [ArRouteInput] that RouteArrows / ArGuidanceView draw.
 * His plan is y-down (heading PI/2 = south); the AR layer is y-up like the core building files, so y is negated.
 */
fun GuidanceState.toArRouteInput(): ArRouteInput? {
    val pts = route.points.map { RoutePoint(it.position.x, -it.position.y, it.floor) }
    if (pts.size < 2) return null
    val turn = if (step.kind == StepKind.TURN_LEFT || step.kind == StepKind.TURN_RIGHT || step.kind == StepKind.TURN_AROUND) {
        RouteArrows.arrowAt(pts, step.startIndex)
    } else null
    val dest = ArDestination(destination.name, destination.position.x, -destination.position.y, destination.floor)
    val here = route.points.getOrNull(progress.segmentIndex)?.node?.id ?: "here"
    val i = progress.segmentIndex.coerceIn(0, pts.lastIndex)
    val placement = RouteArrows.placementAt(pts, i, here)?.copy(walkInDeg = route.points.getOrNull(i)?.node?.headingDeg)
    return ArRouteInput(pts, floor, turn, dest, placement, building.core.floorHeightM)
}
