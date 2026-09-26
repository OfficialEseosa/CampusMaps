package com.campusmaps.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.campusmaps.AppContainer
import com.campusmaps.data.Building
import com.campusmaps.data.BuildingEdits
import com.campusmaps.data.BuildingPatch
import com.campusmaps.data.BuildingValidator
import com.campusmaps.data.EditKind
import com.campusmaps.data.Node
import com.campusmaps.data.Problem
import com.campusmaps.data.Severity
import java.io.File

/** What a tap on the plan does. */
enum class EditMode(val label: String, val kind: EditKind?) {
    SELECT("Select", null), ROOM("Add room", EditKind.ROOM), HALLWAY("Add hallway point", EditKind.HALLWAY),
    DOOR("Add door", EditKind.DOOR), CONNECT("Connect", null),
}

// The in-app building editor (Settings > Edit this building). Works on the core building (y north) and turns the difference
// with the asset file into a patch on Save (BuildingPatch.diff). Undo keeps whole building copies (the files are small).
class EditorViewModel(private val app: AppContainer, val code: String) : ViewModel() {
    private val context: Context get() = app.appContext

    val base: Building = PatchStore.asset(context, code)
    private val assetText: String = context.assets.open("buildings/$code.json").bufferedReader().use { it.readText() }

    /** Why the stored patch was skipped at open, if it was. */
    val storedPatchProblem: String?

    var building by mutableStateOf(base); private set
    private var savedBuilding: Building
    private val undo = ArrayDeque<Building>()
    var canUndo by mutableStateOf(false); private set
    val dirty: Boolean get() = building != savedBuilding

    var floor by mutableStateOf(1)
    var mode by mutableStateOf(EditMode.SELECT); private set
    var selectedId by mutableStateOf<String?>(null); private set
    var connectFrom by mutableStateOf<String?>(null); private set
    /** Select mode "Move": the next plan tap moves the selected node there. */
    var moving by mutableStateOf(false); private set
    /** An Add tap waiting for its name: core x, y. */
    var pendingAdd by mutableStateOf<Pair<Double, Double>?>(null); private set
    var problems by mutableStateOf<List<Problem>>(emptyList()); private set
    var showProblems by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    init {
        val stored = PatchStore.read(context, code)
        var why: String? = null
        if (stored != null) {
            val (merged, reason) = BuildingPatch.applyOrSkip(base, stored)
            building = merged; why = reason
        }
        storedPatchProblem = why
        savedBuilding = building
        floor = building.nodes.firstOrNull { it.id.startsWith("E-") }?.floor ?: building.nodes.minOf { it.floor }
        validate()
        if (why != null) message = "Stored patch skipped: $why"
    }

    val floors: List<Int> get() = building.nodes.map { it.floor }.distinct().sorted()
    val selected: Node? get() = selectedId?.let { building.nodeOrNull(it) }
    val patch: BuildingPatch get() = BuildingPatch.diff(base, building, note = "Edited on the phone")
    val errorCount: Int get() = problems.count { it.severity == Severity.ERROR }
    val warningCount: Int get() = problems.count { it.severity == Severity.WARN }

    private fun change(next: Building) {
        if (next == building) return
        undo.addLast(building); if (undo.size > 50) undo.removeFirst()
        building = next; canUndo = true
    }

    fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        building = prev; canUndo = undo.isNotEmpty()
        if (selectedId != null && building.nodeOrNull(selectedId!!) == null) selectedId = null
        message = "Undone"
    }

    fun chooseMode(m: EditMode) {
        mode = m; connectFrom = null; moving = false; pendingAdd = null
        if (m != EditMode.SELECT) selectedId = null
    }

    /** A tap on the plan at core (x, y); [nodeId] is the node under the finger, if any. */
    fun tap(x: Double, y: Double, nodeId: String?) {
        when (mode) {
            EditMode.SELECT -> {
                val sel = selectedId
                if (moving && sel != null) {
                    change(BuildingEdits.move(building, sel, x, y)); moving = false
                    message = "Moved $sel"
                } else selectedId = nodeId
            }
            EditMode.ROOM, EditMode.HALLWAY, EditMode.DOOR -> pendingAdd = x to y
            EditMode.CONNECT -> {
                if (nodeId == null) return
                val from = connectFrom
                if (from == null) { connectFrom = nodeId; return }
                if (from == nodeId) { connectFrom = null; return }
                val next = BuildingEdits.connect(building, from, nodeId)
                if (next == null) message = "$from and $nodeId are already linked (or cannot be linked)"
                else { change(next); message = "Linked $from to $nodeId (${"%.1f".format(next.edges.last().lengthM)} m)" }
                connectFrom = null
            }
        }
    }

    fun cancelAdd() { pendingAdd = null }

    fun confirmAdd(name: String) {
        val kind = mode.kind ?: return
        val (x, y) = pendingAdd ?: return
        val (next, id) = BuildingEdits.addNode(building, kind, name, floor, x, y)
        change(next); pendingAdd = null
        message = "Added $id. Use Connect to link it to a hallway point."
    }

    /** The id the next Add would get, for the name sheet. */
    fun previewId(name: String): String = mode.kind?.let { BuildingEdits.newId(building, it, name) } ?: ""

    fun rename(id: String, name: String) { if (name.isNotBlank()) change(BuildingEdits.update(building, id) { it.copy(name = name.trim()) }) }
    fun setFloor(id: String, f: Int) { change(BuildingEdits.update(building, id) { it.copy(floor = f) }) }
    fun nudge(id: String, dx: Double, dy: Double) {
        val n = building.nodeOrNull(id) ?: return
        change(BuildingEdits.move(building, id, n.x + dx, n.y + dy))
    }
    fun startMove() { moving = true; message = "Tap the plan where the point should go" }
    fun delete(id: String) { change(BuildingEdits.delete(building, id)); selectedId = null; message = "Deleted $id" }
    fun unlink(a: String, b: String) { change(BuildingEdits.disconnect(building, a, b)) }
    fun deselect() { selectedId = null; moving = false }

    fun links(id: String): List<Pair<String, String>> = building.edges.filter { it.from == id || it.to == id }
        .map { e -> (if (e.from == id) e.to else e.from) to "${e.kind.name.lowercase()} ${"%.1f".format(e.lengthM)} m" }

    private fun validate() {
        problems = BuildingValidator.validate(building, imageExists = { path ->
            try { context.assets.open(path).close(); true } catch (_: Exception) { false }
        }).filter { it.severity != Severity.INFO }
    }

    /** Writes the patch (or deletes it when there is nothing left), reloads every building so routing sees it, re-validates. */
    fun save() {
        val p = patch
        try {
            if (p.isEmpty) PatchStore.delete(context, code) else PatchStore.write(context, p)
            savedBuilding = building
            app.reloadBuildings()
            validate()
            message = "Saved (${p.summary()}). Routing uses it now. Validator: $errorCount errors, $warningCount warnings"
            Log.i(PatchStore.TAG, "$code: saved patch ${p.summary()}")
        } catch (e: Exception) {
            Log.e(PatchStore.TAG, "$code: save failed", e)
            message = "Save failed: ${e.message}"
        }
    }

    fun resetPatch() {
        PatchStore.delete(context, code)
        undo.clear(); canUndo = false
        building = base; savedBuilding = base; selectedId = null
        app.reloadBuildings(); validate()
        message = "Patch removed. Back to the building file."
    }

    /**
     * Saves, writes the merged file (asset text plus patch) to cache/exports/<code>.json and to the app's external files
     * (Android/data/com.campusmaps/files/exports, easy to adb pull), copies the patch JSON to the clipboard, and returns a share intent.
     */
    fun export(): Intent? {
        if (dirty) save()
        return try {
            val merged = BuildingPatch.applyToJson(assetText, patch)
            val cache = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(cache, "$code.json").apply { writeText(merged) }
            context.getExternalFilesDir(null)?.let { ext ->
                runCatching { File(ext, "exports").apply { mkdirs() }.resolve("$code.json").writeText(merged) }
            }
            val clip = context.getSystemService(ClipboardManager::class.java)
            clip?.setPrimaryClip(ClipData.newPlainText("$code patch", patch.toJson()))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            message = "Merged $code.json ready to share; the patch is on the clipboard"
            Log.i(PatchStore.TAG, "$code: exported ${file.path}")
            Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "$code.json (building file with phone edits)")
                putExtra(Intent.EXTRA_TEXT, "Replace app/src/main/assets/buildings/$code.json with the attached file. Patch: ${patch.summary()}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.let { Intent.createChooser(it, "Export $code.json") }
        } catch (e: Exception) {
            Log.e(PatchStore.TAG, "$code: export failed", e)
            message = "Export failed: ${e.message}"
            null
        }
    }

    class Factory(private val app: AppContainer, private val code: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = EditorViewModel(app, code) as T
    }
}
