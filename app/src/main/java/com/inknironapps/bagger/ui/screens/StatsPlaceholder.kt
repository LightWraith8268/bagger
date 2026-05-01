package com.inknironapps.bagger.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.inknironapps.bagger.ui.components.EmptyState

@Composable
fun StatsPlaceholder(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) { EmptyState("Stats", "Coming in Plan 7.") }
}

@Composable
fun SettingsPlaceholder(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize()) { EmptyState("Settings", "Coming in Plan 7.") }
}
