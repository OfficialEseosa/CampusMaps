package com.campusmaps.routing

import kotlin.math.abs
import kotlin.math.roundToInt

// Builds the words on the S1b route cards from a RouteOption.
// Kept separate from the UI so the copy can be unit tested.
object RouteCardText {

    private const val SAME_WALK_M = 10

    // Card title: the entrance name, or "From here" when starting inside.
    fun title(option: RouteOption, startsInside: Boolean): String = when {
        !startsInside && option.entrance != null -> option.entrance.name
        option.method == FloorChange.LEVEL -> "From here"
        else -> "From here, ${methodWords(option.method)}"
    }

    // "Enter on floor 1 · by elevator"
    fun subtitle(option: RouteOption, startsInside: Boolean, startFloor: Int): String {
        val how = methodWords(option.method)
        return if (!startsInside && option.entrance != null) {
            "Enter on floor ${option.entrance.floor} · $how"
        } else {
            "Start on floor $startFloor · $how"
        }
    }

    fun methodWords(method: FloorChange): String = when (method) {
        FloorChange.ELEVATOR -> "by elevator"
        FloorChange.STAIRS -> "by stairs"
        FloorChange.LEVEL -> "same floor"
    }

    // Best card bottom line: "90 m walk, elevator, avg wait 35 s"
    fun bestSummary(option: RouteOption): String {
        val parts = mutableListOf("${option.walkM.roundToInt()} m walk")
        methodDetail(option)?.let { parts += it }
        option.shortcutName?.let { parts += "via $it" }
        return parts.joinToString(", ")
    }

    // "also via: 95 Decatur Street entrance"
    fun alsoVia(option: RouteOption): String? =
        if (option.alsoVia.isEmpty()) null else "also via: ${option.alsoVia.joinToString(", ")}"

    // Other cards: "+47 s: same walk, 5 floors of stairs"
    fun difference(option: RouteOption, best: RouteOption): String {
        val parts = mutableListOf<String>()
        val walkDelta = (option.walkM - best.walkM).roundToInt()
        parts += when {
            abs(walkDelta) < SAME_WALK_M -> "same walk"
            walkDelta > 0 -> "$walkDelta m more walking"
            else -> "${-walkDelta} m less walking"
        }
        if (option.method != best.method || option.floorsChanged != best.floorsChanged) {
            methodDetail(option)?.let { parts += it }
        }
        option.shortcutName?.let { parts += "via $it" }
        val delta = (option.etaSeconds - best.etaSeconds).coerceAtLeast(0.0)
        return "${Formats.etaDifference(delta)}: ${parts.joinToString(", ")}"
    }

    private fun methodDetail(option: RouteOption): String? = when (option.method) {
        FloorChange.ELEVATOR -> "elevator, avg wait ${option.elevatorWaitS ?: 0} s"
        FloorChange.STAIRS -> {
            val n = option.floorsChanged
            if (n == 1) "1 floor of stairs" else "$n floors of stairs"
        }
        FloorChange.LEVEL -> null
    }
}
