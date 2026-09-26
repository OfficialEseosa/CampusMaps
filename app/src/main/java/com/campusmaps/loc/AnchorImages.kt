package com.campusmaps.loc

import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.BitmapFactory
import android.util.Log
import com.google.ar.core.AugmentedImageDatabase
import com.google.ar.core.Session
import java.io.FileNotFoundException

/**
 * The Augmented Images database for the AR session (docs/03 section 1). Image names are anchor ids ("CS-A01").
 *
 * 1. `assets/anchors/anchors.imgdb`, built with arcoreimg from images that scored >= 75 (see assets/anchors/SCORES.md).
 * 2. If that file is absent, debug builds add `assets/anchors/<code>/<id>.jpg|png` at run time (width from the building
 *    file); images ARCore rejects are skipped and logged. Release builds use no database then.
 */
object AnchorImages {
    private const val TAG = "AnchorImages"
    const val DB_ASSET = "anchors/anchors.imgdb"

    /** Last result, for logs and the debug card. */
    @Volatile var status: String = "not loaded"
        private set

    fun load(context: Context, session: Session): AugmentedImageDatabase? {
        try {
            val db = context.assets.open(DB_ASSET).use { AugmentedImageDatabase.deserialize(session, it) }
            status = "$DB_ASSET: ${db.numImages} images"
            Log.i(TAG, status)
            return db
        } catch (_: FileNotFoundException) {
            // Fall through to the run-time database.
        } catch (e: Exception) {
            status = "$DB_ASSET failed: ${e.message}"
            Log.e(TAG, status, e)
        }
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!debuggable) {
            status = "no $DB_ASSET (release: no image anchors)"
            Log.w(TAG, status)
            return null
        }
        val db = AugmentedImageDatabase(session)
        val added = mutableListOf<String>(); val skipped = mutableListOf<String>()
        val dirs = context.assets.list("anchors").orEmpty()
        for (dir in dirs) {
            val files = context.assets.list("anchors/$dir").orEmpty()
            for (f in files) {
                if (!(f.endsWith(".jpg", true) || f.endsWith(".jpeg", true) || f.endsWith(".png", true))) continue
                val id = f.substringBeforeLast('.')
                try {
                    val bmp = context.assets.open("anchors/$dir/$f").use { BitmapFactory.decodeStream(it) } ?: throw IllegalStateException("decode failed")
                    val w = ArFeed.anchors[id]?.widthM?.takeIf { it > 0 }
                    if (w != null) db.addImage(id, bmp, w.toFloat()) else db.addImage(id, bmp)
                    added += id
                } catch (e: Exception) {
                    skipped += "$id (${e.javaClass.simpleName})"
                }
            }
        }
        status = "run-time database: ${added.size} images ${added}; skipped ${skipped}"
        Log.i(TAG, status)
        return db.takeIf { it.numImages > 0 }
    }
}
