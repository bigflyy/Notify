package com.example.notify;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;

import androidx.lifecycle.ViewModelStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.notify.ui.MainViewModel;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public class FileTranscriptionViewModelInstrumentedTest {
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
        } finally {
            store.clear();
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
