package com.campusmaps.ui.findme

import com.campusmaps.glasses.AnchorText
import com.campusmaps.glasses.SignVoter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FindMeVoterTest {
    // KL anchors and rooms as in assets/buildings/KL.json.
    private val klAnchors = listOf(
        AnchorText("KL-A01", "E-RWD", 1, "RESEARCH WING", listOf("KLAUS ADVANCED COMPUTING BUILDING", "RESE ARCH WING")),
        AnchorText("KL-A02", "R-3361", 3, "COEUS", listOf("3361", "COEUS LAB", "C0EUS")),
        AnchorText("KL-A03", "R-1116W", 1, "1116W", listOf("SEMINAR ROOM WEST", "1116 W", "III6W")),
    )
    private val klRooms = FindMeVoter.roomNumbers(listOf("R-1116W" to "Room 1116W", "R-3361" to "COEUS lab, room 3361"))

    // CS: rooms 150 and 608, and an anchor-less room 999 to show rooms match without an anchor.
    private val csAnchors = listOf(
        AnchorText("CS-A05", "E-LM2", 2, "LIBRARY SOUTH", listOf("LIBRARY ENTRANCE")),
        AnchorText("CS-A06", "H3", 1, "LIBRARY SOUTH", listOf("LIBRARY SOUTH FIREALARM")),
    )
    private val csRooms = FindMeVoter.roomNumbers(listOf("R-150" to "Room 150", "R-608" to "Room 608", "R-999" to "Room 999"))

    @Test fun roomNumbersFromIdsAndNames() {
        assertEquals(mapOf("1116W" to "R-1116W", "3361" to "R-3361"), klRooms)
        assertEquals("R-608", csRooms["608"])
    }

    // Room-number rule: one frame with an exact room number is enough, even without an anchor for that room.
    @Test fun oneFrameWithRoomNumberMatches() {
        val v = FindMeVoter(csAnchors, csRooms)
        val m = v.onFrame("ROOM\n999")
        assertNotNull(m)
        assertEquals("R-999", m!!.nodeId)
        assertTrue(m.byRoomNumber)
    }

    @Test fun roomNumberWithLetter() {
        assertEquals("R-1116W", SignVoter.matchRoomNumber("1116W\nSEMINAR ROOM WEST", klRooms))
        assertEquals("R-1116W", SignVoter.matchRoomNumber("1116 W", klRooms))
        assertEquals("R-608", SignVoter.matchRoomNumber("6O8", csRooms)) // OCR slip O -> 0
    }

    @Test fun roomNumberMustBeWholeToken() {
        assertNull(SignVoter.matchRoomNumber("16080 SQ FT", csRooms))
        assertNull(SignVoter.matchRoomNumber("1500", csRooms))
        assertNull(SignVoter.matchRoomNumber("1116", klRooms)) // 1116W is a different room
        assertNull(SignVoter.matchRoomNumber("FLOOR 1", mapOf("1" to "H9")))
    }

    // 2 of 3 rule: a sign (not a room number) needs 2 of the last 3 frames.
    @Test fun signNeedsTwoOfThreeFrames() {
        val v = FindMeVoter(klAnchors, klRooms)
        assertNull(v.onFrame("KLAUS ADVANCED COMPUTING BUILDING\nRESEARCH WING"))
        val m = v.onFrame("RESEARCH WING")
        assertNotNull(m)
        assertEquals("E-RWD", m!!.nodeId)
    }

    @Test fun signWithAGapStillCountsInTheWindow() {
        val v = FindMeVoter(klAnchors, klRooms)
        assertNull(v.onFrame("RESEARCH WING"))
        assertNull(v.onFrame(""))
        assertEquals("E-RWD", v.onFrame("RESE ARCH WING")?.nodeId)
    }

    @Test fun signFramesTooFarApartDoNotMatch() {
        val v = FindMeVoter(klAnchors, klRooms)
        assertNull(v.onFrame("RESEARCH WING"))
        assertNull(v.onFrame("TOBACCO FREE"))
        assertNull(v.onFrame("EXIT"))
        assertNull(v.onFrame("RESEARCH WING")) // The first one fell out of the window
    }

    @Test fun libraryVotesTheLibraryEntrance() {
        val v = FindMeVoter(csAnchors, csRooms)
        assertNull(v.onFrame("LIBRARY SOUTH"))
        assertEquals("E-LM2", v.onFrame("LIBRARY SOUTH")?.nodeId)
    }

    @Test fun unknownTextNeverMatches() {
        val v = FindMeVoter(klAnchors, klRooms)
        repeat(5) { assertNull(v.onFrame("CLASSROOM SOUTH")) }
    }
}
