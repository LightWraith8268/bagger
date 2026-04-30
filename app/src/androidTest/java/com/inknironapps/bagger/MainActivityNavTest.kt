package com.inknironapps.bagger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test

@HiltAndroidTest
class MainActivityNavTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun shelfTabIsDefault() {
        compose.onNodeWithText("Shelf — Plan 3").assertIsDisplayed()
    }

    @Test fun tappingBagsShowsBagsScreen() {
        compose.onNodeWithText("Bags").performClick()
        compose.onNodeWithText("Bags — Plan 3").assertIsDisplayed()
    }
}
