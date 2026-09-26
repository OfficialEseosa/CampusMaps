package com.campusmaps.route

// Sentences that are not route instructions. The instructions themselves ("Turn left at H3",
// "Take the elevator to floor 6", "Room 608 is on your right") come from core's Instructions,
// through CoreRouter; screens never write their own.
object RouteText {
    // Error line on S1 and the S1b empty state (docs/20 QA #12).
    // [closed]: every door is shut for everyone (a card does not help either).
    fun noRouteLocked(room: String, dayTime: String, closed: Boolean = false) =
        "No route to $room: every entrance is ${if (closed) "closed" else "card-only"} at $dayTime."

    fun noRouteStepFree(room: String) = "No route to $room without stairs."

    fun noRouteUnknown(room: String) = "No route to $room from here."
}

// Which side of the student the destination door is on at arrival (from core's arrival sentence).
enum class Side { LEFT, RIGHT, AHEAD, BEHIND }
