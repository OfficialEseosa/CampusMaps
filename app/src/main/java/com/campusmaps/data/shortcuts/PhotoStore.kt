package com.campusmaps.data.shortcuts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

// Where shortcut photos live on the phone, and how they are shrunk before upload.
class PhotoStore(private val context: Context) {

    private val folder: File get() = File(context.filesDir, "shortcut_photos").apply { mkdirs() }

    // A fresh file plus a content:// URI the camera app is allowed to write into.
    fun newPhotoTarget(): Pair<File, Uri> {
        val file = File(folder, "photo_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    // Shrinks a photo so its long side is about 1600 px (section 16.6) and overwrites it.
    suspend fun compress(file: File) = withContext(Dispatchers.IO) {
        if (!file.exists() || file.length() == 0L) return@withContext
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longSide = max(bounds.outWidth, bounds.outHeight)
        if (longSide <= ShortcutRules.PHOTO_LONG_SIDE_PX) return@withContext

        // Decode at a power of two first (cheap), then scale to the exact size.
        var sample = 1
        while (longSide / (sample * 2) >= ShortcutRules.PHOTO_LONG_SIDE_PX) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return@withContext
        val scale = ShortcutRules.PHOTO_LONG_SIDE_PX.toFloat() / max(decoded.width, decoded.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).roundToInt(), (decoded.height * scale).roundToInt(), true)
        } else {
            decoded
        }
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    // Small preview for the photo grid tiles.
    suspend fun thumbnail(path: String, targetPx: Int = 300): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return@withContext null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    fun delete(path: String) {
        File(path).delete()
    }
}
