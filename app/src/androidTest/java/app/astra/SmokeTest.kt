// Emulator smoke tests: onboarding → tracker → chart → today → profile, birth-time states, persistence.
// Screenshots go to the app's external files dir (/sdcard/Android/data/app.astra/files/shots) for review.
package app.astra

import android.graphics.Bitmap
import app.astra.core.NotifyKind
import app.astra.core.StoryRules
import app.astra.core.ThreadStatus
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
        rule.runOnUiThread {
            app.deleteEverything(); app.backend = NoBackend; AskState.resetForTests()
            app.astra.ui.tarot.TarotState.resetForTests(); app.analytics.setEnabled(false)
        }
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
        rule.onNodeWithText("Chart").performClick()
        waitTag("lagna").assertIsDisplayed()
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
        rule.onNodeWithText("Chart").performClick()
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
        rule.onNodeWithTag("saveAnswer").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue("saved: ${app.readings.readings.size}", app.readings.readings.size == 1)
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
        rule.onNodeWithTag("saveAnswer").performScrollTo().performClick()
        waitTag("storyOffer")
        shot("50_story_offer_after_save")
        rule.onNodeWithTag("storyOfferYes").performScrollTo().performClick()
        waitTag("storySwitch")
        rule.waitForIdle()
        assertTrue(app.story.story.enabled && app.story.story.consent!!.offerPoint == "second")
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

    @Test fun i_tarotReadingAndCrossReading() {
        val fake = FakeBackend()
        rule.runOnUiThread { app.backend = fake }
        onboard("I know the exact time")
        waitTag("whereYouAre")
        rule.onNodeWithTag("tab_TAROT").performClick()
        waitTag("tarotIntro")
        rule.onNodeWithTag("tarotQuestion").performTextInput("What do I need to see about the job offer?")
        shot("80_tarot_new")
        rule.onNodeWithTag("drawCards").performClick()
        waitTag("card0")
        shot("81_tarot_face_down")
        rule.onNodeWithTag("card0").performClick()
        rule.mainClock.advanceTimeBy(800)
        shot("82_tarot_one_revealed")
        rule.onNodeWithTag("revealAll").performClick()
        waitTag("cardMeanings")
        shot("83_tarot_meanings")
        val drawn = app.astra.ui.tarot.TarotState.draw!!
        assertTrue(drawn.cards.size == 3 && drawn.cards.map { it.cardId }.toSet().size == 3)
        rule.onNodeWithTag("readCards").performScrollTo().performClick()
        waitTag("tarotAnswer", 30_000)
        // The draw reached the server before it was read, and the reading is of that same draw.
        assertTrue(fake.draws.single().id == drawn.id && fake.readings.first() == ("tarot" to drawn.id))
        rule.onNodeWithTag("tarotAnswer").performScrollTo()
        shot("84_tarot_reading")
        rule.onNodeWithTag("crossReading").performScrollTo().performClick()
        waitTag("crossAnswer", 30_000)
        rule.onNodeWithText("Where they differ").performScrollTo().assertIsDisplayed()
        shot("85_cross_reading")
        rule.onNodeWithTag("rate5").performScrollTo().performClick()
        rule.onNodeWithTag("saveCross").performScrollTo().performClick()
        rule.waitForIdle()
        val saved = app.readings.readings.first()
        assertTrue(saved.type == "cross" && saved.draw == drawn && saved.rating == 5)
        rule.onNodeWithTag("tarotLibrary").performClick()
        waitTag("libraryList")
        rule.onNodeWithText("Cross-Reading").assertIsDisplayed()
        shot("86_library_with_cards")
    }

    @Test fun j_checkinThenLifeEventOnTracker() {
        onboard("I know the exact time", story = true)
        waitTag("whereYouAre")
        rule.runOnUiThread {
            app.story.update { StoryRules.addThread(it, "Deciding on a job offer", today = java.time.LocalDate.now().minusDays(22)) }
            app.rollCheckins()
        }
        rule.onNodeWithTag("tab_TODAY").performClick()
        waitTag("checkin")
        shot("90_checkin")
        rule.onNodeWithTag("checkinText").performTextInput("Took the new job")
        rule.onNodeWithTag("checkinResolved").performClick()
        waitTag("checkinAddEvent").performClick()
        rule.waitForIdle()
        val st = app.story.story
        assertTrue(st.threads.single().status == ThreadStatus.RESOLVED && st.threads.single().outcome == "Took the new job")
        assertTrue(st.events.single().title == "Took the new job")
        shot("91_checkin_done")
        waitTag("checkinSeeTracker").performClick()
        rule.onNodeWithText("Timeline").performClick()
        waitTag("timelineBands", 40_000)
        waitTag("lifeEvent").performScrollTo().performClick()
        waitTag("askAboutThis").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("What your chart showed then:").assertIsDisplayed()
        shot("92_life_event_on_tracker")
    }

    @Test fun k_dailyCardAndPrivacy() {
        onboard("I know the exact time")
        waitTag("whereYouAre")
        rule.onNodeWithTag("tab_TODAY").performClick()
        waitTag("dailyCard").performScrollTo()
        shot("100_daily_card_down")
        rule.onNodeWithTag("dailyCardFace").performClick()
        waitTag("askDailyCard").assertIsDisplayed()
        shot("101_daily_card_up")
        val card = app.draws.daily!!
        assertTrue(card.day == java.time.LocalDate.now() && card.cards.size == 1)
        rule.onNodeWithTag("askDailyCard").performClick()
        waitTag("cardMeanings")
        assertTrue(app.astra.ui.tarot.TarotState.draw == card)
        rule.onNodeWithTag("tab_ME").performClick()
        rule.onNodeWithTag("privacyCard").performScrollTo()
        shot("102_me_privacy")
        rule.onNodeWithTag("analyticsSwitch").performClick(); rule.waitForIdle()
        assertTrue(app.analytics.enabled)
        rule.onNodeWithTag("analyticsSwitch").performClick(); rule.waitForIdle()
        assertTrue(!app.analytics.enabled)
        rule.onNodeWithTag("openPrivacy").performScrollTo().performClick()
        waitTag("privacyNotice")
        rule.onNodeWithText("Your choices and rights").performScrollTo().assertIsDisplayed()
        shot("103_privacy_notice")
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.onNodeWithTag("deleteAccount").performScrollTo().performClick()
        rule.onNodeWithText("Delete your account?").assertIsDisplayed()
        shot("104_delete_account_confirm")
        rule.onNodeWithText("Cancel").performClick()
    }
}
