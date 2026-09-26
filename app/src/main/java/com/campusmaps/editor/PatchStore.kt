package com.campusmaps.editor

import android.content.Context
import android.util.Log
import com.campusmaps.data.Building
import com.campusmaps.data.BuildingLoader
import com.campusmaps.data.BuildingPatch
import java.io.File

// Field fixes on top of the asset building files. One patch per building at filesDir/patches/<code>.json.
// The asset in git stays the source of truth; CoreBridge.load applies the stored patch after parsing each asset.
object PatchStore {
    const val TAG = "BuildingEditor"

    private fun dir(context: Context) = File(context.filesDir, "patches")
    fun file(context: Context, code: String) = File(dir(context), "$code.json")

    /** The stored patch, or null when there is none or it cannot be read (logged). */
    fun read(context: Context, code: String): BuildingPatch? {
        val f = file(context, code)
        if (!f.exists()) return null
        return try {
            BuildingPatch.fromJson(f.readText())
        } catch (e: Exception) {
            Log.e(TAG, "$code: cannot read patch ${f.path}, skipped", e)
            null
        }
    }

    fun write(context: Context, patch: BuildingPatch) {
        val f = file(context, patch.building)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, "${f.name}.tmp")
        tmp.writeText(patch.toJson())
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun delete(context: Context, code: String): Boolean = file(context, code).delete()

    /** The asset file as shipped, without any patch. */
    fun asset(context: Context, code: String): Building =
        context.assets.open("buildings/$code.json").bufferedReader().use { BuildingLoader.fromJson(it.readText()) }

    /** [asset] with the stored patch on top. A patch that does not fit is logged and skipped; never throws for a bad patch. */
    fun applyStored(context: Context, asset: Building): Building {
        val patch = read(context, asset.code) ?: return asset
        val (merged, why) = BuildingPatch.applyOrSkip(asset, patch)
        if (why != null) Log.e(TAG, "${asset.code}: patch skipped: $why")
        else Log.i(TAG, "${asset.code}: patch applied (${patch.summary()})")
        return merged
    }
}
