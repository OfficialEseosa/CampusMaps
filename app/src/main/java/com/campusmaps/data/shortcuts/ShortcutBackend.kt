package com.campusmaps.data.shortcuts

import kotlinx.coroutines.delay
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

// The review queue on the server (section 16.5). The phone only talks to it through this interface,
// so the fake below can be swapped for a real HTTP client without touching any screen.
interface ShortcutBackend {
    // Sends one submission. Throws IOException when the network is down.
    suspend fun submit(submission: ShortcutSubmission)

    // Latest status for everything this submitter has sent.
    suspend fun statuses(submitterId: String): List<StatusUpdate>

    // Every approved shortcut for a building, from every student.
    suspend fun approved(buildingId: String): List<ShortcutSubmission>
}

data class StatusUpdate(val id: String, val status: ShortcutStatus, val reviewerNote: String?)

// In-memory stand-in for the real backend, used until the admin page and API exist.
// It starts with one approved shortcut from "another student" so the S1b tag can be seen.
// The debug overlay can approve or reject your pending submissions to test the whole loop.
class FakeShortcutBackend(private val isOnline: () -> Boolean) : ShortcutBackend {

    private val store = ConcurrentHashMap<String, ShortcutSubmission>()

    init {
        val seeded = ShortcutSubmission(
            id = "seed-sparks",
            submitterId = "another-student",
            name = "Sparks side cut through",
            fromNodeId = "cs_p1",
            toNodeId = "cs_walters",
            path = listOf(
                PathPoint(40.0, 90.0, 1, 0),
                PathPoint(20.0, 95.0, 1, 15_000),
                PathPoint(5.0, 85.0, 1, 26_000),
                PathPoint(10.0, 75.0, 1, 31_000),
            ),
            photos = emptyList(),
            note = "Through the Sparks Hall breezeway",
            status = ShortcutStatus.APPROVED,
            reviewerNote = null,
            createdAt = 0L,
            buildingId = "cs",
            routeLabel = "P1 Decatur St side to Walters side",
            uploaded = true,
        )
        store[seeded.id] = seeded
    }

    override suspend fun submit(submission: ShortcutSubmission) {
        delay(400) // Feels like a network call
        if (!isOnline()) throw IOException("Offline")
        store[submission.id] = submission.copy(status = ShortcutStatus.PENDING, uploaded = true)
    }

    override suspend fun statuses(submitterId: String): List<StatusUpdate> {
        if (!isOnline()) throw IOException("Offline")
        return store.values.filter { it.submitterId == submitterId }.map { StatusUpdate(it.id, it.status, it.reviewerNote) }
    }

    override suspend fun approved(buildingId: String): List<ShortcutSubmission> {
        if (!isOnline()) throw IOException("Offline")
        return store.values.filter { it.buildingId == buildingId && it.status == ShortcutStatus.APPROVED }
    }

    // Debug only: pretend a developer reviewed everything this submitter has pending.
    fun reviewAll(submitterId: String, approve: Boolean) {
        for ((id, s) in store) {
            if (s.submitterId == submitterId && s.status == ShortcutStatus.PENDING) {
                store[id] = if (approve) {
                    s.copy(status = ShortcutStatus.APPROVED)
                } else {
                    s.copy(status = ShortcutStatus.REJECTED, reviewerNote = "Photos too dark to check the route.")
                }
            }
        }
    }
}
