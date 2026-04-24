package com.example.notify

import android.Manifest
import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.notify.domain.AudioEntry
import com.example.notify.domain.TextEntry
import com.example.notify.ui.MainViewModel
import com.example.notify.ui.MainViewModelNoPattern
import com.example.notify.ui.theme.NotifyTheme
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NotifyTheme {
                val viewModel: MainViewModel = viewModel()
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { }

                LaunchedEffect(Unit) {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                val currentNote by viewModel.currentNote.observeAsState()

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    floatingActionButton = {
                        if (currentNote == null) {
                            FloatingActionButton(onClick = { viewModel.createNewTextNote() }) {
                                Icon(Icons.Default.Add, contentDescription = "New Note")
                            }
                        }
                    }
                ) { innerPadding ->
                    MainScreen(viewModel, Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel, modifier: Modifier = Modifier) {
    val notes by viewModel.allNotes.observeAsState(emptyList())
    val allDbTags by viewModel.allTags.observeAsState(emptyList())
    val currentNote by viewModel.currentNote.observeAsState()
    val isRecording by viewModel.isRecording.observeAsState(false)
    val isEngineReady by viewModel.isEngineReady.observeAsState(false)
    val playingAudioPath by viewModel.playingAudioPath.observeAsState()
    val playbackPosition by viewModel.playbackPosition.observeAsState(0)
    val playbackDuration by viewModel.playbackDuration.observeAsState(0)

    if (currentNote != null) {
        BackHandler {
            viewModel.stopAudio()
            viewModel.selectNote(null)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (currentNote == null) {
            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No notes yet. Tap + to create one.", color = Color.Gray)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                    items(notes) { note ->
                        Card(
                            onClick = { viewModel.selectNote(note) },
                            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    // 1. The Title
                                    Text(
                                        text = if (note.title.isNullOrBlank()) "Empty Note" else note.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1
                                    )

                                    // 2. THE TAGS (Add this block)
                                    if (note.tags.isNotEmpty()) {
                                        FlowRow(
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            note.tags.take(3).forEach { tag -> // Show only first 3 tags to keep it clean
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "#${tag.name}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                                    )
                                                }
                                            }
                                            if (note.tags.size > 3) {
                                                Text(
                                                    text = "+${note.tags.size - 3}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray,
                                                    modifier = Modifier.align(Alignment.CenterVertically)
                                                )
                                            }
                                        }
                                    }

                                    // 3. The Date
                                    val dateFormatter = remember { SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()) }
                                    Text(
                                        text = "Created: ${dateFormatter.format(note.createdAt)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteNote(note) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            NoteEditor(
                note = currentNote!!,
                isRecording = isRecording,
                isEngineReady = isEngineReady,
                playingAudioPath = playingAudioPath,
                playbackPosition = playbackPosition,
                playbackDuration = playbackDuration,
                onEntryContentChange = { index, content -> viewModel.updateEntryContent(index, content) },
                onTitleChange = { viewModel.updateNoteTitle(it) },
                onBack = {
                    viewModel.stopAudio()
                    viewModel.selectNote(null)
                },
                onMicTap = {
                    if (isRecording) viewModel.stopAudioRecording()
                    else viewModel.startAudioRecording()
                },
                onPlayAudio = { viewModel.playAudio(it) },
                onSeek = { viewModel.seekTo(it) },
                onSkipForward = { viewModel.skipForward() },
                onSkipBackward = { viewModel.skipBackward() },
                onSplitAndInsertAudio = { index, pos -> viewModel.splitAndInsertAudio(index, pos) },
                onMoveEntry = { from, to -> viewModel.moveEntry(from, to) },
                onCommitMove = { viewModel.commitMove() },
                onDeleteEntry = { viewModel.deleteEntry(it) },
                onAddTextEntry = { viewModel.addTextEntry() },
                onAddTag = { viewModel.addTag(it) },
                allExistingTags = allDbTags,
                onRemoveTag = { tag -> viewModel.removeTag(currentNote, tag) },
                onDeleteTagGlobally = { tag: com.example.notify.domain.Tag ->
                    viewModel.deleteTagFromDatabase(tag)
                }

            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditor(
    note: com.example.notify.domain.Note,
    isRecording: Boolean,
    isEngineReady: Boolean,
    playingAudioPath: String?,
    playbackPosition: Int,
    playbackDuration: Int,
    onEntryContentChange: (Int, String) -> Unit,
    onTitleChange: (String) -> Unit,
    onBack: () -> Unit,
    onMicTap: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onSeek: (Int) -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSplitAndInsertAudio: (Int, Int) -> Unit,
    onMoveEntry: (Int, Int) -> Unit,
    onCommitMove: () -> Unit,
    onDeleteEntry: (Int) -> Unit,
    onAddTextEntry: () -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (com.example.notify.domain.Tag) -> Unit,
    allExistingTags: List<com.example.notify.domain.Tag>,
    onDeleteTagGlobally: (com.example.notify.domain.Tag) -> Unit
) {
    val scope = rememberCoroutineScope()
    var title by remember(note.id) { mutableStateOf(note.title ?: "") }
    var selectedBlockIndex by remember { mutableIntStateOf(-1) }
    var micTargetIndex by remember { mutableIntStateOf(-1) }
    var cursorPosition by remember { mutableIntStateOf(0) }
    LaunchedEffect(note.entries.size) {
        // When the list size changes (new audio added),
        // ensure the selection stays on the block you were recording for
        if (selectedBlockIndex != -1) {
            // If the new audio was inserted above our selection, shift selection down
            // But with our logic, it's usually inserted below, so we stay put.
        }
    }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isRecording) {
                        onMicTap() // This calls viewModel.stopAudioRecording()

                        // Add the auto-scroll here:
                        scope.launch {
                            listState.animateScrollToItem(note.entries.size)
                        }
                    } else {
                        onMicTap() // This calls viewModel.startAudioRecording()
                    }
                },
                shape = CircleShape,
                containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primaryContainer
            ) {
                if (!isEngineReady && !isRecording) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Mic"
                    )
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)) {
            TextField(
                value = title,
                onValueChange = { title = it; onTitleChange(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Title", style = MaterialTheme.typography.headlineMedium) },
                textStyle = MaterialTheme.typography.headlineMedium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f))
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Show existing tags
                note.tags.forEach { tag ->
                    InputChip(
                        selected = false,
                        onClick = { /* Optional: filter by tag */ },
                        label = { Text("#${tag.name}", style = MaterialTheme.typography.labelSmall) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onRemoveTag(tag) }
                            )
                        },
                        colors = InputChipDefaults.inputChipColors(
                            labelColor = MaterialTheme.colorScheme.primary
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    )
                }
                // 2. The "Add Tag" button
                var showDialog by remember { mutableStateOf(false) }

                AssistChip(
                    onClick = { showDialog = true },
                    label = { Text("Add Tag") },
                    leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp)) }
                )

                // 3. Simple Dialog to type new tag
                if (showDialog) {
                    var newTagName by remember { mutableStateOf("") }

                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title = { Text("New Tag") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextField(
                                    value = newTagName,
                                    onValueChange = { newTagName = it },
                                    singleLine = true,
                                    placeholder = { Text("e.g. Work, Ideas...") }
                                )

                                // Only show if there are tags to suggest
                                if (allExistingTags.isNotEmpty()) {
                                    Text("Suggestions:", style = MaterialTheme.typography.labelSmall)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(allExistingTags) { tag ->
                                            // We use OutlinedCard to get the standard chip border and shape
                                            OutlinedCard(
                                                shape = RoundedCornerShape(8.dp), // Recreates the chip shape
                                                border = BorderStroke(
                                                    width = 1.dp,
                                                    // Uses the standard chip outline color
                                                    color = MaterialTheme.colorScheme.outline
                                                ),
                                                colors = CardDefaults.outlinedCardColors(
                                                    // Recreates the transparent chip background
                                                    containerColor = Color.Transparent
                                                ),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    // 1. More padding to make the chip "a bit bigger"
                                                    modifier = Modifier.padding(start = 12.dp, end = 6.dp)
                                                ) {
                                                    // Click the text to add the tag
                                                    Text(
                                                        text = tag.name,
                                                        // 2. Standard SuggestionChip text style
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier
                                                            .clickable {
                                                                onAddTag(tag.name)
                                                                showDialog = false
                                                            }
                                                            .padding(vertical = 10.dp) // More vertical space
                                                    )

                                                    // 3. Spacing between the name and the X button
                                                    Spacer(modifier = Modifier.width(15.dp))

                                                    // The 'X' button to delete from DB
                                                    IconButton(
                                                        onClick = { onDeleteTagGlobally(tag) },
                                                        modifier = Modifier.size(30.dp) // Standard icon size
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Close,
                                                            contentDescription = "Delete Global",
                                                            // Use a slightly dimmer red so it doesn't shout
                                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                if (newTagName.isNotBlank()) {
                                    onAddTag(newTagName)
                                    showDialog = false
                                }
                            }) { Text("Add") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDialog = false }) { Text("Cancel") }
                        }
                    )
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(
                    note.entries,
                    key = { index, entry ->
                        // Use Type + ID (or Hash) + Index for absolute stability
                        "${entry.javaClass.simpleName}_${entry.id ?: entry.hashCode()}_$index"
                    }
                ) { index, entry ->
                    val isSelected = selectedBlockIndex == index

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // REORDER CONTROLS (ARROWS)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    val newIndex = index - 1 // Calculate target index
                                    onMoveEntry(index, newIndex)
                                    onCommitMove()
                                    selectedBlockIndex = newIndex // Update selection to follow
                                },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, "Up", tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.LightGray)
                            }
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    val newIndex = index + 1 // Calculate target index
                                    onMoveEntry(index, newIndex)
                                    onCommitMove()
                                    selectedBlockIndex = newIndex // Update selection to follow
                                },
                                enabled = index < note.entries.size - 1,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, "Down", tint = if (index < note.entries.size - 1) MaterialTheme.colorScheme.primary else Color.LightGray)
                            }
                        }

                        // CONTENT AREA
                        Box(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                            when (entry) {
                                is TextEntry -> {
                                    var textFieldValue by remember(entry) {
                                        mutableStateOf(TextFieldValue(entry.text, TextRange(entry.text.length)))
                                    }
                                    TextField(
                                        value = textFieldValue,
                                        onValueChange = {
                                            textFieldValue = it
                                            cursorPosition = it.selection.start
                                            onEntryContentChange(index, it.text)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    selectedBlockIndex = index
                                                    micTargetIndex = index
                                                    cursorPosition = textFieldValue.selection.start
                                                }
                                            },
                                        placeholder = { Text("Write your note here...") },
                                        textStyle = MaterialTheme.typography.bodyLarge,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        )
                                    )
                                }
                                is AudioEntry -> {
                                    val isPlaying = playingAudioPath == entry.audioPath
                                    // Add this line to check if we should show the "Resume" state
                                    val isPaused = playingAudioPath == null && playbackPosition > 0 && isSelected

                                    Card(
                                        onClick = {
                                            focusManager.clearFocus()
                                            selectedBlockIndex = index
                                            micTargetIndex = index
                                            // We moved the play logic to the button below,
                                            // but clicking the card can still select the block.
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ),
                                        border = if (isPlaying) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                // REPLACE YOUR OLD ICON WITH THIS ICONBUTTON
                                                IconButton(onClick = { entry.audioPath?.let { onPlayAudio(it) } }) {
                                                    Icon(
                                                        imageVector = when {
                                                            isPlaying -> Icons.Default.Pause
                                                            isPaused -> Icons.Default.PlayArrow // Ready to resume
                                                            else -> Icons.Default.PlayArrow // New start
                                                        },
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Voice Recording", style = MaterialTheme.typography.labelLarge)
                                                Spacer(modifier = Modifier.weight(1f))
                                                if (isPlaying) {
                                                    Text(
                                                        text = formatTime(playbackPosition) + " / " + formatTime(playbackDuration),
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                            if (isPlaying) {
                                                Slider(
                                                    value = playbackPosition.toFloat(),
                                                    onValueChange = { onSeek(it.toInt()) },
                                                    valueRange = 0f..playbackDuration.toFloat().coerceAtLeast(1f),
                                                    modifier = Modifier.height(24.dp)
                                                )
                                            }
                                            if (!entry.transcription.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = entry.transcription,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // DELETE BUTTON (VISIBLE ONCE SELECTED)
                        if (isSelected) {
                            IconButton(
                                onClick = { onDeleteEntry(index) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete block",
                                    tint = Color.Red.copy(alpha = 0.6f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                item {
                    TextButton(
                        onClick = onAddTextEntry,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add text block")
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}