package app.astra.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.astra.App
import app.astra.MainActivity
import app.astra.R
import app.astra.core.StoryRules
import app.astra.core.ThemeChoice
import app.astra.ui.ask.AskScreen
import app.astra.ui.chart.ChartScreen
import app.astra.ui.components.AIcon
import app.astra.ui.onboarding.OnboardingFlow
import app.astra.ui.profile.ProfileScreen
import app.astra.ui.theme.Astra
import app.astra.ui.theme.AstraTheme
import app.astra.ui.today.TodayScreen
import app.astra.ui.tracker.TrackerScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.astra.ui.onboarding.OnboardingClock
import app.astra.ui.ask.AskState
import app.astra.ui.tarot.TarotScreen

enum class Tab(val label: String, @DrawableRes val icon: Int) {
    TODAY("Today", R.drawable.ic_sun), TRACKER("Tracker", R.drawable.ic_orbit), TAROT("Tarot", R.drawable.ic_layers),
    ASK("Ask", R.drawable.ic_message_circle), ME("Me", R.drawable.ic_user_round)
}

@Composable
fun AppRoot(app: App, activity: MainActivity? = null) {
    val profile = app.profiles.profile
    AstraTheme(profile?.theme ?: ThemeChoice.DARK) {
        var editing by remember { mutableStateOf(false) }
        when {
            profile == null -> OnboardingFlow(null, onDone = { p, about ->
                if (app.profiles.save(p)) {
                    app.story.update { s ->
                        val shown = s.copy(offersShown = maxOf(s.offersShown, 1))
                        if (about != null) StoryRules.grant(shown.copy(about = about), "onboarding") else shown
                    }
                    app.analytics.log("onboarding_completed", "birth_time_status" to p.timeStatus.name.lowercase(), "story_opt_in" to (about != null),
                        "language" to p.language.name.lowercase(), "duration_s" to OnboardingClock.seconds())
                    if (about != null) app.analytics.log("story_item_added", "type" to "about", "source" to "user")
                }
            })
            editing -> OnboardingFlow(profile, onDone = { p, _ -> if (app.profiles.save(p)) editing = false }, onCancel = { editing = false })
            else -> {
                // New session when the profile changes or the app comes back on a new day.
                var day by remember { mutableStateOf(java.time.LocalDate.now()) }
                androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                    val d = java.time.LocalDate.now(); if (d != day) day = d
                    onPauseOrDispose { }
                }
                val session = remember(profile, day) { Session(profile) }
                // Once per session: start a due check-in (at most one a day) and send queued usage events.
                LaunchedEffect(session) {
                    app.rollCheckins(session.today)
                    app.analytics.log("app_open", "from_push" to (activity?.openedFromPush == true))
                    app.analytics.flush()
                }
                // Keep the server's copy of the chart facts current (once per day / profile change).
                LaunchedEffect(session, app.backend) {
                    if (app.backend.available || app.cloud.firebaseReady) runCatching {
                        app.backend.syncFacts(withContext(Dispatchers.Default) { session.factsJson() }, session.today.toString())
                        if (app.story.story.enabled) app.backend.syncStory(app.story.story)
                    }
                }
                Main(app, session, activity, onEdit = { editing = true })
            }
        }
    }
}

@Composable
private fun Main(app: App, session: Session, activity: MainActivity?, onEdit: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TRACKER) }
    val requested = activity?.openTab?.value
    LaunchedEffect(requested) {
        requested?.let { name -> Tab.entries.firstOrNull { it.name == name }?.let { tab = it }; activity?.openTab?.value = null }
    }
    LaunchedEffect(tab) { app.analytics.screen = tab.name.lowercase() }
    val c = Astra.colors
    Scaffold(
        containerColor = c.background,
        bottomBar = {
            NavigationBar(containerColor = c.backgroundAlt, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { AIcon(t.icon, null, tint = if (tab == t) c.accent else c.textSecondary) },
                        label = { Text(t.label, style = Astra.type.meta) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = c.gold.copy(alpha = 0.16f), selectedTextColor = c.text, unselectedTextColor = c.textSecondary),
                        modifier = Modifier.testTag("tab_${t.name}")
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().background(c.background).padding(pad)) {
            when (tab) {
                Tab.TODAY -> TodayScreen(app, session, onOpenTarot = { tab = Tab.TAROT }, onOpenTracker = { tab = Tab.TRACKER })
                Tab.TRACKER -> TrackerScreen(app, session, onAsk = { q -> AskState.draft = q; tab = Tab.ASK })
                Tab.TAROT -> TarotScreen(app, session)
                Tab.ASK -> AskScreen(app, session, onOpenStory = { tab = Tab.ME })
                Tab.ME -> ProfileScreen(app, session, onEdit)
            }
        }
    }
}
