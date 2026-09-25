package com.example.notify

import com.example.notify.domain.AudioEntry
import com.example.notify.domain.Note
import com.example.notify.domain.Tag
import com.example.notify.domain.TextEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

class NoteMarkdownTest {
    @Test
    fun exportsEntriesAndTranscriptsInOrder() {
        val date = Date(0)
        val note = Note(1, "Meeting", date).apply {
            addTag(Tag(1, "work"))
            addEntry(TextEntry(1, 0, date, "First line\nSecond line"))
            addEntry(AudioEntry(2, 1, date, "recording.wav", "Spoken words", 3.0))
            addEntry(TextEntry(3, 2, date, "Last paragraph"))
        }

        assertEquals(
            "# Meeting\n\nTags: work\n\nFirst line\nSecond line\n\n" +
                "**Voice recording transcript**\n\nSpoken words\n\nLast paragraph\n",
            noteToMarkdown(note)
        )
    }

    @Test
    fun includesUntranscribedRecordingAndSafeFileName() {
        val date = Date(0)
        val note = Note(1, "  Bad/title  ", date).apply {
            addEntry(AudioEntry(1, 0, date, "recording.wav", null, 3.0))
        }

        assertEquals("# Bad/title\n\n*Voice recording (no transcript)*\n", noteToMarkdown(note))
        assertEquals("Bad_title.md", markdownFileName(note))
    }
}
