package com.example.notify;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;

import androidx.lifecycle.ViewModelStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.notify.ui.MainViewModel;
import com.example.notify.stt.TranscriptHistory;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public class FileTranscriptionViewModelInstrumentedTest {
    @Test public void completedTranscriptIsSavedToHistory() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = new File(target.getCacheDir(), "complete-example.wav");
        try (InputStream input = target.getAssets().open(
                "sherpa-onnx-nemo-transducer-punct-giga-am-v3-russian-2025-12-16/test_wavs/example.wav");
             OutputStream output = new FileOutputStream(file)) {
            byte[] bytes = new byte[8192];
            int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
        }
        MainViewModel viewModel = new MainViewModel((Application) target.getApplicationContext());
        ViewModelStore store = new ViewModelStore();
        store.put("complete-test", viewModel);
        TranscriptHistory history = new TranscriptHistory(target.getFilesDir());
        String savedId = null;
        try {
            await(() -> Boolean.TRUE.equals(viewModel.getIsEngineReady().getValue()), 120000);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> viewModel.transcribeFile(Uri.fromFile(file), file.getName()));
            await(() -> !Boolean.TRUE.equals(viewModel.getIsImporting().getValue()), 60000);
            assertFalse(isBlank(viewModel.getImportedTranscript().getValue()));
            TranscriptHistory.Entry saved = history.list().stream()
                    .filter(entry -> file.getName().equals(entry.fileName))
                    .findFirst().orElseThrow(AssertionError::new);
            savedId = saved.id;
            assertTrue(saved.complete);
            assertEquals(viewModel.getImportedTranscript().getValue(), saved.text);
        } finally {
            store.clear();
            if (savedId != null) history.delete(savedId);
            file.delete();
        }
    }

    @Test public void cancelKeepsLivePartialTranscript() throws Exception {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context tests = InstrumentationRegistry.getInstrumentation().getContext();
        File file = new File(target.getCacheDir(), "partial-minute.wav");
        try (InputStream input = tests.getAssets().open("benchmark-minute.wav");
             OutputStream output = new FileOutputStream(file)) {
            byte[] bytes = new byte[8192];
            int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
        }
        MainViewModel viewModel = new MainViewModel((Application) target.getApplicationContext());
        ViewModelStore store = new ViewModelStore();
        store.put("file-test", viewModel);
        TranscriptHistory history = new TranscriptHistory(target.getFilesDir());
        String savedId = null;
        try {
            await(() -> Boolean.TRUE.equals(viewModel.getIsEngineReady().getValue()), 120000);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(
                    () -> viewModel.transcribeFile(Uri.fromFile(file), file.getName()));
            await(() -> !isBlank(viewModel.getImportedTranscript().getValue()), 60000);
            assertTrue(Boolean.TRUE.equals(viewModel.getIsImporting().getValue()));
            InstrumentationRegistry.getInstrumentation().runOnMainSync(viewModel::cancelFileTranscription);
            await(() -> !Boolean.TRUE.equals(viewModel.getIsImporting().getValue()), 30000);
            assertTrue(Boolean.TRUE.equals(viewModel.getImportWasCancelled().getValue()));
            assertFalse(isBlank(viewModel.getImportedTranscript().getValue()));
            TranscriptHistory.Entry saved = history.list().stream()
                    .filter(entry -> file.getName().equals(entry.fileName))
                    .findFirst().orElseThrow(AssertionError::new);
            savedId = saved.id;
            assertFalse(saved.complete);
            assertEquals(viewModel.getImportedTranscript().getValue(), saved.text);
        } finally {
            store.clear();
            if (savedId != null) history.delete(savedId);
            file.delete();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void await(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = SystemClock.elapsedRealtime() + timeoutMillis;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
        assertTrue("Timed out waiting for file transcription", condition.getAsBoolean());
    }
}
