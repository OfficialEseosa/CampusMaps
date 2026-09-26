package com.campusmaps.glasses

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

// ML Kit text recognition (bundled model) on one glasses still. Returns the lines tall enough to
// be a sign (docs/03 step 3: at least 20 px), joined with newlines, ready for SignVoter.
class SignReader {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(bitmap: Bitmap): String {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        return result.textBlocks.flatMap { it.lines }
            .filter { (it.boundingBox?.height() ?: 0) >= MIN_LINE_PX }
            .joinToString("\n") { it.text }
    }

    fun close() = recognizer.close()

    companion object { const val MIN_LINE_PX = 20 }
}
