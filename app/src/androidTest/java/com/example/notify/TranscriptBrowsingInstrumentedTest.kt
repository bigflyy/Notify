package com.example.notify

import android.content.ClipboardManager
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import com.example.notify.ui.MainViewModel
import androidx.test.platform.app.InstrumentationRegistry
import com.example.notify.stt.TranscriptHistory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.util.UUID
import java.io.File
import android.graphics.Bitmap

/** Build with -PisolatedUiTests=true so fixture history never touches the user's installation. */
class TranscriptBrowsingInstrumentedTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val first = TranscriptHistory.Entry(UUID.randomUUID().toString(), "Lecture.wav",
        "Это сохранённая расшифровка лекции.", 1, true)
    private val second = TranscriptHistory.Entry(UUID.randomUUID().toString(), "Interview.mp3",
        "Другой текст, который нужно сохранить.", 2, false)

    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            // Fixtures belong only to the separate test installation.
            check(context.packageName.endsWith(".codextest"))
            TranscriptHistory(context.filesDir).apply { save(first); save(second) }
            fun shell(command: String) {
                ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
                    .use { it.readBytes() }
            }
            shell("pm grant ${context.packageName} android.permission.RECORD_AUDIO")
            shell("am start -W -n ${context.packageName}/androidx.activity.ComponentActivity")
        }

        override fun after() {
            TranscriptHistory(InstrumentationRegistry.getInstrumentation().targetContext.filesDir).apply {
                delete(first.id)
                delete(second.id)
            }
        }
    }).around(compose)

    @Test fun browseReturnExportAndConfirmDeletion() {
        compose.onNodeWithText("Transcripts").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText(first.fileName).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Your transcripts").assertIsDisplayed()
        screenshot("transcript-list.png")
        compose.onNodeWithText(first.fileName).performClick()
        compose.onNodeWithText(first.text).assertIsDisplayed()
        screenshot("transcript-detail.png")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(first.text).assertIsDisplayed()
        compose.onNodeWithText("Your transcripts").assertDoesNotExist()
        compose.onNodeWithText("Notes").assertDoesNotExist()
        compose.onNodeWithContentDescription("Export transcript").performClick()
        compose.onNodeWithText("Save text file").assertIsDisplayed()
        compose.onNodeWithText("Save Markdown file").assertIsDisplayed()
        compose.onNodeWithText("Copy as Markdown").performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("# Lecture\n\n${first.text}\n", clipboard.primaryClip!!.getItemAt(0).text.toString())
        }
        compose.onNodeWithContentDescription("Back to transcripts").performClick()
        compose.onNodeWithText("Your transcripts").assertIsDisplayed()
        compose.onNodeWithText(second.fileName).performClick()
        compose.onNodeWithText(second.text).assertIsDisplayed()
        compose.onNodeWithText("Partial transcript").assertIsDisplayed()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Your transcripts").assertIsDisplayed()
        compose.onNode(hasContentDescription("Delete transcript") and hasAnyAncestor(hasText(first.fileName)))
            .performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText(first.fileName).assertIsDisplayed()
        compose.onNode(hasContentDescription("Delete transcript") and hasAnyAncestor(hasText(first.fileName)))
            .performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText(first.fileName).fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText(second.fileName).assertIsDisplayed()

        // Simulate an active job to verify browsing never replaces its live text.
        lateinit var viewModel: MainViewModel
        compose.runOnIdle {
            viewModel = ViewModelProvider(compose.activity)[MainViewModel::class.java]
            (viewModel.importedFileName as MutableLiveData).value = "In progress.wav"
            (viewModel.importedTranscript as MutableLiveData).value = "Live result"
            (viewModel.isImporting as MutableLiveData).value = true
        }
        compose.onNodeWithContentDescription("Delete transcript").assertIsNotEnabled()
        compose.onNodeWithText(second.fileName).performClick()
        compose.onNodeWithText(second.text).assertIsDisplayed()
        compose.onNodeWithText("Live result").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back to transcripts").performClick()
        compose.onNodeWithText("In progress.wav").performClick()
        compose.onNodeWithText("Live result").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsEnabled()
        compose.runOnIdle {
            assertEquals("Live result", viewModel.importedTranscript.value)
            (viewModel.isImporting as MutableLiveData).value = false
        }
    }

    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(compose.activity.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
