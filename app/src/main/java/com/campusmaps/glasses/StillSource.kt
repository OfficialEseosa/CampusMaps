package com.campusmaps.glasses

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

// Where glasses stills come from: the real Ray-Bans, or the toolkit's mock device replaying survey photos.
interface StillSource {
    val name: String
    val connected: StateFlow<Boolean>

    // Gets ready to burst (initialise, register, find the glasses). False = not now; the caller backs off and retries.
    suspend fun connect(): Boolean

    // Camera on, [count] stills, camera fully off, in that order. Returns only after the camera is stopped,
    // so the caller may speak as soon as this returns. Throws on a session or stream failure.
    // [hintAnchorId] is only used by the replay source to pick which survey photos the mock camera "sees".
    suspend fun burst(count: Int, hintAnchorId: String?): List<Bitmap>

    // Tear everything down (Stop on S3, or a failure before a reconnect).
    fun disconnect()
}

class GlassesUnavailable(message: String) : Exception(message)
