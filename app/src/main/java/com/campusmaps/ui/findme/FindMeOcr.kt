package com.campusmaps.ui.findme

import android.graphics.Bitmap
import com.campusmaps.glasses.SignReader
import kotlin.math.max
import kotlin.math.min

// OCR of one phone frame. SignReader keeps only lines at least 20 px tall (tuned for glasses stills of big signs);
// a door plaque's room number is often smaller than that in a phone frame, so the frame is scaled up 2x first
// (long side capped at 3200 px). SignReader itself is unchanged: the glasses depend on it.
object FindMeOcr {
    const val SCALE = 2f
    const val MAX_SIDE = 3200

    suspend fun read(reader: SignReader, frame: Bitmap): String = reader.read(scaled(frame))

    fun scaled(frame: Bitmap): Bitmap {
        val long = max(frame.width, frame.height)
        val k = min(SCALE, MAX_SIDE.toFloat() / long)
        if (k <= 1f) return frame
        return Bitmap.createScaledBitmap(frame, (frame.width * k).toInt(), (frame.height * k).toInt(), true)
    }
}
