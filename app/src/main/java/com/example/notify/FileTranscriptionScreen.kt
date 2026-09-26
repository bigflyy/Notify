package com.example.notify

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.notify.stt.TranscriptHistory
import com.example.notify.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FileTranscriptionScreen(
    viewModel: MainViewModel,
    detailOpen: Boolean,
    onDetailOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val isEngineReady by viewModel.isEngineReady.observeAsState(false)
    val isRecording by viewModel.isRecording.observeAsState(false)
    val isImporting by viewModel.isImporting.observeAsState(false)
    val isImportCancelling by viewModel.isImportCancelling.observeAsState(false)
    val importWasCancelled by viewModel.importWasCancelled.observeAsState(false)
    val transcriptPartial by viewModel.importedTranscriptPartial.observeAsState(false)
    val history by viewModel.transcriptionHistory.observeAsState(emptyList())
    val progress by viewModel.importProgress.observeAsState()
    val fileName by viewModel.importedFileName.observeAsState()
    val transcript by viewModel.importedTranscript.observeAsState()
    val error by viewModel.importError.observeAsState()
    // Browsing a saved entry must not replace the state of an active import.
    var selectedHistoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val savedEntry = history.firstOrNull { it.id == selectedHistoryId }
    val displayedName = if (selectedHistoryId != null) savedEntry?.fileName else fileName
    val displayedText = if (selectedHistoryId != null) savedEntry?.text else transcript
    LaunchedEffect(detailOpen, selectedHistoryId, fileName) {
        // An interrupted import cannot be resumed after Android recreates the process.
        if (detailOpen && selectedHistoryId == null && fileName == null) onDetailOpenChange(false)
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Recording"
            selectedHistoryId = null
            viewModel.transcribeFile(uri, name)
            onDetailOpenChange(true)
        }
    }
    val textExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null && !displayedText.isNullOrBlank()) saveFile(context, scope, uri, "$displayedText\n")
    }
    val markdownExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        if (uri != null && !displayedText.isNullOrBlank()) saveFile(context, scope, uri, transcriptMarkdown(displayedName, displayedText))
    }

    if (!detailOpen) {
        TranscriptList(
            history = history,
            canImport = isEngineReady && !isImporting && !isRecording,
            isImporting = isImporting,
            status = when {
                !isEngineReady -> "Loading Russian speech model..."
                isRecording -> "Stop recording to transcribe a file."
                isImporting -> "Transcription continues while you browse. Deletion is available when it finishes."
                else -> error
            },
            currentFileName = fileName?.takeIf { isImporting || transcript.isNullOrBlank() },
            currentStatus = if (isImporting) "Transcribing${progress?.percent?.let { " · $it%" }.orEmpty()} · Tap to view"
                else "View result",
            onOpenCurrent = { selectedHistoryId = null; onDetailOpenChange(true) },
            onOpen = { selectedHistoryId = it.id; onDetailOpenChange(true) },
            onDelete = { viewModel.deleteSavedTranscript(it.id) },
            onImport = { filePicker.launch(arrayOf("*/*")) },
            modifier = modifier
        )
    } else {
        TranscriptDetail(
            fileName = displayedName.orEmpty(),
            transcript = displayedText,
            onCopy = { clipboard.setText(AnnotatedString(displayedText.orEmpty())) },
            onCopyMarkdown = { clipboard.setText(AnnotatedString(transcriptMarkdown(displayedName, displayedText))) },
            onSaveText = {
                textExporter.launch("${exportBaseName(displayedName)}.txt")
            },
            onSaveMarkdown = {
                markdownExporter.launch("${exportBaseName(displayedName)}.md")
            },
            modifier = modifier,
            status = {
                if (selectedHistoryId != null) {
                    if (savedEntry == null) Text(error ?: "Loading saved transcript...")
                    else if (!savedEntry.complete) Text("Partial transcript", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
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

                    if (isImporting && !transcript.isNullOrBlank()) Text("Partial transcript · Still transcribing")
                    else if (transcriptPartial && !transcript.isNullOrBlank()) Text("Partial transcript")
                    else if (importWasCancelled && transcript.isNullOrBlank()) Text("Stopped before any speech was transcribed")
                    if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    if (isImporting && transcript == null) Text("Recognized speech will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
internal fun TranscriptBackButton(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to transcripts") }
}

@Composable
internal fun TranscriptList(
    history: List<TranscriptHistory.Entry>,
    canImport: Boolean,
    isImporting: Boolean,
    status: String?,
    currentFileName: String?,
    currentStatus: String,
    onOpenCurrent: () -> Unit,
    onOpen: (TranscriptHistory.Entry) -> Unit,
    onDelete: (TranscriptHistory.Entry) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
            item { Text("Your transcripts", style = MaterialTheme.typography.headlineSmall) }
            if (status != null) item { Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (currentFileName != null) item {
                Card(onClick = onOpenCurrent, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(currentFileName, style = MaterialTheme.typography.titleMedium)
                        Text(currentStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (history.isEmpty() && currentFileName == null) item {
                Text("Choose a Russian audio or video file. Your transcripts will be saved here.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(history, key = { it.id }) { entry ->
                SavedTranscriptCard(entry, onOpen = { onOpen(entry) }, onDelete = { onDelete(entry) }, deleteEnabled = !isImporting)
            }
        }
        if (canImport) ExtendedFloatingActionButton(
            onClick = onImport,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Transcribe file") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        )
    }
}

@Composable
internal fun TranscriptDetail(
    fileName: String,
    transcript: String?,
    onCopy: () -> Unit,
    onCopyMarkdown: () -> Unit,
    onSaveText: () -> Unit,
    onSaveMarkdown: () -> Unit,
    modifier: Modifier = Modifier,
    status: @Composable () -> Unit = {}
) {
    var exportMenu by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fileName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { exportMenu = true }, enabled = !transcript.isNullOrBlank()) {
                    Icon(Icons.Default.Share, "Export transcript")
                }
                DropdownMenu(expanded = exportMenu, onDismissRequest = { exportMenu = false }) {
                    DropdownMenuItem(text = { Text("Copy text") }, onClick = { exportMenu = false; onCopy() })
                    DropdownMenuItem(text = { Text("Copy as Markdown") }, onClick = { exportMenu = false; onCopyMarkdown() })
                    DropdownMenuItem(text = { Text("Save text file") }, onClick = { exportMenu = false; onSaveText() })
                    DropdownMenuItem(text = { Text("Save Markdown file") }, onClick = { exportMenu = false; onSaveMarkdown() })
                }
            }
        }
        status()
        if (transcript != null) {
            SelectionContainer(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text(transcript.ifBlank { "No speech detected" }, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

internal fun transcriptMarkdown(fileName: String?, transcript: String?): String {
    val title = fileName.orEmpty().substringBeforeLast('.').replace(Regex("[\\r\\n]+"), " ").ifBlank { "Transcript" }
    return "# $title\n\n${transcript.orEmpty()}\n"
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
