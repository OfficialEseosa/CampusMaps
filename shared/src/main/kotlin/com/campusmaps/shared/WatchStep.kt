package com.campusmaps.shared

// The kinds of step the watch knows how to draw (section 12 of the design handoff).
// Each kind has its own arrow shape, color and haptic pattern.
enum class WatchStepType {
    STRAIGHT,
    LEFT,
    RIGHT,
    STAIRS,      // Stairs up
    STAIRS_DOWN, // Same icon mirrored; same haptic as STAIRS
    ELEVATOR,
    DOOR,
    LOCKED,
    ARRIVED,
}

// One screen worth of information for the watch.
// bigText is the short fact ("10 m", "Floor 6", "Room 608").
// label is the small line under it ("Atrium north", "Elevator", "Arrived").
data class WatchStep(
    val type: WatchStepType,
    val bigText: String,
    val label: String,
)

// Turns a WatchStep into bytes for the Wear data layer and back again.
// The format is deliberately tiny and human readable: TYPE, big text and label
// separated by the ASCII "unit separator" character, which never appears in room names.
object WatchProtocol {
    // Message path used by both apps. The watch listens for this exact path.
    const val STEP_PATH = "/campusmaps/step"

    // Empty message sent when a route ends; the watch goes back to its idle face.
    const val CLEAR_PATH = "/campusmaps/clear"

    private const val SEPARATOR = '\u001F'

    fun encode(step: WatchStep): ByteArray {
        val text = listOf(step.type.name, clean(step.bigText), clean(step.label))
            .joinToString(SEPARATOR.toString())
        return text.toByteArray(Charsets.UTF_8)
    }

    // Returns null for anything we do not understand, so a bad message never crashes the watch.
    fun decode(bytes: ByteArray): WatchStep? {
        val parts = String(bytes, Charsets.UTF_8).split(SEPARATOR)
        if (parts.size != 3) return null
        val type = WatchStepType.entries.firstOrNull { it.name == parts[0] } ?: return null
        return WatchStep(type = type, bigText = parts[1], label = parts[2])
    }

    // Removes the separator character from user facing text, just in case.
    private fun clean(text: String): String = text.replace(SEPARATOR, ' ')
}
