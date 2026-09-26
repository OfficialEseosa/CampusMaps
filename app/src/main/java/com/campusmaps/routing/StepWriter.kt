package com.campusmaps.routing

import com.campusmaps.data.model.EdgeKind
import com.campusmaps.data.model.GraphNode
import com.campusmaps.data.model.NodeKind
import com.campusmaps.data.model.Point
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// Turns a list of route points into the spoken / shown instructions.
// Rule of thumb: one instruction per place where the student has to do something
// (turn, ride, go through a door, arrive). Straight stretches are skipped.
object StepWriter {

    // Headings closer than this count as "straight on".
    private const val STRAIGHT_DEG = 35.0
    // Headings further than this count as "turn around".
    private const val AROUND_DEG = 150.0
    // HEAD and EXIT steps finish after this many metres, so the next turn can show its distance.
    private const val SHORT_STEP_M = 4.0

    fun write(points: List<RoutePoint>, destination: GraphNode, startHeadingRad: Double? = null): List<RouteStep> {
        require(points.isNotEmpty()) { "A route needs at least one point" }
        val last = points.last()

        // Start is the destination: one step, nothing to walk.
        if (points.size == 1) {
            return listOf(
                RouteStep(
                    kind = StepKind.ALREADY_THERE,
                    text = Instructions.alreadyThere(destination.name),
                    startIndex = 0,
                    completeAtM = 0.0,
                    completeFloor = last.floor,
                    place = destination.name,
                ),
            )
        }

        val steps = mutableListOf<RouteStep>()
        var scanFrom: Int

        if (points.first().node.isOutdoor) {
            // Outside: the first instruction is simply "Walk to <entrance>".
            val entranceIndex = points.indexOfFirst { it.node.kind == NodeKind.ENTRANCE }
            if (entranceIndex > 0) {
                val entrance = points[entranceIndex].node
                steps += RouteStep(
                    kind = StepKind.WALK_TO_ENTRANCE,
                    text = Instructions.walkTo(entrance.name),
                    startIndex = entranceIndex,
                    completeAtM = points[entranceIndex].cumulativeM,
                    completeFloor = entrance.floor,
                    place = entrance.name,
                    approachText = Instructions.goThrough(entrance.name, entrance.hint),
                )
                scanFrom = entranceIndex + 1
            } else {
                scanFrom = 1
            }
        } else if (points[1].arrivedBy == EdgeKind.WALK) {
            // Inside: tell the student which way to set off.
            val target = points[1].node
            val direction = startHeadingRad?.let { heading ->
                val facing = Point(cos(heading), sin(heading))
                turnDirection(facing, points[1].position - points[0].position)
            }
            val (kind, text) = when (direction) {
                null -> StepKind.HEAD to Instructions.headToward(target.name)
                TurnDirection.STRAIGHT -> StepKind.CONTINUE to Instructions.continueToward(target.name)
                TurnDirection.LEFT -> StepKind.TURN_LEFT to Instructions.turnToward(direction, target.name)
                TurnDirection.RIGHT -> StepKind.TURN_RIGHT to Instructions.turnToward(direction, target.name)
                TurnDirection.AROUND -> StepKind.TURN_AROUND to Instructions.turnToward(direction, target.name)
            }
            steps += RouteStep(
                kind = kind,
                text = text,
                startIndex = 0,
                completeAtM = min(points[1].cumulativeM, SHORT_STEP_M),
                completeFloor = points[0].floor,
                place = target.name,
            )
            scanFrom = 1
        } else {
            // Inside and the very first move is a ride (starting at an elevator lobby).
            scanFrom = 0
        }

        var m = scanFrom
        while (m < points.size - 1) {
            val here = points[m]
            val outgoing = points[m + 1].arrivedBy

            if (outgoing != EdgeKind.WALK) {
                // A ride starts here. Find where it ends (several floors in a row).
                var end = m
                while (end + 1 < points.size && points[end + 1].arrivedBy != EdgeKind.WALK) end++
                val change = points[end].floor - here.floor
                val isElevator = outgoing == EdgeKind.ELEVATOR
                steps += RouteStep(
                    kind = if (isElevator) StepKind.ELEVATOR else StepKind.STAIRS,
                    text = if (isElevator) Instructions.elevator(points[end].floor) else Instructions.stairs(change),
                    startIndex = m,
                    completeAtM = points[end].cumulativeM,
                    completeFloor = points[end].floor,
                    place = here.node.name,
                    targetFloor = points[end].floor,
                    fromFloor = here.floor,
                )
                // Straight after the ride, point the student the right way (unless we are already there).
                if (end + 1 < points.size - 1) {
                    val next = points[end + 1]
                    steps += RouteStep(
                        kind = StepKind.EXIT_TOWARD,
                        text = Instructions.exitToward(next.node.name),
                        startIndex = end,
                        completeAtM = min(points[end].cumulativeM + SHORT_STEP_M, next.cumulativeM),
                        completeFloor = points[end].floor,
                        place = next.node.name,
                    )
                }
                m = end + 1
                continue
            }

            if (m >= 1 && here.arrivedBy == EdgeKind.WALK && points[m - 1].floor == here.floor) {
                val direction = turnDirection(here.position - points[m - 1].position, points[m + 1].position - here.position)
                if (direction != TurnDirection.STRAIGHT) {
                    steps += RouteStep(
                        kind = when (direction) {
                            TurnDirection.LEFT -> StepKind.TURN_LEFT
                            TurnDirection.RIGHT -> StepKind.TURN_RIGHT
                            else -> StepKind.TURN_AROUND
                        },
                        text = Instructions.turnAt(direction, here.node.name, here.node.hint),
                        startIndex = m,
                        completeAtM = here.cumulativeM,
                        completeFloor = here.floor,
                        place = here.node.name,
                    )
                } else if (here.node.kind == NodeKind.DOOR) {
                    steps += RouteStep(
                        kind = StepKind.DOOR,
                        text = Instructions.door(),
                        startIndex = m,
                        completeAtM = here.cumulativeM,
                        completeFloor = here.floor,
                        place = here.node.name,
                    )
                }
            }
            m++
        }

        // Arrival: say which side the door is on when we know it.
        val before = points[points.size - 2]
        val side = if (before.floor == last.floor) {
            destination.roomInside?.let { inside -> sideOf(last.position - before.position, inside - last.position) }
        } else {
            null
        }
        steps += RouteStep(
            kind = StepKind.ARRIVE,
            text = side?.let { Instructions.arriveSide(destination.name, it) } ?: Instructions.arrivedAt(destination.name),
            startIndex = points.size - 1,
            completeAtM = last.cumulativeM,
            completeFloor = last.floor,
            place = destination.name,
            side = side,
        )
        return steps
    }

    // Signed angle from heading a to heading b, in degrees. Positive = clockwise = to the right,
    // because y grows downward on our floor plans.
    fun signedAngleDeg(a: Point, b: Point): Double {
        val cross = a.x * b.y - a.y * b.x
        val dot = a.x * b.x + a.y * b.y
        return Math.toDegrees(atan2(cross, dot))
    }

    fun turnDirection(incoming: Point, outgoing: Point): TurnDirection {
        val angle = signedAngleDeg(incoming, outgoing)
        return when {
            abs(angle) < STRAIGHT_DEG -> TurnDirection.STRAIGHT
            abs(angle) > AROUND_DEG -> TurnDirection.AROUND
            angle > 0 -> TurnDirection.RIGHT
            else -> TurnDirection.LEFT
        }
    }

    // Where the room is compared to the way the student is walking.
    fun sideOf(walking: Point, toRoom: Point): Side {
        val angle = signedAngleDeg(walking, toRoom)
        return when {
            abs(angle) < 45 -> Side.AHEAD
            abs(angle) > 135 -> Side.BEHIND
            angle > 0 -> Side.RIGHT
            else -> Side.LEFT
        }
    }
}
