package com.inknironapps.bagger.ui.screens.disc_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.ui.components.FlightNumbersRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogDiscDetailScreen(
    onBack: () -> Unit,
    vm: CatalogDiscDetailViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    if (state.added) LaunchedEffect(Unit) { snack.showSnackbar("Added to your shelf") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.disc?.mold ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        val disc = state.disc ?: return@Scaffold
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(disc.brand, style = MaterialTheme.typography.titleSmall)
            Text(disc.mold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            FlightNumbersRow(disc.speed, disc.glide, disc.turn, disc.fade)
            Text("${disc.discType} · ${disc.stability}", style = MaterialTheme.typography.bodyMedium)
            disc.yearReleased?.let { Text("Released $it") }
            if (disc.pdgaApproved) Text("PDGA approved", color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Button(onClick = vm::addToShelf, enabled = !state.added) {
                Text(if (state.added) "Added" else "Add to my shelf")
            }
        }
    }
}
