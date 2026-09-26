package com.campusmaps.shared

// Haptic patterns for the watch. Each array alternates ON, OFF, ON... in milliseconds.
// Pass to VibrationEffect.createWaveform(timings, -1) after prepending a 0 ms start delay.
object WatchHaptics {
    val straight = longArrayOf(80)
    val left = longArrayOf(80, 80, 80)
    val right = longArrayOf(80, 80, 80, 80, 80)
    val stairs = longArrayOf(300, 100, 80)
    val elevator = longArrayOf(300, 100, 300)
    val door = longArrayOf(80, 100, 300)
    val locked = longArrayOf(80, 60, 80, 60, 80, 60, 80)
    val arrive = longArrayOf(600)

    // Picks the pattern that belongs to a step type.
    fun patternFor(type: WatchStepType): LongArray = when (type) {
        WatchStepType.STRAIGHT -> straight
        WatchStepType.LEFT -> left
        WatchStepType.RIGHT -> right
        WatchStepType.STAIRS, WatchStepType.STAIRS_DOWN -> stairs
        WatchStepType.ELEVATOR -> elevator
        WatchStepType.DOOR -> door
        WatchStepType.LOCKED -> locked
        WatchStepType.ARRIVED -> arrive
    }

    // Android's createWaveform wants OFF first, so we add a 0 ms delay at the start.
    fun waveformTimings(type: WatchStepType): LongArray = longArrayOf(0L) + patternFor(type)
}
