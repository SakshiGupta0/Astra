// Emulator smoke tests: onboarding → tracker → chart → today → profile, birth-time states, persistence.
// Screenshots go to the app's external files dir (/sdcard/Android/data/app.astra/files/shots) for review.
package app.astra

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as App

    @Before fun reset() {
        rule.runOnUiThread { app.profiles.clear() }
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val dir = File(app.getExternalFilesDir(null), "shots").apply { mkdirs() }
        val bmp = rule.onNode(isRoot()).captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun waitTag(tag: String, ms: Long = 20_000): SemanticsNodeInteraction {
        rule.waitUntil(ms) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        return rule.onAllNodesWithTag(tag).onFirst()
    }

    private fun next() = rule.onNodeWithTag("continue").performClick()

    private fun onboard(timeChoice: String, hour: String? = "11", minute: String? = "05", pm: Boolean = false, shots: Boolean = false) {
        if (shots) shot("01_welcome")
        rule.onNodeWithTag("getStarted").performClick()
        rule.onNodeWithTag("name").performTextInput("Priya"); if (shots) shot("02_name"); next()
        rule.onNodeWithTag("day").performTextInput("14"); rule.onNodeWithTag("month").performTextInput("2"); rule.onNodeWithTag("year").performTextInput("1995")
        if (shots) shot("03_date"); next()
        rule.onNodeWithText(timeChoice).performClick()
        if (hour != null) { rule.onNodeWithTag("hour").performTextInput(hour); rule.onNodeWithTag("minute").performTextInput(minute!!) }
        if (pm) rule.onNodeWithText("PM").performClick()
        if (shots) shot("04_time"); next()
        rule.onNodeWithTag("placeQuery").performTextInput("bangalore")
        rule.waitUntil(20_000) { rule.onAllNodes(hasText("Bengaluru, Karnataka, India")).fetchSemanticsNodes().isNotEmpty() }
        if (shots) shot("05_place_search")
        rule.onAllNodes(hasText("Bengaluru, Karnataka, India")).onFirst().performClick()
        waitTag("selectedPlace").assertIsDisplayed()
        if (shots) shot("06_place_selected"); next()
        rule.onNodeWithText("Same as my birthplace").performClick(); next()
        rule.onNodeWithText("Hinglish").performClick(); if (shots) shot("07_language"); next()
    }

    @Test fun a_fullJourneyKnownTime() {
        onboard("I know the exact time", shots = true)
        waitTag("whereYouAre").assertIsDisplayed()
        waitTag("mahadasha").assertIsDisplayed()
        waitTag("tone", 40_000)
        shot("10_tracker_now")
        rule.onNodeWithTag("planet_SATURN").performScrollTo().performClick()
        shot("11_tracker_saturn_open")
        rule.onNodeWithText("Timeline").performClick()
        waitTag("timelineBands", 40_000)
        shot("12_tracker_timeline")
        rule.onNodeWithTag("tab_CHART").performClick()
        rule.onNodeWithTag("lagna").assertIsDisplayed()
        rule.onNodeWithText("Birth chart (Rasi)").assertIsDisplayed()
        shot("13_chart")
        rule.onNodeWithText("How this was calculated").performScrollTo()
        shot("14_chart_method")
        rule.onNodeWithTag("tab_TODAY").performClick()
        waitTag("greeting").assertIsDisplayed()
        waitTag("panchang", 40_000)
        shot("15_today")
        rule.onNodeWithTag("tab_PROFILE").performClick()
        rule.onNodeWithText(BuildConfig.VERSION_NAME).performScrollTo().assertIsDisplayed()
        shot("16_profile")
        assertNotNull(app.profiles.profile)
    }

    @Test fun b_unknownTimeHidesLagna() {
        onboard("I don't know", hour = null, minute = null)
        waitTag("whereYouAre")
        rule.onNodeWithTag("tab_CHART").performClick()
        rule.onNodeWithText("Moon chart (Chandra lagna)").assertIsDisplayed()
        rule.onNodeWithText("Needs birth time").assertIsDisplayed()
        shot("20_chart_unknown_time")
        assertNull(app.profiles.profile!!.birthTime)
    }

    @Test fun c_profileSurvivesRestart() {
        onboard("I know roughly", hour = "6", minute = "30", pm = true)
        waitTag("whereYouAre")
        rule.activityRule.scenario.recreate()
        waitTag("whereYouAre").assertIsDisplayed()
        rule.onNodeWithTag("tab_PROFILE").performClick()
        rule.onNodeWithText("around 6:30 pm (±60 min)").assertIsDisplayed()
    }

    @Test fun d_lightTheme() {
        onboard("I know the exact time")
        waitTag("whereYouAre")
        rule.onNodeWithTag("tab_PROFILE").performClick()
        rule.onNodeWithText("Light").performClick()
        rule.onNodeWithTag("tab_TRACKER").performClick()
        waitTag("tone", 40_000)
        shot("30_tracker_light")
        rule.onNodeWithTag("tab_TODAY").performClick()
        waitTag("panchang", 40_000)
        shot("31_today_light")
    }
}
