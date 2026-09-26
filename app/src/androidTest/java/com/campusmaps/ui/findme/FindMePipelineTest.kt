package com.campusmaps.ui.findme

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.campusmaps.CampusMapsApplication
import com.campusmaps.glasses.SignReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

// Runs the Find me pipeline (ML Kit OCR via SignReader, then FindMeVoter) on the app's own sign photos,
// the way the sheet does with camera frames: the same photo as frame after frame until a match.
@RunWith(AndroidJUnit4::class)
class FindMePipelineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (context.applicationContext as CampusMapsApplication).container

    private fun firstMatch(buildingId: String, asset: String): FindMeMatch? = runBlocking {
        val bitmap = context.assets.open(asset).use { BitmapFactory.decodeStream(it) }
        val voter = FindMeVoter.forBuilding(container.building(buildingId))
        val reader = SignReader()
        try {
            repeat(3) { i ->
                val text = FindMeOcr.read(reader, bitmap)
                val m = voter.onFrame(text)
                Log.i("FindMeTest", "$asset frame ${i + 1}: '${text.replace('\n', '|')}' -> ${m?.nodeId}")
                if (m != null) return@runBlocking m
            }
            null
        } finally {
            reader.close()
        }
    }

    @Test fun researchWingSignVotesTheResearchWingDoor() {
        val m = firstMatch("KL", "anchors/KL/KL-A01.jpg")
        assertNotNull(m)
        assertEquals("E-RWD", m!!.nodeId)
    }

    @Test fun plaque1116WMatchesTheRoom() {
        val m = firstMatch("KL", "anchors/KL/KL-A03.jpg")
        assertNotNull(m)
        assertEquals("R-1116W", m!!.nodeId)
    }

    // Debug replay photos of the CS survey (debug source set).
    @Test fun librarySouthVotesTheLibraryEntrance() {
        val m = firstMatch("CS", "glasses-replay/CS/CS-A05-straight.jpg")
        assertNotNull(m)
        assertEquals("E-LM2", m!!.nodeId)
    }

    @Test fun room608PhotoMatchesRoom608() {
        val m = firstMatch("CS", "glasses-replay/CS/CS-A07-straight.jpg")
        assertNotNull(m)
        assertEquals("R-608", m!!.nodeId)
    }
}
