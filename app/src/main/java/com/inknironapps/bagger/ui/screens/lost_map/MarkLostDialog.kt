package com.inknironapps.bagger.ui.screens.lost_map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@Composable
fun MarkLostDialog(
    onDismiss: () -> Unit,
    onConfirm: (courseName: String?, hole: Int?, notes: String?, captureGps: Boolean) -> Unit
) {
    var courseName by remember { mutableStateOf("") }
    var hole by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var capture by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mark disc lost") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("Course name (optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = hole,
                    onValueChange = { hole = it },
                    label = { Text("Hole number (optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = capture, onCheckedChange = { capture = it })
                    Text("Pin GPS location")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    courseName.takeIf { it.isNotBlank() },
                    hole.toIntOrNull(),
                    notes.takeIf { it.isNotBlank() },
                    capture
                )
            }) { Text("Mark lost") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
