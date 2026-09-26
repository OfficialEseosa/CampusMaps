package com.campusmaps.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignVoterTest {
    // CS anchors as in assets/buildings/CS.json.
    private val cs = listOf(
        AnchorText("CS-A02", "E-95DS", 1, "95 DECATUR ST", listOf("95 DECATUR ST.", "DECATUR ST", "ECATURST")),
        AnchorText("CS-A03", "R-150", 1, "150", listOf("AUDITORIUM 150", "I50", "15O")),
        AnchorText("CS-A04", "H2", 1, "STAIRS", listOf("STAIRS ->")),
        AnchorText("CS-A05", "E-LM2", 2, "LIBRARY SOUTH", listOf("LIBRARY ENTRANCE")),
        AnchorText("CS-A06", "H3", 1, "LIBRARY SOUTH", listOf("LIBRARY SOUTH FIREALARM")),
        AnchorText("CS-A07", "R-608", 6, "608", listOf("6O8", "60B")),
    )

    @Test fun roomNumberExactWins() {
        val v = SignVoter.vote(listOf("608\nGSU", "ROOM 608", "EXIT"), cs)
        assertNotNull(v)
        assertEquals("R-608", v!!.nodeId)
        assertEquals(2, v.votes)
    }

    @Test fun ocrConfusionIsFixed() {
        assertEquals("608", SignVoter.fixDigits("6O8"))
        assertEquals("150", SignVoter.fixDigits("I50"))
        assertEquals("STAIRS", SignVoter.fixDigits("STAIRS"))
        assertEquals("R-608", SignVoter.vote(listOf("6O8", "608"), cs)!!.nodeId)
    }

    @Test fun roomNumberNeedsWholeToken() {
        assertEquals(0.0, SignVoter.scorePhrase("608", "16080"), 0.0)
        assertEquals(0.0, SignVoter.scorePhrase("150", "1500 SQ FT"), 0.0)
    }

    @Test fun oneStillOfThreeIsNotAMajority() {
        assertNull(SignVoter.vote(listOf("608", "HELLO", "WORLD"), cs))
    }

    @Test fun nearMissAloneCannotWin() {
        // "609" is one edit from 608: it scores 0.65, below the 0.7 confidence floor.
        assertNull(SignVoter.vote(listOf("609", "609"), cs))
    }

    @Test fun fuzzyWordsMatch() {
        assertTrue(SignVoter.scorePhrase("LIBRARY SOUTH", "L1BRARY SOUTH") >= 0.9)
        assertEquals("H2", SignVoter.vote(listOf("NOTICE STAIRS ->", "STAIRS", "STAIR5"), cs)!!.nodeId)
    }

    @Test fun sharedTextPrefersNodeOnRoute() {
        val stills = listOf("LIBRARY SOUTH", "LIBRARY SOUTH FIRE ALARM", "LIBRARY")
        assertEquals("H3", SignVoter.vote(stills, cs, preferNodes = setOf("H3"))!!.nodeId)
        assertEquals("E-LM2", SignVoter.vote(stills, cs, preferNodes = setOf("E-LM2"))!!.nodeId)
    }

    @Test fun emptyBurstVotesNothing() {
        assertNull(SignVoter.vote(emptyList(), cs))
        assertNull(SignVoter.vote(listOf("", ""), cs))
    }
}
