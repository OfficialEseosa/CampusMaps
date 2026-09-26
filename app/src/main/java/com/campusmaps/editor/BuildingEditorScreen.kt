package com.campusmaps.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusmaps.AppContainer
import com.campusmaps.data.EdgeKind
import com.campusmaps.data.NodeType
import com.campusmaps.data.BuildingEdits
import com.campusmaps.ui.theme.Sora
import kotlin.math.hypot
import kotlin.math.min

private val Ink = Color(0xFF313131)
private val Cream = Color(0xFFF9F2ED)
private val ClayDeep = Color(0xFFA85F33)
private val DoorTeal = Color(0xFF2F6B7A)
private val EntranceGreen = Color(0xFF2E7D32)
private val VerticalPlum = Color(0xFF6E5A4E)
private val Corridor = Color(0xFFD9C8BC)

// Settings > "Edit this building" (demo mode off). Floor plan with pan and zoom; modes as chips; Save writes the patch.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildingEditorScreen(app: AppContainer, code: String, onClose: () -> Unit) {
    val vm: EditorViewModel = viewModel(key = "editor-$code", factory = EditorViewModel.Factory(app, code))
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    val leave = { if (vm.dirty) confirmLeave = true else onClose() }
    BackHandler { leave() }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    Scaffold(
        modifier = Modifier.testTag("buildingEditor"),
        topBar = {
            TopAppBar(
                title = { Text("Edit ${vm.base.name}", fontFamily = Sora, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1) },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
                    IconButton(onClick = vm::save) { Icon(Icons.Rounded.Save, "Save", tint = if (vm.dirty) ClayDeep else LocalContentColorOr()) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Export merged file") }, onClick = {
                                menu = false
                                vm.export()?.let { context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            })
                            DropdownMenuItem(text = { Text("Reset patch") }, onClick = { menu = false; confirmReset = true })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Status: validator counts and the patch summary.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val color = if (vm.errorCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                Text("${vm.errorCount} errors, ${vm.warningCount} warnings", color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                if (vm.problems.isNotEmpty()) {
                    Text("Show", color = ClayDeep, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { vm.showProblems = true }.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                Text((if (vm.dirty) "Unsaved: " else "Patch: ") + vm.patch.summary(), fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            // Floor picker.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                vm.floors.forEach { f ->
                    FilterChip(selected = f == vm.floor, onClick = { vm.floor = f }, label = { Text("Floor $f") }, colors = chipColors())
                }
            }
            // Modes.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EditMode.entries.forEach { m ->
                    FilterChip(selected = m == vm.mode, onClick = { vm.chooseMode(m) }, label = { Text(m.label) }, colors = chipColors())
                }
            }
            val hint = when {
                vm.mode == EditMode.SELECT && vm.moving -> "Tap where ${vm.selectedId} should go"
                vm.mode == EditMode.SELECT -> "Tap a point to change it. Pinch to zoom, drag to pan."
                vm.mode == EditMode.CONNECT -> vm.connectFrom?.let { "From $it: tap the second point" } ?: "Tap the first point to link"
                else -> "Tap the plan where the new ${vm.mode.label.removePrefix("Add ")} goes"
            }
            Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                PlanCanvas(vm, Modifier.fillMaxSize())
                vm.selected?.takeIf { vm.mode == EditMode.SELECT }?.let { n ->
                    NodeCard(vm, n.id, Modifier.align(Alignment.BottomCenter).padding(10.dp))
                }
            }
        }
    }

    if (vm.pendingAdd != null) AddDialog(vm)
    if (vm.showProblems) {
        AlertDialog(
            onDismissRequest = { vm.showProblems = false },
            confirmButton = { TextButton(onClick = { vm.showProblems = false }) { Text("Close") } },
            title = { Text("Validator: ${vm.errorCount} errors, ${vm.warningCount} warnings") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    vm.problems.forEach { Text(it.toString(), fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) }
                }
            },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            confirmButton = { TextButton(onClick = { confirmReset = false; vm.resetPatch() }) { Text("Reset") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
            title = { Text("Reset patch?") },
            text = { Text("Removes every phone edit for ${vm.code} and goes back to the building file in the app.") },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            confirmButton = { TextButton(onClick = { confirmLeave = false; vm.save(); onClose() }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { confirmLeave = false; onClose() }) { Text("Discard") } },
            title = { Text("Unsaved changes") },
            text = { Text("Save the edits to ${vm.code} before leaving?") },
        )
    }
}

@Composable
private fun LocalContentColorOr(): Color = androidx.compose.material3.LocalContentColor.current

@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = ClayDeep, selectedLabelColor = Color.White,
)

