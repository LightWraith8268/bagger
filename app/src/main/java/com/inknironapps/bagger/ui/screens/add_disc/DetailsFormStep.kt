package com.inknironapps.bagger.ui.screens.add_disc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.inknironapps.bagger.ui.components.FlightNumbersRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsFormStep(
    state: AddDiscState,
    onPlastic: (String) -> Unit,
    onWeight: (String) -> Unit,
    onColor: (String) -> Unit,
    onCondition: (String) -> Unit,
    onNotes: (String) -> Unit,
    onSave: () -> Unit
) {
    val disc = state.selectedDisc ?: return
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(disc.brand, style = MaterialTheme.typography.titleSmall)
        Text(disc.mold, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        FlightNumbersRow(disc.speed, disc.glide, disc.turn, disc.fade)
        Spacer(Modifier.height(8.dp))
        Text("Optional details", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            state.plasticType,
            onPlastic,
            label = { Text("Plastic (e.g. Star, ESP)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            state.weight,
            onWeight,
            label = { Text("Weight (g)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedTextField(
            state.color,
            onColor,
            label = { Text("Color") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Text("Condition")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("New", "Good", "Beat", "Dyed").forEach { c ->
                FilterChip(
                    selected = state.condition == c,
                    onClick = { onCondition(c) },
                    label = { Text(c) }
                )
            }
        }

        OutlinedTextField(
            state.notes,
            onNotes,
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save to shelf") }
    }
}
