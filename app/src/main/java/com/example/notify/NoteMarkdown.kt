package com.example.notify

import com.example.notify.domain.AudioEntry
import com.example.notify.domain.Note
import com.example.notify.domain.TextEntry

fun noteToMarkdown(note: Note): String {
    val title = note.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled note"
    val sections = mutableListOf("# ${title.replace(Regex("[\\r\\n]+"), " ")}")

    if (note.tags.isNotEmpty()) {
        sections += "Tags: ${note.tags.joinToString(", ") { it.name }}"
    }

    note.entries.forEach { entry ->
        when (entry) {
            is TextEntry -> entry.text?.takeIf { it.isNotBlank() }?.let { sections += it }
            is AudioEntry -> sections += entry.transcription
                ?.takeIf { it.isNotBlank() }
                ?.let { "**Voice recording transcript**\n\n$it" }
                ?: "*Voice recording (no transcript)*"
        }
    }

    return sections.joinToString("\n\n", postfix = "\n")
}

fun markdownFileName(note: Note): String {
    val title = note.title?.trim().orEmpty()
        .replace(Regex("[\\\\/:*?\"<>|\\r\\n]"), "_")
        .trim('.', ' ')
        .take(80)
        .ifBlank { "Untitled note" }
    return "$title.md"
}
