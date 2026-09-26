package com.campusmaps.ui.findme

import android.Manifest
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.campusmaps.CampusMapsApplication
import com.campusmaps.MainActivity
import com.campusmaps.ui.MainViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// Drives S1 -> Find me on the emulator and saves screenshots of the sheet states to
// /sdcard/Android/data/com.campusmaps/files/findme-shots. Set up from outside (adb) before each method:
//   matchFlow:     setprop debug.campusmaps.findme anchors/KL/KL-A03.jpg, camera granted
//   noMatchFlow:   setprop debug.campusmaps.findme anchors/CS/CS-A01.jpg, camera granted
//   noPermission:  camera revoked and user-fixed (the request is denied without a dialog)
@RunWith(AndroidJUnit4::class)
class FindMeSheetShotsTest {
    @get:Rule(order = 0)
    val location: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS,
    )

    @get:Rule(order = 1)
    val rule = createAndroidComposeRule<MainActivity>()

    private val container get() = (rule.activity.application as CampusMapsApplication).container

    private fun openS1(building: String) {
        runBlocking { container.settings.setDemoMode(false); container.settings.setBuilding(building) }
        val vm = ViewModelProvider(rule.activity, MainViewModel.Factory(container))[MainViewModel::class.java]
        Thread.sleep(800)
        rule.runOnUiThread { vm.openBuilding(building) }
        waitText("Where to?")
        Thread.sleep(800)
        rule.onNodeWithTag("findMeButton").performScrollTo()
        rule.waitForIdle()
    }

    // The test clock skips delays when it auto-advances, so the 1.5 s auto-accept would fire at once. Once the sheet
    // is open the clock is driven by hand at real speed, so the screenshots see each state for as long as a person would.
    private fun realWait(ms: Long, cond: () -> Boolean) {
        if (rule.mainClock.autoAdvance) return rule.waitUntil(ms, cond)
        val end = System.currentTimeMillis() + ms
        while (!cond()) {
            check(System.currentTimeMillis() < end) { "timed out after $ms ms" }
            rule.mainClock.advanceTimeBy(50)
            Thread.sleep(50)
        }
    }

    private fun waitText(t: String, ms: Long = 10_000) =
        realWait(ms) { rule.onAllNodes(hasText(t, substring = true)).fetchSemanticsNodes().isNotEmpty() }

    private fun waitTag(t: String, ms: Long = 10_000) =
        realWait(ms) { rule.onAllNodes(hasTestTag(t)).fetchSemanticsNodes().isNotEmpty() }

    private fun shot(name: String) {
        rule.mainClock.advanceTimeByFrame()
        Thread.sleep(300)
        val bmp = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val dir = File(rule.activity.getExternalFilesDir(null), "findme-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("FindMeShots", "saved $name")
    }

    @Test fun matchFlow() {
        openS1("KL")
        shot("1-s1-find-me-button")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("findMeButton").performClick()
        waitTag("findMeSheet")
        shot("2-sheet-hint")
        waitTag("findMeMatch", 20_000)
        shot("3-sheet-match")
        waitText("You are at", 10_000)
        shot("4-s1-you-are-at")
        waitText("Inside: Room 1116W")
    }

    @Test fun noMatchFlow() {
        openS1("KL")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("findMeButton").performClick()
        waitText("Seen:", 20_000)
        waitTag("findMeNoMatch", 30_000)
        shot("5-sheet-no-match")
        assertEquals(0, rule.onAllNodes(hasTestTag("findMeMatch")).fetchSemanticsNodes().size)
    }

    @Test fun noPermission() {
        openS1("KL")
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("findMeButton").performClick()
        waitTag("findMeCameraNeeded")
        Thread.sleep(1500)
        shot("6-sheet-camera-needed")
    }
}
