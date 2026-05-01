package com.inknironapps.bagger.ui.screens.comparison

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.ui.components.FlightNumbersRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(onBack: () -> Unit, vm: ComparisonViewModel = hiltViewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val palette = listOf(Color(0xFF095F73), Color(0xFFC53030), Color(0xFFD69E2E))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compare discs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.picked.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Flight comparison", style = MaterialTheme.typography.titleMedium)
                        Box(Modifier.fillMaxWidth().height(220.dp)) {
                            FlightChartCanvas(state.picked.mapIndexed { i, d -> d to palette[i % palette.size] })
                        }
                        state.picked.forEachIndexed { i, d ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    Modifier
                                        .size(12.dp)
                                        .background(palette[i % palette.size], shape = CircleShape)
                                )
                                Text("${d.brand} ${d.mold}")
                                FlightNumbersRow(d.speed, d.glide, d.turn, d.fade)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text("Search to add (max 3)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.all, key = { it.id }) { d ->
                    val picked = state.picked.any { it.id == d.id }
                    Card(onClick = { vm.togglePick(d) }) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = picked, onCheckedChange = { vm.togglePick(d) })
                            Column {
                                Text("${d.brand} — ${d.mold}", style = MaterialTheme.typography.titleSmall)
                                FlightNumbersRow(d.speed, d.glide, d.turn, d.fade)
                            }
                        }
                    }
                }
            }
        }
    }
}
