package com.campusmaps.data.shortcuts

import com.campusmaps.data.model.EdgeKind
import com.campusmaps.data.model.EdgeSource
import com.campusmaps.data.model.GraphEdge
import com.campusmaps.data.model.GraphNode
import kotlinx.serialization.Serializable
import kotlin.math.hypot

// One shortcut a student submitted. Stored on the backend, cached on the phone.
@Serializable
data class ShortcutSubmission(
    val id: String,                    // UUID made on the phone
    val submitterId: String,           // From SubmitterIdProvider (device ID for now)
    val name: String,                  // Short title, e.g. "Library cut through"
    val fromNodeId: String,            // Start place or room node
    val toNodeId: String,              // End place or room node
    val path: List<PathPoint>,         // Recorded walk, in order
    val photos: List<ShortcutPhoto>,   // 2 to 6 photos
    val note: String?,                 // Optional tip from the student
    val status: ShortcutStatus,        // PENDING, APPROVED or REJECTED
    val reviewerNote: String?,         // Why it was rejected, if it was
    val createdAt: Long,               // Epoch millis
    // Extra fields the phone needs:
    val buildingId: String,            // Which building file the nodes belong to
    val routeLabel: String,            // "Library South to Langdale Hall", shown on the row
    val uploaded: Boolean = false,     // False = still queued on the phone ("Waiting to upload")
)

@Serializable
data class PathPoint(val x: Double, val y: Double, val floor: Int, val timeMs: Long)

@Serializable
data class ShortcutPhoto(val uri: String, val atPointIndex: Int) // Which path point the photo was taken at

@Serializable
enum class ShortcutStatus { PENDING, APPROVED, REJECTED }

// Rules from section 16.
object ShortcutRules {
    const val MIN_PHOTOS = 2
    const val MAX_PHOTOS = 6
    const val PHOTO_LONG_SIDE_PX = 1600

    // How far (metres) the recorded walk may start/end from the From/To place, for localisation drift.
    const val END_TOLERANCE_M = 8.0

    // True when the walk starts at From and ends at To, on their floors.
    fun walkConnects(path: List<PathPoint>, from: GraphNode, to: GraphNode): Boolean {
        fun PathPoint.near(n: GraphNode) = floor == n.floor && hypot(x - n.position.x, y - n.position.y) <= END_TOLERANCE_M
        return path.size >= 2 && path.first().near(from) && path.last().near(to)
    }

    // Length of a recorded walk in metres (only counts moves on the same floor).
    fun pathLengthM(path: List<PathPoint>): Double =
        path.zipWithNext().sumOf { (a, b) -> if (a.floor == b.floor) hypot(b.x - a.x, b.y - a.y) else 0.0 }

    // What an approved shortcut becomes in the route graph: a walking edge marked STUDENT.
    fun toEdge(s: ShortcutSubmission): GraphEdge = GraphEdge(
        from = s.fromNodeId,
        to = s.toNodeId,
        kind = EdgeKind.WALK,
        lengthM = pathLengthM(s.path).coerceAtLeast(1.0),
        source = EdgeSource.STUDENT,
        shortcutName = s.name,
    )

    // Name shown on "Your submissions": "Library South cut through".
    fun autoName(fromName: String): String = "${fromName.removeSuffix(" entrance")} cut through"

    // Text on the disabled Submit button, or null when everything is ready.
    fun missingReason(
        hasFrom: Boolean,
        hasTo: Boolean,
        hasWalk: Boolean,
        photoCount: Int,
        walkConnects: Boolean = true,
    ): String? = when {
        !hasFrom || !hasTo -> "Pick From and To to submit"
        !hasWalk -> "Record your walk to submit"
        !walkConnects -> "Walk all the way from From to To to submit"
        photoCount < MIN_PHOTOS -> {
            val missing = MIN_PHOTOS - photoCount
            if (missing == 1) "Add 1 photo to submit" else "Add $missing photos to submit"
        }
        else -> null
    }
}
