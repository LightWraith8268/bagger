package com.inknironapps.bagger.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class BaggerPrefsTest {
    @Before fun clearPrefs() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            // Reset the singleton DataStore to a clean slate between tests.
            dataStoreFor(context).edit { it.clear() }
        }
    }

    @Test fun setAndReadThemeMode() = runTest {
        val prefs = BaggerPrefs(ApplicationProvider.getApplicationContext())
        prefs.setThemeMode("dark")
        prefs.themeMode.test {
            assertEquals("dark", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun defaultThemeIsSystem() = runTest {
        val prefs = BaggerPrefs(ApplicationProvider.getApplicationContext())
        prefs.themeMode.test {
            assertEquals("system", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
