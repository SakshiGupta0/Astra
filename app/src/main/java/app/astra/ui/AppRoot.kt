package app.astra.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.astra.App
import app.astra.R
import app.astra.core.ThemeChoice
import app.astra.ui.chart.ChartScreen
import app.astra.ui.components.AIcon
import app.astra.ui.onboarding.OnboardingFlow
import app.astra.ui.profile.ProfileScreen
import app.astra.ui.theme.Astra
import app.astra.ui.theme.AstraTheme
import app.astra.ui.today.TodayScreen
import app.astra.ui.tracker.TrackerScreen

enum class Tab(val label: String, @DrawableRes val icon: Int) {
    TODAY("Today", R.drawable.ic_sun), TRACKER("Tracker", R.drawable.ic_orbit),
    CHART("Chart", R.drawable.ic_compass), PROFILE("Profile", R.drawable.ic_user_round)
}

@Composable
fun AppRoot(app: App) {
    val profile = app.profiles.profile
    AstraTheme(profile?.theme ?: ThemeChoice.SYSTEM) {
        var editing by remember { mutableStateOf(false) }
        when {
            profile == null -> OnboardingFlow(null, onDone = { app.profiles.save(it) })
            editing -> OnboardingFlow(profile, onDone = { if (app.profiles.save(it)) editing = false }, onCancel = { editing = false })
            else -> {
                // New session when the profile changes or the app comes back on a new day.
                var day by remember { mutableStateOf(java.time.LocalDate.now()) }
                androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                    val d = java.time.LocalDate.now(); if (d != day) day = d
                    onPauseOrDispose { }
                }
                val session = remember(profile, day) { Session(profile) }
                Main(app, session, onEdit = { editing = true })
            }
        }
    }
}

@Composable
private fun Main(app: App, session: Session, onEdit: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.TRACKER) }
    val c = Astra.colors
    Scaffold(
        containerColor = c.background,
        bottomBar = {
            NavigationBar(containerColor = c.backgroundAlt, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { AIcon(t.icon, null, tint = if (tab == t) c.gold else c.textSecondary) },
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
                Tab.TODAY -> TodayScreen(session)
                Tab.TRACKER -> TrackerScreen(session)
                Tab.CHART -> ChartScreen(session)
                Tab.PROFILE -> ProfileScreen(app, session.profile, onEdit)
            }
        }
    }
}
