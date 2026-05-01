package com.inknironapps.bagger.ui.screens.shelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
fun ShelfScreen(
    onAddDisc: () -> Unit,
    onDiscClick: (String) -> Unit,
    vm: ShelfViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Shelf") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddDisc,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add disc") }
            )
        }
    ) { padding ->
        if (state.owned.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState(
                    "Your shelf is empty",
                    "Tap Add disc to catalog your first disc."
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(180.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding)
            ) {
                items(state.owned, key = { it.id }) { owned ->
                    OwnedDiscCard(
                        owned = owned,
                        catalog = state.catalog[owned.discId],
                        onClick = { onDiscClick(owned.id) }
                    )
                }
            }
        }
    }
}
