package com.inknironapps.bagger.ui.screens.add_disc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.ml.ScoredDisc
import com.inknironapps.bagger.ui.components.CatalogDiscCard

@Composable
fun PickStep(candidates: List<ScoredDisc>, onPick: (DiscEntity) -> Unit, onSearch: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "Pick the closest match",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(candidates, key = { it.disc.id }) { sd ->
                CatalogDiscCard(sd.disc, { onPick(sd.disc) })
            }
        }
        OutlinedButton(
            onClick = onSearch,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp)
        ) {
            Text("None of these — search manually")
        }
    }
}
