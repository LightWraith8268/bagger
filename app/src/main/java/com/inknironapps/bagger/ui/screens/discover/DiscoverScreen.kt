package com.inknironapps.bagger.ui.screens.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.ui.components.CatalogDiscCard
import com.inknironapps.bagger.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onDiscClick: (String) -> Unit,
    vm: DiscoverViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("Discover") })
                OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::setQuery,
                    label = { Text("Search brand or mold") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf<String?>(null, "Putter", "Approach", "Mid", "Fairway", "Driver").forEach { t ->
                        FilterChip(
                            selected = state.typeFilter == t,
                            onClick = { vm.setType(t) },
                            label = { Text(t ?: "All") }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (state.results.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState("No discs match", "Try a different search or filter.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(180.dp),
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.results, key = { it.id }) { disc ->
                    CatalogDiscCard(disc, { onDiscClick(disc.id) })
                }
            }
        }
    }
}
