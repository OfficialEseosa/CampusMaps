package com.campusmaps.ui.ar

import com.campusmaps.data.campus.Campuses
import com.campusmaps.guidance.GuidanceState
import com.campusmaps.route.LockedNotice
import com.campusmaps.route.Route
import com.campusmaps.route.StepKind

/**
 * The only file in ui/ar that knows the teammate's guidance types (replaces Raphael's ArInputs.kt, docs/05 "Porting"):
 * turns a GuidanceState into the neutral [ArRouteInput] that RouteArrows / ArGuidanceView draw.
 * His plan is y-down (heading PI/2 = south); the AR layer is y-up like the core building files, so y is negated.
 *
 * [lockedNotice] is optional: pass the plan's notice to get the red "PantherCard required" sign when the route still
 * ends at the door it names (without the card the router normally goes around it, so this is rare).
 */
fun GuidanceState.toArRouteInput(lockedNotice: LockedNotice? = null): ArRouteInput? {
    val pts = route.points.map { RoutePoint(it.position.x, -it.position.y, it.floor) }
    if (pts.size < 2) return null
    val turn = if (step.kind == StepKind.TURN_LEFT || step.kind == StepKind.TURN_RIGHT || step.kind == StepKind.TURN_AROUND) {
        RouteArrows.arrowAt(pts, step.startIndex)
    } else null
    val dest = ArDestination(destination.name, destination.position.x, -destination.position.y, destination.floor)
    val here = route.points.getOrNull(progress.segmentIndex)?.node?.id ?: "here"
    val i = progress.segmentIndex.coerceIn(0, pts.lastIndex)
    val placement = RouteArrows.placementAt(pts, i, here)?.copy(walkInDeg = route.points.getOrNull(i)?.node?.headingDeg)
    return ArRouteInput(pts, floor, turn, dest, placement, building.core.floorHeightM, cardDoorSign(route, lockedNotice))
}

/**
 * The sign at the route's entrance when that door is card-only now. The card holder's route marks its
 * "Walk to" step with the card name (CoreRouter): amber "Tap your PantherCard". A route that still ends at the door
 * named by [lockedNotice]: red "PantherCard required". Otherwise null. The sign stands on the entrance node.
 */
fun cardDoorSign(route: Route, lockedNotice: LockedNotice? = null): ArDoorSign? {
    val walk = route.steps.firstOrNull { it.kind == StepKind.WALK_TO_ENTRANCE } ?: return null
    val door = route.points.getOrNull(walk.startIndex) ?: return null
    val x = door.position.x; val y = -door.position.y
    walk.cardName?.let { return ArDoorSign("Tap your $it", x, y, door.floor, DoorSignTone.ATTENTION) }
    if (lockedNotice != null && lockedNotice.lockedEntrance == door.node.name) {
        return ArDoorSign("${Campuses.of(route.buildingId).cardName} required", x, y, door.floor, DoorSignTone.BLOCKED)
    }
    return null
}
