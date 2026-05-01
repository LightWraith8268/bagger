package com.inknironapps.bagger.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inknironapps.bagger.data.db.entity.DiscEntity
import com.inknironapps.bagger.data.db.entity.OwnedDiscEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogDiscCard(disc: DiscEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(12.dp)) {
            Text(disc.brand, style = MaterialTheme.typography.labelMedium)
            Text(disc.mold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            FlightNumbersRow(disc.speed, disc.glide, disc.turn, disc.fade)
            Spacer(Modifier.height(4.dp))
            Text(
                "${disc.discType} · ${disc.stability}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnedDiscCard(
    owned: OwnedDiscEntity,
    catalog: DiscEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(12.dp)) {
            if (catalog != null) {
                Text(catalog.brand, style = MaterialTheme.typography.labelMedium)
                Text(catalog.mold, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                FlightNumbersRow(catalog.speed, catalog.glide, catalog.turn, catalog.fade)
            } else {
                Text("Unknown disc", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = {}, label = { Text(owned.state) })
                owned.plasticType?.let { AssistChip(onClick = {}, label = { Text(it) }) }
                owned.weight?.let { AssistChip(onClick = {}, label = { Text("${it}g") }) }
            }
        }
    }
}
