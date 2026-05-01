package com.inknironapps.bagger.ui.screens.bags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.ui.components.EmptyState
import com.inknironapps.bagger.ui.components.OwnedDiscCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BagDetailScreen(
    onBack: () -> Unit,
    onDiscClick: (String) -> Unit,
    vm: BagDetailViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.bag?.name ?: "Bag") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (state.discs.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState("No discs in this bag", "Move discs from your shelf into this bag.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.discs, key = { it.id }) { disc ->
                    OwnedDiscCard(disc, state.catalog[disc.discId], { onDiscClick(disc.id) })
                }
            }
        }
    }
}
