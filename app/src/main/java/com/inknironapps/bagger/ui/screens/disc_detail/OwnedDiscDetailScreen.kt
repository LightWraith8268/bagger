package com.inknironapps.bagger.ui.screens.disc_detail

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.inknironapps.bagger.ui.components.FlightNumbersRow
import com.inknironapps.bagger.ui.screens.lost_map.MarkLostDialog
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnedDiscDetailScreen(
    onBack: () -> Unit,
    vm: OwnedDiscDetailViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var showBagPicker by remember { mutableStateOf(false) }
    var showLostDialog by remember { mutableStateOf(false) }

    val addPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { vm.addPhotoFromUri(it, "Front") }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.catalog?.mold ?: "Disc") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.delete(); onBack() }) {
                        Icon(Icons.Filled.Delete, null)
                    }
                }
            )
        }
    ) { padding ->
        val o = state.owned ?: return@Scaffold
        val c = state.catalog
        Column(
            Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.photos.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.photos, key = { it.id }) { photo ->
                        Card(modifier = Modifier.size(width = 160.dp, height = 160.dp)) {
                            AsyncImage(
                                model = File(photo.localPath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (c != null) {
                    Text(c.brand, style = MaterialTheme.typography.titleSmall)
                    Text(c.mold, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    FlightNumbersRow(c.speed, c.glide, c.turn, c.fade)
                }
                Spacer(Modifier.height(8.dp))
                Text("State: ${o.state}", style = MaterialTheme.typography.bodyMedium)
                o.plasticType?.let { Text("Plastic: $it") }
                o.weight?.let { Text("Weight: ${it}g") }
                o.color?.let { Text("Color: $it") }
                o.notes?.let { Text("Notes: $it") }
                Spacer(Modifier.height(12.dp))
                Text("Move to:", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(onClick = { vm.changeState("Shelf", null) }) { Text("Shelf") }
                    FilledTonalButton(onClick = { showBagPicker = true }) { Text("A bag") }
                    FilledTonalButton(onClick = { showLostDialog = true }) { Text("Lost") }
                    FilledTonalButton(onClick = { vm.changeState("Retired") }) { Text("Retired") }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { addPhotoLauncher.launch("image/*") }) { Text("Add photo") }
            }
        }

        if (showLostDialog) {
            MarkLostDialog(
                onDismiss = { showLostDialog = false },
                onConfirm = { course, hole, notes, gps ->
                    vm.markLost(course, hole, notes, gps)
                    showLostDialog = false
                }
            )
        }

        if (showBagPicker) {
            AlertDialog(
                onDismissRequest = { showBagPicker = false },
                title = { Text("Pick a bag") },
                text = {
                    Column {
                        if (state.bags.isEmpty()) Text("No bags yet — create one in Bags tab.")
                        state.bags.forEach { bag ->
                            TextButton(onClick = {
                                vm.changeState("InBag", bag.id)
                                showBagPicker = false
                            }) { Text(bag.name) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showBagPicker = false }) { Text("Cancel") } }
            )
        }
    }
}
