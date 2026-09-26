package com.campusmaps.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.campusmaps.AppContainer
import com.campusmaps.data.model.Building
import com.campusmaps.data.model.GraphNode
import com.campusmaps.data.model.NodeKind
import com.campusmaps.data.shortcuts.PathPoint
import com.campusmaps.data.shortcuts.ShortcutPhoto
import com.campusmaps.data.shortcuts.ShortcutRules
import com.campusmaps.data.shortcuts.ShortcutStatus
import com.campusmaps.data.shortcuts.ShortcutSubmission
import com.campusmaps.guidance.Pose
import com.campusmaps.guidance.SimulatedPositionProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

// A photo taken for the draft, tagged with the path point where it was taken.
data class DraftPhoto(val path: String, val atPointIndex: Int)

// The shortcut being put together on S4.
data class ShortcutDraft(
    val fromId: String? = null,
    val toId: String? = null,
    val recording: Boolean = false,
    val recordStartMs: Long = 0L,
    val path: List<PathPoint> = emptyList(),
    val liveDistanceM: Double = 0.0,
    val liveSeconds: Double = 0.0,
    val photos: List<DraftPhoto> = emptyList(),
    val note: String = "",
    val submitting: Boolean = false,
    val walkConnects: Boolean = true,
) {
    val hasWalk: Boolean get() = !recording && path.size >= 2
    val missingReason: String? get() =
        ShortcutRules.missingReason(fromId != null, toId != null, hasWalk, photos.size, walkConnects)
    val canSubmit: Boolean get() = missingReason == null && !submitting

    // Progress steps: 0 = Walk, 1 = Photos, 2 = Submit.
    val currentStep: Int get() = when {
        fromId == null || toId == null || !hasWalk || !walkConnects -> 0
        photos.size < ShortcutRules.MIN_PHOTOS -> 1
        else -> 2
    }
}

class ShortcutViewModel(private val app: AppContainer) : ViewModel() {

    private val _draft = MutableStateFlow(ShortcutDraft())
    val draft: StateFlow<ShortcutDraft> = _draft.asStateFlow()

    val submissions: StateFlow<List<ShortcutSubmission>> = app.shortcuts.submissions
    val online: StateFlow<Boolean> = app.network.online

