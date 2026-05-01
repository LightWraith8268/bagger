package com.inknironapps.bagger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.inknironapps.bagger.data.changelog.ChangelogParser
import com.inknironapps.bagger.data.prefs.BaggerPrefs
import com.inknironapps.bagger.ui.nav.BaggerBottomBar
import com.inknironapps.bagger.ui.nav.BaggerNavHost
import com.inknironapps.bagger.ui.nav.DetailRoutes
import com.inknironapps.bagger.ui.screens.onboarding.OnboardingScreen
import com.inknironapps.bagger.ui.theme.BaggerTheme
import com.inknironapps.bagger.ui.whatsnew.WhatsNewDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefs: BaggerPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by prefs.themeMode.collectAsState(initial = "system")
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            BaggerTheme(darkTheme = isDark) {
                val onboarded by prefs.onboardingComplete.collectAsState(initial = true)
                val nav = rememberNavController()
                val scope = rememberCoroutineScope()

                if (!onboarded) {
                    OnboardingScreen(onGetStarted = {
                        scope.launch { prefs.setOnboardingComplete(true) }
                    })
                } else {
                    Scaffold(bottomBar = { BaggerBottomBar(nav) }) { padding ->
                        Box(Modifier.padding(padding)) {
                            BaggerNavHost(nav)
                        }
                    }

                    val context = LocalContext.current
                    val lastSeen by prefs.lastSeenChangelogVersion.collectAsState(initial = null)
                    var entries by remember { mutableStateOf<List<ChangelogParser.Entry>>(emptyList()) }
                    LaunchedEffect(lastSeen) {
                        val current = BuildConfig.VERSION_NAME.substringBefore("-")
                        if (lastSeen != BuildConfig.VERSION_NAME) {
                            try {
                                val text = context.assets.open("CHANGELOG.md")
                                    .bufferedReader().use { it.readText() }
                                val parsed = ChangelogParser.parse(text)
                                val between = ChangelogParser.entriesBetween(parsed, lastSeen, current)
                                if (between.isNotEmpty() && lastSeen != null) {
                                    entries = between
                                }
                            } catch (_: Exception) {
                                // No changelog asset bundled or parse error — silently skip.
                            }
                            prefs.setLastSeenChangelogVersion(BuildConfig.VERSION_NAME)
                        }
                    }
                    if (entries.isNotEmpty()) {
                        WhatsNewDialog(entries, onDismiss = { entries = emptyList() })
                    }
                }
            }
        }
    }
}
