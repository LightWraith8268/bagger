package com.inknironapps.bagger.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit, vm: StatsViewModel = hiltViewModel()) {
    val s by vm.ui.collectAsStateWithLifecycle()
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Stats") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                }
            }
        )
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatCard("Total discs", s.totalDiscs.toString())
            if (s.totalValue > 0) {
                StatCard("Inventory value", "$" + (s.totalValue / 100.0).format(2))
            }
            StatCard("Lost this year", s.lostThisYear.toString())
            StatCard("Retired", s.retiredCount.toString())

            if (s.byBrand.isNotEmpty()) BarSection("By brand", s.byBrand)
            if (s.byType.isNotEmpty()) BarSection("By type", s.byType)
            if (s.byPlastic.isNotEmpty()) BarSection("By plastic", s.byPlastic)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable
private fun BarSection(title: String, data: Map<String, Int>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            val maxV = data.values.max()
            data.entries.sortedByDescending { it.value }.forEach { (k, v) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(k, modifier = Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(20.dp)
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val w = size.width * (v.toFloat() / maxV.toFloat())
                            drawRect(color = Color(0xFF095F73), topLeft = Offset.Zero, size = Size(w, size.height))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(v.toString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)
