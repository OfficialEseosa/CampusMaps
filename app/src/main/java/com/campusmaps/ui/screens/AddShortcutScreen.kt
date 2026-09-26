package com.campusmaps.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.campusmaps.data.model.GraphNode
import com.campusmaps.data.shortcuts.ShortcutRules
import com.campusmaps.data.shortcuts.ShortcutStatus
import com.campusmaps.data.shortcuts.ShortcutSubmission
import com.campusmaps.route.Formats
import com.campusmaps.ui.DraftPhoto
import com.campusmaps.ui.ShortcutDraft
import com.campusmaps.ui.ShortcutViewModel
import com.campusmaps.ui.components.AppTopBar
import com.campusmaps.ui.components.PrimaryPillButton
import com.campusmaps.ui.components.SectionLabel
import com.campusmaps.ui.components.StatusPill
import com.campusmaps.ui.icons.AppIcons
import com.campusmaps.ui.theme.Space
import java.io.File

// S4 Add a shortcut (section 16). Follows the system theme like S1. Hidden in demo mode.
@Composable
fun AddShortcutScreen(viewModel: ShortcutViewModel, onBack: () -> Unit) {
    val draft by viewModel.draft.collectAsState()
    val submissions by viewModel.submissions.collectAsState()
    val online by viewModel.online.collectAsState()
    val building by viewModel.building.collectAsState()
    val places = remember(building) { viewModel.places(building) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    // ---- Camera: permission, then the system camera app writes into our file. ----
    var pendingPhoto by remember { mutableStateOf<File?>(null) }
    var cameraDenied by rememberSaveable { mutableStateOf(false) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pendingPhoto?.let { viewModel.photoTaken(it, ok) }
        pendingPhoto = null
    }
    fun launchCamera() {
        val (file, uri) = viewModel.newPhotoTarget()
        pendingPhoto = file
        takePicture.launch(uri)
    }
    val askCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraDenied = !granted
        if (granted) launchCamera()
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun addPhoto() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) launchCamera() else askCamera.launch(Manifest.permission.CAMERA)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxSize().imePadding()) {
            AppTopBar(title = "Add a shortcut", onBack = onBack)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = Space.xl, end = Space.xl, top = Space.s, bottom = Space.xxl),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Text(
                    "Found a faster way? Walk it, snap a few photos, and our team reviews it before it goes live for everyone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ProgressSteps(draft.currentStep)

                // ---- Step 1: Walk ----
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlaceField("From", places.firstOrNull { it.id == draft.fromId }, places, viewModel::setFrom, Modifier.weight(1f))
                    PlaceField("To", places.firstOrNull { it.id == draft.toId }, places, viewModel::setTo, Modifier.weight(1f))
                }
                WalkRecorder(draft, onStart = viewModel::startRecording, onStop = viewModel::stopRecording)

                // ---- Step 2: Photos ----
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text("Photos along the way", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Text("${draft.photos.size} of ${ShortcutRules.MAX_PHOTOS}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PhotoGrid(
                        photos = draft.photos,
                        canAdd = draft.photos.size < ShortcutRules.MAX_PHOTOS,
                        onAdd = ::addPhoto,
                        onRemove = viewModel::removePhoto,
                        loadThumbnail = { viewModel.thumbnail(it) },
                    )
                    if (cameraDenied) {
                        CameraPermissionNotice {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                            context.startActivity(intent)
                        }
                    }
                }

                // ---- Optional note ----
                OutlinedTextField(
                    value = draft.note,
                    onValueChange = viewModel::setNote,
                    singleLine = true,
                    label = { Text("Anything we should know? (optional)") },
                    placeholder = { Text("Door is unlocked until 6 pm") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                // ---- Step 3: Submit ----
                PrimaryPillButton(
                    text = draft.missingReason ?: "Submit for review",
                    enabled = draft.canSubmit,
                    height = 56.dp,
                    onClick = {
                        // Ask once for notifications so we can say "Your shortcut ... is live."
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.submit()
                    },
                    modifier = Modifier.testTag("submitShortcut"),
                )

                // ---- Your submissions ----
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Your submissions")
                    if (submissions.isEmpty()) {
                        Text("Nothing submitted yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    submissions.forEach { SubmissionRow(it, online) }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
    }
}

// Three steps joined by 2 dp lines: Walk, Photos, Submit.
@Composable
private fun ProgressSteps(current: Int) {
    val colors = MaterialTheme.colorScheme
    val labels = listOf("Walk", "Photos", "Submit")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            val done = index < current
            val isCurrent = index == current
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            done -> colors.onSurface
                            isCurrent -> colors.primary
                            else -> Color.Transparent
                        },
                    )
                    .then(if (!done && !isCurrent) Modifier.border(1.dp, colors.outlineVariant, CircleShape) else Modifier)
                    .semantics { contentDescription = "$label, " + if (done) "done" else if (isCurrent) "current step" else "upcoming" },
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    Icon(AppIcons.check, contentDescription = null, tint = colors.surface, modifier = Modifier.size(16.dp))
                } else {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isCurrent) colors.onPrimary else colors.onSurfaceVariant,
                    )
                }
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (done || isCurrent) colors.onSurface else colors.onSurfaceVariant,
            )
            if (index < labels.lastIndex) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(2.dp)
                        .background(if (done) colors.onSurface else colors.outlineVariant),
                )
            }
        }
    }
}

