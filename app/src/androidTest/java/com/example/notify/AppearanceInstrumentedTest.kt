package com.example.notify

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.notify.domain.*
import com.example.notify.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import java.io.File
import java.util.Date

class AppearanceInstrumentedTest {
    private val compose = createComposeRule()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val command = "am start -W -n ${instrumentation.targetContext.packageName}/androidx.activity.ComponentActivity"
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
                .use { it.readBytes() }
        }
    }).around(compose)

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun lightAndDarkChoicePersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = readThemeMode(context)
        val note = Note(1, "Ideas for the week", Date()).apply {
            addTag(Tag(1, "Personal"))
            addEntry(TextEntry(1, 0, Date(), "Make room for the things that matter.\n\nA few thoughts from today's walk."))
            addEntry(AudioEntry(2, 1, Date(), "preview.pcm", "Записать идеи и вернуться к ним вечером.", 42.0))
        }
        try {
            compose.setContent {
                var mode by remember { mutableStateOf(ThemeMode.LIGHT) }
                NotifyTheme(mode) {
                    Scaffold(topBar = {
                        TopAppBar(title = { Text("Notify") }, actions = {
                            AppearanceMenu(mode) { mode = it; saveThemeMode(context, it) }
                        })
                    }) { padding ->
                        Box(Modifier.padding(padding)) {
                            NoteEditor(note, false, true, null, 0, 0,
                                { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, { _, _ -> },
                                { _, _ -> }, {}, {}, {}, {}, {}, note.tags, {})
                        }
                    }
                }
            }
            for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)) {
                compose.onNodeWithContentDescription("Appearance").performClick()
                compose.onNodeWithText(mode.label).performClick()
                compose.runOnIdle { assertEquals(mode, readThemeMode(context)) }
                if (mode != ThemeMode.SYSTEM) {
                    val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
                    File(context.cacheDir, "appearance-${mode.name}.png").outputStream().use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        } finally { saveThemeMode(context, previous) }
    }
}
