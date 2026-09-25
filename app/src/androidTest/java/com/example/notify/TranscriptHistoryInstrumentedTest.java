package com.example.notify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.os.SystemClock;

import androidx.lifecycle.ViewModelStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.notify.stt.TranscriptHistory;
import com.example.notify.ui.MainViewModel;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class TranscriptHistoryInstrumentedTest {
    @Test public void persistsPartialAndCompletedTextAcrossStoreInstances() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        TranscriptHistory first = new TranscriptHistory(target.getFilesDir());
        String id = UUID.randomUUID().toString();
        try {
            first.save(new TranscriptHistory.Entry(id, "lecture.wav", "Первый сегмент", 1234, false));
            TranscriptHistory reopened = new TranscriptHistory(target.getFilesDir());
            TranscriptHistory.Entry partial = reopened.list().stream()
                    .filter(entry -> entry.id.equals(id)).findFirst().orElseThrow(AssertionError::new);
            assertEquals("Первый сегмент", partial.text);
            assertFalse(partial.complete);

            reopened.save(new TranscriptHistory.Entry(id, "lecture.wav",
                    "Первый сегмент. Второй сегмент", 1234, true));
            TranscriptHistory.Entry complete = first.list().stream()
                    .filter(entry -> entry.id.equals(id)).findFirst().orElseThrow(AssertionError::new);
            assertEquals("Первый сегмент. Второй сегмент", complete.text);
            assertTrue(complete.complete);

            MainViewModel viewModel = new MainViewModel((Application) target.getApplicationContext());
            ViewModelStore store = new ViewModelStore();
            store.put("history-test", viewModel);
            try {
                long deadline = SystemClock.elapsedRealtime() + 20000;
                while (SystemClock.elapsedRealtime() < deadline && viewModel.getTranscriptionHistory()
                        .getValue().stream().noneMatch(entry -> entry.id.equals(id))) Thread.sleep(100);
                assertTrue(viewModel.getTranscriptionHistory().getValue().stream()
                        .anyMatch(entry -> entry.id.equals(id)));
                InstrumentationRegistry.getInstrumentation().runOnMainSync(
                        () -> viewModel.openSavedTranscript(id));
                assertEquals(complete.text, viewModel.getImportedTranscript().getValue());
            } finally {
                store.clear();
            }
        } finally {
            first.delete(id);
        }
    }
}
