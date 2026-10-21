// Emulator smoke tests: onboarding → tracker → chart → today → profile, birth-time states, persistence.
// Screenshots go to the app's external files dir (/sdcard/Android/data/app.astra/files/shots) for review.
package app.astra

import android.graphics.Bitmap
import app.astra.core.NotifyKind
import app.astra.system.Notifications
import app.astra.ui.ask.AskState
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
import org.junit.Assert.assertTrue
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
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    /** On any failure, save a screenshot named after the test so the problem can be seen. */
    @get:Rule(order = 2) val onFail = object : org.junit.rules.TestWatcher() {
        override fun failed(e: Throwable?, d: org.junit.runner.Description) { runCatching { shot("FAIL_${d.methodName}") } }
    }

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as App

    @Before fun reset() {
        rule.runOnUiThread { app.deleteEverything(); app.backend = NoBackend; AskState.resetForTests() }
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

    private fun onboard(timeChoice: String, hour: String? = "11", minute: String? = "05", pm: Boolean = false, shots: Boolean = false, story: Boolean = false) {
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
        rule.waitUntil(60_000) { rule.onAllNodes(hasText("Bengaluru, Karnataka, India")).fetchSemanticsNodes().isNotEmpty() }
        if (shots) shot("05_place_search")
        rule.onAllNodes(hasText("Bengaluru, Karnataka, India")).onFirst().performClick()
        waitTag("selectedPlace").assertIsDisplayed()
        if (shots) shot("06_place_selected"); next()
        rule.onNodeWithText("Same as my birthplace").performClick(); next()
        if (story) {
            if (shots) shot("07a_story_offer")
            rule.onNodeWithTag("onboardingStorySwitch").performClick()
            rule.onNodeWithText("Working professional").performClick()
            rule.onNodeWithText("Career").performClick()
            rule.onNodeWithTag("onboardingNow").performScrollTo().performTextInput("Deciding between two job offers")
            if (shots) shot("07b_story_filled")
            next()
        } else rule.onNodeWithTag("skipStory").performScrollTo().performClick()
        rule.onNodeWithText("Hinglish").performClick(); if (shots) shot("07c_language"); next()
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
        rule.onNodeWithTag("tab_ME").performClick()
        shot("16_me_top")
        rule.onNodeWithText(BuildConfig.VERSION_NAME).performScrollTo().assertIsDisplayed()
        shot("17_me_bottom")
        assertNotNull(app.profiles.profile)
    }

    @Test fun b_unknownTimeHidesLagna() {
        onboard("I don't know", hour = null, minute = null)
        waitTag("whereYouAre")
        rule.onNodeWithTag("tab_CHART").performClick()
        rule.onNodeWithText("Moon chart (Chandra lagna)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Needs birth time").performScrollTo().assertIsDisplayed()
        shot("20_chart_unknown_time")
        assertNull(app.profiles.profile!!.birthTime)
    }

    @Test fun c_profileSurvivesRestart() {
        onboard("I know roughly", hour = "6", minute = "30", pm = true)
        waitTag("whereYouAre")
        rule.activityRule.scenario.recreate()
        waitTag("whereYouAre").assertIsDisplayed()
        rule.onNodeWithTag("tab_ME").performClick()
        rule.onNodeWithText("around 6:30 pm (±60 min)").performScrollTo().assertIsDisplayed()
    }

    @Test fun d_lightTheme() {
        onboard("I know the exact time")
        waitTag("whereYouAre")
        rule.onNodeWithTag("tab_ME").performClick()
        rule.onNodeWithText("Light").performScrollTo().performClick()
        rule.onNodeWithTag("tab_TRACKER").performClick()
        waitTag("tone", 40_000)
        shot("30_tracker_light")
        rule.onNodeWithTag("tab_TODAY").performClick()
        waitTag("panchang", 40_000)
        shot("31_today_light")
    }

    @Test fun e_askWithStory() {
        val fake = FakeBackend()
        rule.runOnUiThread { app.backend = fake }
        onboard("I know the exact time", story = true)
        waitTag("whereYouAre")
        assertTrue(app.story.story.enabled && app.story.story.about.nowLine.contains("job offers"))
        rule.onNodeWithTag("tab_ASK").performClick()
        waitTag("suggestion0")
        shot("40_ask_empty")
        rule.onNodeWithTag("askInput").performTextInput("Is this a supportive time for my job switch?")
        rule.onNodeWithTag("askSend").performClick()
        waitTag("answer", 30_000)
        assertTrue(fake.factsSyncs >= 1 && fake.lastFactsJson!!.contains("\"dasha_now\""))
        assertTrue(fake.questions.single().second.contains("about.now"))
        waitTag("fromStory").performClick()
        shot("41_ask_answer_story")
        rule.onNodeWithTag("saveAnswer").performClick()
        rule.waitForIdle()
        assertTrue(app.readings.readings.size == 1)
        assertTrue(app.story.story.digests.first().text.startsWith("Asked:"))
        rule.onNodeWithTag("openLibrary").performClick()
        rule.onNodeWithText("Saved readings").assertIsDisplayed()
        shot("42_library")
        rule.onNodeWithTag("tab_TODAY").performClick()
        waitTag("todayLine", 30_000)
        shot("43_today_ai_line")
    }

    @Test fun f_storyOfferAfterFirstSave() {
        val fake = FakeBackend()
        rule.runOnUiThread { app.backend = fake }
        onboard("I know the exact time", story = false)
        waitTag("whereYouAre")
        rule.onNodeWithTag("tab_ASK").performClick()
        waitTag("suggestion1").performClick()
        waitTag("answer", 30_000)
        assertTrue(fake.questions.single().second.isEmpty())
        rule.onNodeWithTag("saveAnswer").performClick()
        waitTag("storyOffer")
        shot("50_story_offer_after_save")
        rule.onNodeWithText("Set up My Story").performScrollTo().performClick()
        waitTag("storySwitch").performClick()
        rule.waitForIdle()
        assertTrue(app.story.story.enabled && app.story.story.consent!!.offerPoint == "settings")
        rule.onNodeWithTag("threadTitle").performScrollTo().performTextInput("Parents want me to meet a match")
        rule.onNodeWithTag("addThread").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(app.story.story.threads.single().title == "Parents want me to meet a match")
        rule.onNodeWithTag("storyCard").performScrollTo()
        shot("51_me_story_on")
        rule.onNodeWithTag("storySwitch").performClick()
        rule.waitForIdle()
        assertTrue(!app.story.story.enabled && app.story.story.threads.size == 1)
    }

    @Test fun g_notifications() {
        onboard("I know the exact time")
        waitTag("notifyOffer", 40_000)
        shot("60_notify_offer")
        rule.onNodeWithTag("notifyYes").performClick()
        rule.waitForIdle()
        assertTrue(app.notify.enabled == true)
        val posted = Notifications.runDaily(app)
        assertTrue("no daily note: $posted", posted.any { it.kind != NotifyKind.EVENT })
        assertTrue("daily note repeated", Notifications.runDaily(app).none { it.kind != NotifyKind.EVENT })
        rule.onNodeWithTag("tab_ME").performClick()
        rule.onNodeWithTag("notifyCard").performScrollTo()
        shot("61_me_notifications")
    }

    @Test fun h_askNotSetUp() {
        onboard("I know the exact time")
        waitTag("whereYouAre")
        rule.onNodeWithTag("tab_ASK").performClick()
        rule.onNodeWithText("Ask ASTRA is still being set up", substring = true).assertIsDisplayed()
        shot("70_ask_not_set_up")
    }
}
