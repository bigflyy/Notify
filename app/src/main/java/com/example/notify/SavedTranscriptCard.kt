package com.example.notify

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.notify.stt.TranscriptHistory
import java.text.DateFormat
import java.util.Date

@Composable
fun SavedTranscriptCard(entry: TranscriptHistory.Entry, onOpen: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember(entry.id) { mutableStateOf(false) }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.fileName, style = MaterialTheme.typography.titleMedium)
                Text("${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.createdAt))} · ${if (entry.complete) "Complete" else "Partial"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { confirmDelete = true }) {
                Icon(Icons.Default.Delete, "Delete transcript", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            title = "Delete transcript?",
            message = "Delete the saved transcript for \"${entry.fileName}\"? Your original audio or video file will not be deleted.",
            onDismiss = { confirmDelete = false },
            onConfirm = { confirmDelete = false; onDelete() }
        )
    }
}