// "From" / "To" field: 48 dp, 12 dp corners, opens a searchable list of places.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceField(
    label: String,
    selected: GraphNode?,
    places: List<GraphNode>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colors.outlineVariant),
            color = colors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("place_$label"),
        ) {
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    selected?.name ?: "Choose",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected != null) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Icon(AppIcons.dropdown, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (open) {
        var query by remember { mutableStateOf("") }
        // containerColor = surface: the M3 default (surfaceContainerLow) is not in our scheme and
        // falls back to the baseline lavender. Matches the Settings sheet.
        ModalBottomSheet(
            onDismissRequest = { open = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(Modifier.padding(horizontal = Space.xl), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("$label: pick a place", style = MaterialTheme.typography.titleLarge)
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search room number or name") },
                    leadingIcon = { Icon(AppIcons.search, contentDescription = null) },
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceContainerHigh,
                        unfocusedContainerColor = colors.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                val filtered = places.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { it.id }) { place ->
                        Surface(
                            onClick = {
                                onPick(place.id)
                                open = false
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = colors.surfaceContainer,
                            border = BorderStroke(1.dp, colors.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 56.dp),
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(if (place.isOutdoor) AppIcons.locationOn else AppIcons.doorFront, contentDescription = null, tint = colors.secondary)
                                Column {
                                    Text(place.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (place.isOutdoor) "Outside" else Formats.floorLong(place.floor),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// Big "Start recording walk" button; while recording, a live readout and "Stop recording".
@Composable
private fun WalkRecorder(draft: ShortcutDraft, onStart: () -> Unit, onStop: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    when {
        draft.recording -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(colors.error),
                )
                Text(
                    Formats.distanceAndTime(draft.liveDistanceM, draft.liveSeconds),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onSurface,
                    modifier = Modifier.testTag("recordReadout"),
                )
            }
            OutlinedButton(
                onClick = onStop,
                shape = CircleShape,
                border = BorderStroke(1.5.dp, colors.outline),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Icon(AppIcons.stop, contentDescription = null, tint = colors.secondary)
                Text("Stop recording", style = MaterialTheme.typography.labelLarge, color = colors.secondary, modifier = Modifier.padding(start = 8.dp))
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (draft.hasWalk) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(AppIcons.checkCircle, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(20.dp))
                    Text(
                        "Walk recorded: ${Formats.distanceAndTime(draft.liveDistanceM, (draft.path.lastOrNull()?.timeMs ?: 0L) / 1000.0)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                    )
                }
            }
            val canRecord = draft.fromId != null && draft.toId != null && draft.fromId != draft.toId
            if (draft.hasWalk) {
                // Secondary action once a walk exists, so it does not compete with "Submit for review".
                OutlinedButton(
                    onClick = onStart,
                    enabled = canRecord,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colors.outlineVariant),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.secondary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("recordButton"),
                ) {
                    Text("Record the walk again", style = MaterialTheme.typography.labelLarge)
                }
            } else {
                PrimaryPillButton(
                    text = "Start recording walk",
                    enabled = canRecord,
                    onClick = onStart,
                    height = 56.dp,
                    modifier = Modifier.testTag("recordButton"),
                )
            }
        }
    }
}

// 3 columns, 96 dp tall tiles, 14 dp corners, 10 dp gap. Dashed "Add photo" tile.
@Composable
private fun PhotoGrid(
    photos: List<DraftPhoto>,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onRemove: (DraftPhoto) -> Unit,
    loadThumbnail: suspend (String) -> android.graphics.Bitmap?,
) {
    val tiles: List<DraftPhoto?> = photos + if (canAdd) listOf(null) else emptyList()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { photo ->
                    Box(Modifier.weight(1f).height(96.dp)) {
                        if (photo == null) AddPhotoTile(onAdd) else PhotoTile(photo, onRemove, loadThumbnail)
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PhotoTile(photo: DraftPhoto, onRemove: (DraftPhoto) -> Unit, loadThumbnail: suspend (String) -> android.graphics.Bitmap?) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, photo.path) { value = loadThumbnail(photo.path) }
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = "Shortcut photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Surface(
            onClick = { onRemove(photo) },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(32.dp)
                .semantics { contentDescription = "Remove photo" },
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(AppIcons.close, contentDescription = null, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun AddPhotoTile(onAdd: () -> Unit) {
    val primary = MaterialTheme.colorScheme.secondary
    Surface(
        onClick = onAdd,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxSize()
            .semantics { contentDescription = "Add photo" }
            .testTag("addPhoto"),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = primary,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            Icon(AppIcons.addAPhoto, contentDescription = null, tint = primary)
        }
    }
}

@Composable
private fun CameraPermissionNotice(onOpenSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Camera permission needed to add photos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        OutlinedButton(onClick = onOpenSettings, shape = CircleShape, modifier = Modifier.height(48.dp)) {
            Text("Open settings", style = MaterialTheme.typography.labelMedium)
        }
    }
}

// One row in "Your submissions": name, route line, status pill; reviewer note if rejected.
@Composable
private fun SubmissionRow(submission: ShortcutSubmission, online: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(submission.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = colors.onSurface)
                Text(submission.routeLabel, fontSize = 11.sp, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            when {
                !submission.uploaded -> StatusPill("Waiting to upload", AppIcons.cloudUpload, colors.surfaceContainerHigh, colors.onSurfaceVariant)
                submission.status == ShortcutStatus.PENDING -> StatusPill("Pending review", AppIcons.hourglassTop, colors.primaryContainer, colors.onPrimaryContainer)
                submission.status == ShortcutStatus.APPROVED -> StatusPill("Approved", AppIcons.verified, colors.onSurface, colors.surface)
                else -> StatusPill("Not approved", AppIcons.block, colors.errorContainer, colors.onErrorContainer)
            }
        }
        if (submission.status == ShortcutStatus.REJECTED && !submission.reviewerNote.isNullOrBlank()) {
            Text(submission.reviewerNote, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
        }
        if (!submission.uploaded && !online) {
            Text("Will send when you are back online.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}
