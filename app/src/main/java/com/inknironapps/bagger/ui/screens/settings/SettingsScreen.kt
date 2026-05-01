package com.inknironapps.bagger.ui.screens.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inknironapps.bagger.data.update.UpdateState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snackMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val backupSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = vm.exportBackupJson()
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            snackMessage = "Backup exported"
        }
    }

    val backupOpener = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() } ?: return@launch
            snackMessage = vm.importBackupJson(text)
        }
    }

    val csvSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val text = vm.exportCsv()
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            snackMessage = "CSV exported"
        }
    }

    val snack = remember { SnackbarHostState() }
    LaunchedEffect(snackMessage) {
        snackMessage?.let {
            snack.showSnackbar(it)
            snackMessage = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snack) }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.verticalScroll(rememberScrollState())) {

                if (state.updateState is UpdateState.UpdateAvailable) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Update available", style = MaterialTheme.typography.titleMedium)
                            Text("A newer version of Bagger is available on the Play Store.")
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    "market://details?id=${context.packageName}".toUri()
                                )
                                context.startActivity(intent)
                            }) { Text("Open Play Store") }
                        }
                    }
                }

                SectionHeader("Appearance")
                Column(Modifier.padding(horizontal = 16.dp)) {
                    listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (k, label) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.setTheme(k) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = state.themeMode == k, onClick = { vm.setTheme(k) })
                            Text(label)
                        }
                    }
                }

                SectionHeader("Backup & data")
                ListItem(
                    headlineContent = { Text("Export backup (JSON)") },
                    modifier = Modifier.clickable { backupSaver.launch("bagger-backup.json") }
                )
                ListItem(
                    headlineContent = { Text("Restore from backup") },
                    modifier = Modifier.clickable { backupOpener.launch(arrayOf("application/json")) }
                )
                ListItem(
                    headlineContent = { Text("Export inventory CSV") },
                    modifier = Modifier.clickable { csvSaver.launch("bagger-inventory.csv") }
                )
                ListItem(
                    headlineContent = { Text("Delete all my data") },
                    modifier = Modifier.clickable { showDeleteConfirm = true }
                )

                SectionHeader("About")
                ListItem(
                    headlineContent = { Text("Privacy policy") },
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://lightwraith8268.github.io/inknironapps-legal/privacy-policy.html".toUri()
                            )
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Terms & Conditions") },
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                "https://lightwraith8268.github.io/inknironapps-legal/terms.html".toUri()
                            )
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Send feedback") },
                    modifier = Modifier.clickable {
                        val mail = Intent(Intent.ACTION_SENDTO).apply {
                            data = "mailto:info@inknironapps.com".toUri()
                            putExtra(
                                Intent.EXTRA_SUBJECT,
                                "Bagger feedback (v${state.versionName})"
                            )
                        }
                        context.startActivity(mail)
                    }
                )

                ListItem(
                    headlineContent = {
                        Text(
                            "Bagger v${state.versionName}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    supportingContent = {
                        Text(
                            "Ink & Iron Apps",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )
            }
        }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete all data?") },
                text = {
                    Text(
                        "This wipes every disc, bag, lost event, and wishlist item from this device. Cannot be undone."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        vm.deleteAllData()
                        showDeleteConfirm = false
                        snackMessage = "All data deleted"
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}
