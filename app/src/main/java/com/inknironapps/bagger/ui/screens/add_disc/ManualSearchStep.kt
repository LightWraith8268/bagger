package com.inknironapps.bagger.ui.screens.add_disc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.ui.components.CatalogDiscCard
import com.inknironapps.bagger.ui.screens.discover.DiscoverViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualSearchStep(
    initialQuery: String,
    onPick: (DiscEntity) -> Unit,
    vm: DiscoverViewModel = hiltViewModel()
) {
    LaunchedEffect(initialQuery) { if (initialQuery.isNotBlank()) vm.setQuery(initialQuery) }
    val state by vm.ui.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Search the catalog", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = vm::setQuery,
            label = { Text("Brand or mold") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.results, key = { it.id }) { d ->
                CatalogDiscCard(d, { onPick(d) })
            }
        }
    }
}
