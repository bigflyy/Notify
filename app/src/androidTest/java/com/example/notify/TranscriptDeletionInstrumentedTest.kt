package com.example.notify

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.notify.stt.TranscriptHistory
import com.example.notify.ui.theme.NotifyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.util.UUID

class TranscriptDeletionInstrumentedTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "am start -W -n ${instrumentation.targetContext.packageName}/androidx.activity.ComponentActivity"))
                .use { it.readBytes() }
        }
    }).around(compose)

    @Test fun confirmationDeletesOnlyTheChosenSavedText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "history-delete-${UUID.randomUUID()}").apply { mkdirs() }
        val source = File(directory, "source.wav").apply { writeText("original source fixture") }
        val history = TranscriptHistory(directory)
        val chosen = TranscriptHistory.Entry(UUID.randomUUID().toString(), "source.wav", "Transcript", 1, true)
        val other = TranscriptHistory.Entry(UUID.randomUUID().toString(), "other.wav", "Keep me", 2, false)
        try {
            history.save(chosen)
            history.save(other)
            var opened = false
            compose.setContent {
                NotifyTheme {
                    SavedTranscriptCard(chosen, onOpen = { opened = true },
                        onDelete = { assertTrue(history.delete(chosen.id)) })
                }
            }
            compose.onNodeWithContentDescription("Delete transcript").performClick()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(2, history.list().size)
            assertFalse(opened)
            compose.onNodeWithContentDescription("Delete transcript").performClick()
            compose.onNodeWithText("Delete").performClick()
            compose.runOnIdle {
                val remaining = TranscriptHistory(directory).list()
                assertEquals(listOf(other.id), remaining.map { it.id })
                assertEquals("original source fixture", source.readText())
            }
        } finally { directory.deleteRecursively() }
    }
}
