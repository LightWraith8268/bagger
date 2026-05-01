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
class ShelfNavTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Test fun emptyShelfShowsCallToAction() {
        compose.onNodeWithText("Your shelf is empty").assertIsDisplayed()
    }

    @Test fun discoverTabShowsCatalogSearch() {
        compose.onNodeWithText("Discover").performClick()
        compose.onNodeWithText("Search brand or mold").assertIsDisplayed()
    }

    @Test fun bagsTabShowsCreateAction() {
        compose.onNodeWithText("Bags").performClick()
        compose.onNodeWithText("New bag").assertIsDisplayed()
    }
}