@Composable
private fun PlanCanvas(vm: EditorViewModel, modifier: Modifier) {
    val b = vm.building
    val density = LocalDensity.current
    var zoom by remember(vm.code) { mutableFloatStateOf(1f) }
    var pan by remember(vm.code) { mutableStateOf(Offset.Zero) }
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = Sora, fontSize = 10.sp, color = Ink, fontWeight = FontWeight.SemiBold)
    // Fit the shown floor's points (plan coordinates: x east, y down = -core y), at least 20 m across.
    val shown = b.nodes.filter { it.floor == vm.floor }.ifEmpty { b.nodes }
    val cx = (shown.minOf { it.x } + shown.maxOf { it.x }) / 2; val cy = (shown.minOf { -it.y } + shown.maxOf { -it.y }) / 2
    val xs = shown.map { it.x } + listOf(cx - 10, cx + 10); val ys = shown.map { -it.y } + listOf(cy - 10, cy + 10)
    val minX = (xs.minOrNull() ?: 0.0) - 3; val maxX = (xs.maxOrNull() ?: 10.0) + 3
    val minY = (ys.minOrNull() ?: 0.0) - 3; val maxY = (ys.maxOrNull() ?: 10.0) + 3

    Canvas(
        modifier
            .background(Cream)
            .pointerInput(vm.code) {
                detectTransformGestures { centroid, panChange, zoomChange, _ ->
                    val newZoom = (zoom * zoomChange).coerceIn(0.5f, 12f)
                    val k = newZoom / zoom
                    pan = centroid - (centroid - pan) * k + panChange
                    zoom = newZoom
                }
            }
            .pointerInput(vm.code, vm.floor, b) {
                detectTapGestures { tap ->
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    val fit = Fit(w, h, minX, maxX, minY, maxY, 16 * density.density)
                    val p = (tap - pan) / zoom
                    val px = fit.planX(p.x); val py = fit.planY(p.y)
                    val hitPx = 26 * density.density
                    val hit = b.nodes.filter { it.floor == vm.floor }
                        .map { it to hypot((fit.sx(it.x) * zoom + pan.x - tap.x).toDouble(), (fit.sy(-it.y) * zoom + pan.y - tap.y).toDouble()) }
                        .filter { it.second <= hitPx }.minByOrNull { it.second }?.first
                    vm.tap(px, -py, hit?.id)
                }
            },
    ) {
        val fit = Fit(size.width, size.height, minX, maxX, minY, maxY, 16.dp.toPx())
        fun at(x: Double, yUp: Double) = Offset(fit.sx(x) * zoom + pan.x, fit.sy(-yUp) * zoom + pan.y)
        // 5 m grid for a sense of scale.
        val step = 5.0
        var gx = kotlin.math.floor(minX / step) * step
        while (gx <= maxX) { drawLine(Corridor.copy(alpha = 0.35f), at(gx, -minY), at(gx, -maxY), 1f); gx += step }
        var gy = kotlin.math.floor(minY / step) * step
        while (gy <= maxY) { drawLine(Corridor.copy(alpha = 0.35f), at(minX, -gy), at(maxX, -gy), 1f); gy += step }

        for (e in b.edges) {
            val f = b.nodeOrNull(e.from) ?: continue; val t = b.nodeOrNull(e.to) ?: continue
            if (f.floor != vm.floor || t.floor != vm.floor) continue
            val door = e.kind == EdgeKind.DOOR
            drawLine(
                color = if (door) DoorTeal else Ink.copy(alpha = 0.55f),
                start = at(f.x, f.y), end = at(t.x, t.y), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = if (door) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null,
            )
        }
        for (n in b.nodes.filter { it.floor == vm.floor }) {
            val c = at(n.x, n.y)
            val color = when {
                n.type == NodeType.ROOM -> ClayDeep
                n.type == NodeType.ENTRANCE -> EntranceGreen
                n.type == NodeType.STAIRS || n.type == NodeType.ELEVATOR -> VerticalPlum
                BuildingEdits.isDoor(n) -> DoorTeal
                else -> Ink
            }
            val r = if (n.type == NodeType.ROOM) 7.dp.toPx() else 5.dp.toPx()
            if (n.id == vm.selectedId || n.id == vm.connectFrom) drawCircle(ClayDeep.copy(alpha = 0.25f), r + 9.dp.toPx(), c)
            drawCircle(color, r, c)
            drawCircle(Color.White, r, c, style = Stroke(1.5.dp.toPx()))
            // Vertical links (stairs, elevator) leave the floor: a small ring marks them.
            if (b.edges.any { it.kind.vertical && (it.from == n.id || it.to == n.id) }) drawCircle(VerticalPlum, r + 3.dp.toPx(), c, style = Stroke(1.dp.toPx()))
            drawText(measurer, n.id, topLeft = c + Offset(r + 3.dp.toPx(), -r - 8.dp.toPx()), style = label)
        }
    }
}

