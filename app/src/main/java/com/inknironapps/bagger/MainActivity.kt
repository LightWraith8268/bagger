package com.inknironapps.bagger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.inknironapps.bagger.data.prefs.BaggerPrefs
import com.inknironapps.bagger.ui.nav.BaggerBottomBar
import com.inknironapps.bagger.ui.nav.BaggerNavHost
import com.inknironapps.bagger.ui.theme.BaggerTheme
import dagger.hilt.android.AndroidEntryPoint
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
                val nav = rememberNavController()
                Scaffold(bottomBar = { BaggerBottomBar(nav) }) { padding ->
                    Box(Modifier.padding(padding)) {
                        BaggerNavHost(nav)
                    }
                }
            }
        }
    }
}
