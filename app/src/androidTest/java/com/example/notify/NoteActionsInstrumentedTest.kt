package com.example.notify

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.notify.domain.AudioEntry
import com.example.notify.domain.Note
import com.example.notify.domain.Tag
import com.example.notify.domain.TextEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Date

class NoteActionsInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private var deletedBlocks = 0
    private var removedTags = 0
    private var deletedTags = 0

    private fun showEditor(audio: Boolean = false): Note {
        val note = Note(1, "Test note", Date()).apply {
            addEntry(if (audio) AudioEntry(1, 0, Date(), "unused.pcm", "Sample transcript", 5.0)
                     else TextEntry(1, 0, Date(), "Sample text"))
            addTag(Tag(1, "Work"))
        }
        compose.setContent {
            NoteEditor(
                note = note, isRecording = false, isEngineReady = true,
                playingAudioPath = null, playbackPosition = 0, playbackDuration = 0,
                onEntryContentChange = { _, _ -> }, onTitleChange = {}, onBack = {},
                onMicTap = {}, onPlayAudio = {}, onSeek = {}, onSkipForward = {},
                onSkipBackward = {}, onSplitAndInsertAudio = { _, _ -> },
                onMoveEntry = { _, _ -> }, onCommitMove = {},
                onDeleteEntry = { deletedBlocks++ }, onAddTextEntry = {}, onAddTag = {},
                onRemoveTag = { removedTags++ }, allExistingTags = note.tags,
                onDeleteTagGlobally = { deletedTags++ }
            )
        }
        return note
    }

    @Test fun textAndTagActionsWaitForConfirmation() {
        showEditor()
        compose.onNodeWithText("Sample text").performClick()
        compose.onNodeWithContentDescription("Delete block").performClick()
        compose.runOnIdle { assertEquals(0, deletedBlocks) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, deletedBlocks) }
        compose.onNodeWithContentDescription("Delete block").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(1, deletedBlocks) }

        compose.onNodeWithContentDescription("Remove", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(0, removedTags) }
        compose.onNodeWithText("Remove").performClick()
        compose.runOnIdle { assertEquals(1, removedTags) }

        compose.onNodeWithText("Add Tag").performClick()
        compose.onNodeWithContentDescription("Delete Global").performClick()
        compose.runOnIdle { assertEquals(0, deletedTags) }
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(1, deletedTags) }
    }

    @Test fun audioDeletionCanBeCancelled() {
        showEditor(audio = true)
        compose.onNodeWithText("Sample transcript").performClick()
        compose.onNodeWithContentDescription("Delete block").performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, deletedBlocks) }
        compose.onNodeWithContentDescription("Delete block").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(1, deletedBlocks) }
    }

    @Test fun wholeNoteDeletionWaitsForConfirmation() {
        var deletions = 0
        compose.setContent { NoteDeleteButton(Note(1, "Test note", Date())) { deletions++ } }
        compose.onNodeWithContentDescription("Delete note").performClick()
        compose.runOnIdle { assertEquals(0, deletions) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertEquals(0, deletions) }
        compose.onNodeWithContentDescription("Delete note").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(1, deletions) }
    }

    @Test fun exportMenuCopiesWholeNoteAsMarkdown() {
        val note = showEditor()
        compose.onNodeWithContentDescription("Export note").performClick()
        compose.onNodeWithText("Copy as Markdown").performClick()
        compose.runOnIdle {
            val clipboard = InstrumentationRegistry.getInstrumentation().targetContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(noteToMarkdown(note), clipboard.primaryClip?.getItemAt(0)?.text.toString())
        }
    }
}
