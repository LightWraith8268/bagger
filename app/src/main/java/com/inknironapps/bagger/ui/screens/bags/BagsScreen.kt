package com.inknironapps.bagger.ui.screens.bags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.ui.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BagsScreen(
    onBagClick: (String) -> Unit,
    vm: BagsViewModel = hiltViewModel()
) {
    val state by vm.ui.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bags") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreate = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("New bag") }
            )
        }
    ) { padding ->
        if (state.bags.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState("No bags yet", "Create a bag to organize your discs.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.bags, key = { it.id }) { bag ->
                    Card(onClick = { onBagClick(bag.id) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(bag.name, style = MaterialTheme.typography.titleMedium)
                            bag.description?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        if (showCreate) {
            AlertDialog(
                onDismissRequest = { showCreate = false; newName = "" },
                title = { Text("New bag") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.createBag(newName)
                        showCreate = false; newName = ""
                    }) { Text("Create") }
                },
                dismissButton = {
                    TextButton(onClick = { showCreate = false; newName = "" }) { Text("Cancel") }
                }
            )
        }
    }
}
