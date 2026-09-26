package com.campusmaps.routing

// Instruction templates from section 13 of the design handoff.
// The router fills these in. Screens never write their own sentences.
object Instructions {

    fun walkTo(entrance: String) = "Walk to $entrance"

    fun goThrough(entrance: String, hint: String? = null) = "Go through $entrance" + hintSuffix(hint)

    fun headToward(place: String) = "Head toward $place"

    fun continueToward(place: String) = "Continue toward $place"

    fun turnToward(direction: TurnDirection, place: String) = when (direction) {
        TurnDirection.LEFT -> "Turn left toward $place"
        TurnDirection.RIGHT -> "Turn right toward $place"
        TurnDirection.AROUND -> "Turn around toward $place"
        TurnDirection.STRAIGHT -> continueToward(place)
    }

    fun turnAt(direction: TurnDirection, place: String, hint: String? = null): String {
        val verb = when (direction) {
            TurnDirection.LEFT -> "Turn left"
            TurnDirection.RIGHT -> "Turn right"
            TurnDirection.AROUND -> "Turn around"
            TurnDirection.STRAIGHT -> "Continue"
        }
        return "$verb at $place" + hintSuffix(hint)
    }

    fun elevator(toFloor: Int) = "Take the elevator to floor $toFloor"

    fun stairs(floorChange: Int): String {
        val direction = if (floorChange > 0) "up" else "down"
        val count = kotlin.math.abs(floorChange)
        val unit = if (count == 1) "floor" else "floors"
        return "Take the stairs $direction ${Formats.numberWord(count)} $unit"
    }

    fun exitToward(place: String) = "Exit toward $place"

    fun door() = "Go through the door"

    // The "Heads up" banner on S1b.
    fun lockedNotice(locked: String, other: String?) =
        if (other != null) "$locked is card-only now. Using $other instead."
        else "$locked is card-only now. Taking another way."

    fun arriveSide(room: String, side: Side) = when (side) {
        Side.LEFT -> "$room is on your left"
        Side.RIGHT -> "$room is on your right"
        Side.AHEAD -> "$room is ahead"
        Side.BEHIND -> "$room is behind you"
    }

    fun arrivedAt(room: String) = "You have arrived at $room"

    fun alreadyThere(room: String) = "You are at $room"

    // Error line on S1 and the S1b empty state.
    fun noRouteLocked(room: String, dayTime: String) =
        "No route to $room: every entrance is card-only at $dayTime."

    fun noRouteStepFree(room: String) = "No route to $room without stairs."

    fun noRouteUnknown(room: String) = "No route to $room from here."

    private fun hintSuffix(hint: String?) = if (hint.isNullOrBlank()) "" else ", $hint"
}

enum class TurnDirection { LEFT, RIGHT, AROUND, STRAIGHT }

enum class Side { LEFT, RIGHT, AHEAD, BEHIND }