/** Plan metres (x east, y down) to canvas pixels before the user's zoom and pan, keeping the aspect ratio. */
private class Fit(w: Float, h: Float, val minX: Double, maxX: Double, val minY: Double, maxY: Double, pad: Float) {
    val scale = min((w - 2 * pad) / (maxX - minX), (h - 2 * pad) / (maxY - minY)).toFloat()
    val ox = (w - (maxX - minX).toFloat() * scale) / 2f
    val oy = (h - (maxY - minY).toFloat() * scale) / 2f
    fun sx(x: Double) = ox + (x - minX).toFloat() * scale
    fun sy(y: Double) = oy + (y - minY).toFloat() * scale
    fun planX(px: Float) = minX + (px - ox) / scale
    fun planY(py: Float) = minY + (py - oy) / scale
}

@Composable
private fun AddDialog(vm: EditorViewModel) {
    var name by remember { mutableStateOf("") }
    val isRoom = vm.mode == EditMode.ROOM
    AlertDialog(
        onDismissRequest = vm::cancelAdd,
        confirmButton = {
            Button(onClick = { vm.confirmAdd(name) }, colors = ButtonDefaults.buttonColors(containerColor = ClayDeep)) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = vm::cancelAdd) { Text("Cancel") } },
        title = { Text(vm.mode.label + " on floor ${vm.floor}") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(if (isRoom) "Room name, e.g. Room 612" else "Name (optional)") },
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.testTag("editorName"),
                )
                Text("Id: ${vm.previewId(name)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
        },
    )
}

@Composable
private fun NodeCard(vm: EditorViewModel, id: String, modifier: Modifier) {
    val n = vm.building.nodeOrNull(id) ?: return
    var name by remember(id, n.name) { mutableStateOf(n.name) }
    var confirmDelete by remember(id) { mutableStateOf(false) }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${n.id}  ·  ${n.type.name.lowercase()}  ·  floor ${n.floor}", fontWeight = FontWeight.Bold, fontFamily = Sora, color = Ink)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = vm::deselect) { Text("Close") }
            }
            Text("x ${"%.2f".format(n.x)} m east, y ${"%.2f".format(n.y)} m north", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.rename(id, name) }, enabled = name != n.name && name.isNotBlank()) { Text("Rename") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Nudge 1 m", fontSize = 12.sp)
                SmallBtn("W") { vm.nudge(id, -1.0, 0.0) }
                SmallBtn("E") { vm.nudge(id, 1.0, 0.0) }
                SmallBtn("N") { vm.nudge(id, 0.0, 1.0) }
                SmallBtn("S") { vm.nudge(id, 0.0, -1.0) }
                Spacer(Modifier.width(4.dp))
                SmallBtn("Move") { vm.startMove() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Floor", fontSize = 12.sp)
                SmallBtn("-") { vm.setFloor(id, n.floor - 1) }
                SmallBtn("+") { vm.setFloor(id, n.floor + 1) }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
            val links = vm.links(id)
            if (links.isNotEmpty()) {
                Text("Links (tap to remove):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    links.forEach { (other, what) -> SmallBtn("$other ($what) x") { vm.unlink(id, other) } }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
            title = { Text("Delete $id?") },
            text = { Text("Removes the point and its links (Undo brings it back).") },
        )
    }
}

@Composable
private fun SmallBtn(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        modifier = Modifier.heightIn(min = 36.dp)) { Text(text, fontSize = 12.sp, color = Ink) }
}
