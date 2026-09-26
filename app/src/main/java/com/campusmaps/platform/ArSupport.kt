package com.campusmaps.platform

import android.content.Context
import com.google.ar.core.ArCoreApk
import kotlinx.coroutines.delay

// Can this phone run ARCore? If not, S2 shows the "AR unavailable" state (bigger map, text only).
object ArSupport {

    suspend fun isSupported(context: Context): Boolean {
        // The first answer can be "still checking", so ask a few times.
        repeat(10) {
            val availability = try {
                ArCoreApk.getInstance().checkAvailability(context)
            } catch (e: Exception) {
                return false
            }
            if (!availability.isTransient) return availability.isSupported
            delay(200)
        }
        return false
    }
}

// Debug overlay switch for testing both S2 states on any phone.
enum class ArOverride(val label: String) {
    AUTO("auto"),
    FORCE_ON("forced on"),
    FORCE_OFF("forced off"),
}
