package com.inknironapps.bagger.ui.screens.lost_map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.inknironapps.bagger.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostMapScreen(onBack: () -> Unit, vm: LostMapViewModel = hiltViewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val withGps = state.unfound.filter { it.lat != null && it.lng != null }
    val withoutGps = state.unfound.filter { it.lat == null || it.lng == null }

    val center = withGps.firstOrNull()?.let { LatLng(it.lat!!, it.lng!!) } ?: LatLng(40.0, -100.0)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center, if (withGps.isEmpty()) 3f else 12f)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lost Discs") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        if (state.unfound.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState(
                    "No lost discs",
                    "Discs marked Lost from the disc detail screen show up here."
                )
            }
        } else {
            Column(Modifier.padding(padding)) {
                Box(Modifier.weight(1f)) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState
                    ) {
                        withGps.forEach { event ->
                            val owned = state.owned[event.ownedDiscId]
                            val disc = owned?.let { state.catalog[it.discId] }
                            Marker(
                                state = MarkerState(LatLng(event.lat!!, event.lng!!)),
                                title = disc?.let { "${it.brand} ${it.mold}" } ?: "Disc",
                                snippet = listOfNotNull(
                                    event.courseName,
                                    event.holeNumber?.let { "Hole $it" }
                                ).joinToString(" · ")
                            )
                        }
                    }
                }

                if (withoutGps.isNotEmpty()) {
                    Text(
                        "Without GPS pin",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(12.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(withoutGps, key = { it.id }) { e ->
                            val owned = state.owned[e.ownedDiscId]
                            val disc = owned?.let { state.catalog[it.discId] }
                            Card {
                                Column(Modifier.padding(8.dp)) {
                                    Text(disc?.let { "${it.brand} ${it.mold}" } ?: "Disc")
                                    e.courseName?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(onClick = { vm.markFound(e.id) }) { Text("Mark found") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
