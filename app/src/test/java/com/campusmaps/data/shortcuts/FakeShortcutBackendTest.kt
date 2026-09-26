package com.campusmaps.data.shortcuts

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

// docs/22 #9 and #10: approvals survive a restart; no dead seeded shortcut.
class FakeShortcutBackendTest {

    private fun submission(id: String) = ShortcutSubmission(
        id = id, submitterId = "me", name = "Test cut", fromNodeId = "H4", toNodeId = "H5",
        path = listOf(PathPoint(0.0, 0.0, 2, 0), PathPoint(5.0, 0.0, 2, 4_000)), photos = emptyList(), note = null,
        status = ShortcutStatus.PENDING, reviewerNote = null, createdAt = 1L, buildingId = "CS", routeLabel = "H4 to H5",
    )

    @Test
    fun approvedShortcutSurvivesANewBackendInstance() = runTest {
        val file = File.createTempFile("queue", ".json").apply { delete(); deleteOnExit() }
        val first = FakeShortcutBackend { true }.apply { attachStore(file) }
        assertTrue(first.approved("CS").isEmpty()) // no seeded shortcut any more
        first.submit(submission("a"))
        first.submit(submission("b"))
        first.reviewAll("me", approve = true)

        // Process death: a fresh backend reading the same file.
        val second = FakeShortcutBackend { true }.apply { attachStore(file) }
        assertEquals(listOf("a", "b"), second.approved("CS").map { it.id }.sorted())
        assertEquals(ShortcutStatus.APPROVED, second.statuses("me").first().status)
    }

    @Test
    fun pendingStaysPendingAndABrokenFileIsIgnored() = runTest {
        val file = File.createTempFile("queue", ".json").apply { deleteOnExit() }
        file.writeText("not json")
        val backend = FakeShortcutBackend { true }.apply { attachStore(file) }
        backend.submit(submission("p"))
        val again = FakeShortcutBackend { true }.apply { attachStore(file) }
        assertEquals(ShortcutStatus.PENDING, again.statuses("me").single().status)
        assertTrue(again.approved("CS").isEmpty())
    }
}
