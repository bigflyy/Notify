package com.example.notify;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.notify.stt.AndroidAudioDecoder;
import com.example.notify.stt.FfmpegAudioDecoder;
import com.example.notify.stt.SpeechTranscriber;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(AndroidJUnit4.class)
public class FfmpegAudioDecoderInstrumentedTest {
    @Test public void rejectsInvalidMediaWithoutWaitingForAPipeWriter() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = new File(context.getCacheDir(), "invalid-media.txt");
        try (OutputStream output = new FileOutputStream(file)) {
            output.write("not an audio file".getBytes());
        }
        try {
            try {
                FfmpegAudioDecoder.stream(context, Uri.fromFile(file), samples -> { });
                fail("Invalid media must fail");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("could not decode"));
            }
        } finally {
            file.delete();
        }
    }

    @Test public void transcribesUnsupportedAiffAndReadsContentUri() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        File file = new File(context.getCacheDir(), "example.aiff");
        try (InputStream input = testContext.getAssets().open("example.aiff");
             OutputStream output = new FileOutputStream(file)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        try {
            try {
                AndroidAudioDecoder.stream(context, Uri.fromFile(file), samples -> { });
                fail("AIFF should require FFmpeg on this device");
            } catch (IOException expected) { }

            ContentResolver resolver = context.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, "notify-test-example.aiff");
            values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/aiff");
            Uri contentUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (contentUri == null) throw new IOException("Cannot create test media URI");
            try {
                try (InputStream input = testContext.getAssets().open("example.aiff");
                     OutputStream output = resolver.openOutputStream(contentUri)) {
                    if (output == null) throw new IOException("Cannot write test media URI");
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
                AtomicInteger sampleCount = new AtomicInteger();
                FfmpegAudioDecoder.stream(context, contentUri, samples -> {
                    assertTrue(samples.length <= 8192);
                    sampleCount.addAndGet(samples.length);
                });
                assertTrue(sampleCount.get() > 160000);

                SpeechTranscriber transcriber = new SpeechTranscriber(null);
                assertTrue(transcriber.init(context));
                try {
                    assertFalse(transcriber.transcribeMedia(context, contentUri).isEmpty());
                } finally {
                    transcriber.free();
                }
            } finally {
                resolver.delete(contentUri, null, null);
            }
        } finally {
            file.delete();
        }
    }
}
