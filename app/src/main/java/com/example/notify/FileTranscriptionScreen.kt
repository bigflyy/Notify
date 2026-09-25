package com.example.notify

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.notify.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FileTranscriptionScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val isEngineReady by viewModel.isEngineReady.observeAsState(false)
    val isRecording by viewModel.isRecording.observeAsState(false)
    val isImporting by viewModel.isImporting.observeAsState(false)
    val isImportCancelling by viewModel.isImportCancelling.observeAsState(false)
    val importWasCancelled by viewModel.importWasCancelled.observeAsState(false)
    val progress by viewModel.importProgress.observeAsState()
    val fileName by viewModel.importedFileName.observeAsState()
    val transcript by viewModel.importedTranscript.observeAsState()
    val error by viewModel.importError.observeAsState()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                ?: "Recording"
            viewModel.transcribeFile(uri, name)
        }
    }
    val textExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val text = viewModel.importedTranscript.value
        if (uri != null && !text.isNullOrBlank()) saveFile(context, scope, uri, "$text\n")
    }
    val markdownExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        val text = viewModel.importedTranscript.value
        if (uri != null && !text.isNullOrBlank()) {
            val title = fileName.orEmpty().substringBeforeLast('.').replace(Regex("[\\r\\n]+"), " ")
                .ifBlank { "Transcript" }
            saveFile(context, scope, uri, "# $title\n\n$text\n")
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Transcribe a file", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Russian speech · audio or video supported by this device")
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { filePicker.launch(arrayOf("*/*")) },
            enabled = isEngineReady && !isImporting && !isRecording
        ) { Text("Choose media file") }
        if (!isEngineReady) Text("Loading Russian speech model...")
        if (isImporting) {
            val percentage = progress?.percent
            if (percentage != null) {
                Text("Transcribing... $percentage%")
                LinearProgressIndicator(
                    progress = { percentage / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                val remaining = progress?.secondsRemaining
                Text(if (remaining != null) "About ${formatTime(remaining)} left" else "Estimating time left...")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("Transcribing...", modifier = Modifier.padding(start = 12.dp))
                }
                val processed = progress?.secondsProcessed
                if (processed != null) {
                    Text("Processed ${formatTime(processed)} of audio; total duration unavailable")
                }
            }
            TextButton(
                onClick = viewModel::cancelFileTranscription,
                enabled = !isImportCancelling
            ) { Text(if (isImportCancelling) "Stopping..." else "Cancel") }
        }
        if (importWasCancelled) {
            Text(if (transcript.isNullOrBlank()) "Stopped before any speech was transcribed"
                 else "Stopped — partial transcript")
        } else if (isImporting && !transcript.isNullOrBlank()) {
            Text("Partial transcript — still transcribing")
        }
        if (error != null) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
        if (fileName != null) {
            Spacer(Modifier.height(16.dp))
            Text(fileName.orEmpty(), style = MaterialTheme.typography.titleMedium)
        }
        if (transcript != null) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(transcript.orEmpty())) }, enabled = !transcript.isNullOrBlank()) {
                    Text("Copy")
                }
                TextButton(onClick = { textExporter.launch("${exportBaseName(fileName)}.txt") }, enabled = !transcript.isNullOrBlank()) {
                    Text("Save .txt")
                }
                TextButton(onClick = { markdownExporter.launch("${exportBaseName(fileName)}.md") }, enabled = !transcript.isNullOrBlank()) {
                    Text("Save .md")
                }
            }
            SelectionContainer(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(transcript?.ifBlank { "No speech detected" }.orEmpty())
            }
        }
    }
}

private fun formatTime(seconds: Long): String =
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

private fun exportBaseName(fileName: String?): String = fileName.orEmpty()
    .substringBeforeLast('.')
    .replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
    .trim('.', ' ')
    .take(80)
    .ifBlank { "Transcript" }

private fun saveFile(context: Context, scope: CoroutineScope, uri: Uri, text: String) {
    scope.launch {
        val saved = withContext(Dispatchers.IO) {
            runCatching {
                val output = context.contentResolver.openOutputStream(uri)
                    ?: error("Could not open export file")
                output.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }.isSuccess
        }
        Toast.makeText(context, if (saved) "Transcript saved" else "Could not save transcript", Toast.LENGTH_SHORT).show()
    }
}