    // The building comes from settings (same as S1).
    val building: StateFlow<Building> = app.settings.settings
        .map { app.building(it.buildingId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, app.building(com.campusmaps.data.campus.DemoBuildings.DEFAULT_BUILDING_ID))

    // One-off messages for the snackbar.
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var recordJob: Job? = null
    private var walker: SimulatedPositionProvider? = null

    // Places a student can pick for From / To: rooms, entrances, named spots and outside points.
    fun places(building: Building): List<GraphNode> = building.nodes.values
        .filter { it.kind != NodeKind.ELEVATOR && it.kind != NodeKind.STAIRS }
        .distinctBy { it.name }
        .sortedWith(compareBy({ it.kind != NodeKind.ROOM }, { it.name }))

    fun setFrom(id: String) = _draft.update { it.copy(fromId = id, path = emptyList()) }
    fun setTo(id: String) = _draft.update { it.copy(toId = id, path = emptyList()) }
    fun setNote(text: String) = _draft.update { it.copy(note = text) }

    // Records the walk as a list of positions from the localisation system.
    // Until real localisation exists, a simulated walker walks From -> To so the flow can be tested.
    fun startRecording() {
        val d = _draft.value
        val building = building.value
        val from = d.fromId ?: return
        val to = d.toId ?: return
        val route = app.router.bestRoute(building, from, to, app.clock.now(), avoidStairs = false,
            extraEdges = app.shortcuts.approvedEdges.value[building.id].orEmpty())
        val start = building.node(from)
        val sim = SimulatedPositionProvider(
            scope = viewModelScope,
            startPose = Pose(start.position, start.floor, 0.0, 0.92f),
            startLost = false,
            signText = { start.signText ?: start.name.uppercase() },
        )
        route?.let { sim.follow(it) }
        walker = sim

        val startedAt = System.currentTimeMillis()
        _draft.update { it.copy(recording = true, recordStartMs = startedAt, path = emptyList(), liveDistanceM = 0.0, liveSeconds = 0.0) }
        recordJob?.cancel()
        recordJob = viewModelScope.launch {
            var lastSample = 0L
            while (isActive) {
                val now = System.currentTimeMillis()
                val pose = sim.pose.value
                _draft.update { current ->
                    // Sample one path point per second (and always the first one).
                    val path = if (current.path.isEmpty() || now - lastSample >= 1_000) {
                        lastSample = now
                        current.path + PathPoint(pose.position.x, pose.position.y, pose.floor, now - startedAt)
                    } else {
                        current.path
                    }
                    current.copy(
                        path = path,
                        liveDistanceM = ShortcutRules.pathLengthM(path),
                        liveSeconds = (now - startedAt) / 1000.0,
                    )
                }
                delay(200)
            }
        }
    }

    fun stopRecording() {
        recordJob?.cancel()
        recordJob = null
        val pose = walker?.pose?.value
        walker?.stop()
        walker = null
        _draft.update { d ->
            val finalPath = if (pose != null) {
                d.path + PathPoint(pose.position.x, pose.position.y, pose.floor, System.currentTimeMillis() - d.recordStartMs)
            } else {
                d.path
            }
            val b = building.value
            val connects = d.fromId != null && d.toId != null &&
                ShortcutRules.walkConnects(finalPath, b.node(d.fromId), b.node(d.toId))
            d.copy(
                recording = false,
                path = finalPath,
                liveDistanceM = ShortcutRules.pathLengthM(finalPath),
                walkConnects = connects,
            )
        }
    }

    // Where the camera app should save the next photo.
    fun newPhotoTarget() = app.photos.newPhotoTarget()

    // Called after the camera app returns. Shrinks the photo and tags it with the current path point.
    fun photoTaken(file: File, success: Boolean) {
        if (!success || !file.exists() || file.length() == 0L) {
            file.delete()
            return
        }
        viewModelScope.launch {
            app.photos.compress(file)
            _draft.update { d ->
                if (d.photos.size >= ShortcutRules.MAX_PHOTOS) d
                else d.copy(photos = d.photos + DraftPhoto(file.path, atPointIndex = (d.path.size - 1).coerceAtLeast(0)))
            }
        }
    }

    fun removePhoto(photo: DraftPhoto) {
        app.photos.delete(photo.path)
        _draft.update { d -> d.copy(photos = d.photos - photo) }
    }

    suspend fun thumbnail(path: String) = app.photos.thumbnail(path)

    fun submit() {
        val d = _draft.value
        if (!d.canSubmit) return
        val building = building.value
        val from = building.node(d.fromId!!)
        val to = building.node(d.toId!!)
        _draft.update { it.copy(submitting = true) }
        viewModelScope.launch {
            val submission = ShortcutSubmission(
                id = UUID.randomUUID().toString(),
                submitterId = app.submitterIdProvider.currentId(),
                name = ShortcutRules.autoName(from.name),
                fromNodeId = from.id,
                toNodeId = to.id,
                path = d.path,
                photos = d.photos.map { ShortcutPhoto(uri = File(it.path).toURI().toString(), atPointIndex = it.atPointIndex) },
                note = d.note.trim().ifBlank { null },
                status = ShortcutStatus.PENDING,
                reviewerNote = null,
                createdAt = System.currentTimeMillis(),
                buildingId = building.id,
                routeLabel = "${from.name} to ${to.name}",
            )
            app.shortcuts.submit(submission)
            _draft.value = ShortcutDraft()
            _messages.send("Sent for review. We'll show it to everyone once it's approved.")
        }
    }

    override fun onCleared() {
        walker?.stop()
    }

    class Factory(private val app: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ShortcutViewModel(app) as T
    }
}
