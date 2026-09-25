package com.example.notify;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.notify.stt.SpeechTranscriber;
import com.example.notify.stt.PcmWavReader;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.OutputStream;

@RunWith(AndroidJUnit4.class)
public class OneMinuteParityInstrumentedTest {
    @Test public void originalAndStreamingMatchOnOneMinuteWav() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        File file = new File(context.getCacheDir(), "benchmark-minute.wav");
        try (InputStream input = testContext.getAssets().open("benchmark-minute.wav");
             OutputStream output = new FileOutputStream(file)) {
            byte[] bytes = new byte[8192];
            int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
        }
        SpeechTranscriber transcriber = new SpeechTranscriber(null);
        try {
            assertTrue(transcriber.init(context));
            float[] samples;
            try (InputStream input = new FileInputStream(file)) {
                samples = PcmWavReader.read(input);
            }
            long originalStart = SystemClock.elapsedRealtime();
            String original = transcriber.transcribeSamples(samples);
            long originalMillis = SystemClock.elapsedRealtime() - originalStart;
            long streamingStart = SystemClock.elapsedRealtime();
            String result = transcriber.transcribeMedia(context, Uri.fromFile(file));
            long streamingMillis = SystemClock.elapsedRealtime() - streamingStart;
            assertFalse(result.isEmpty());
            assertEquals(original, result);
            try (PrintWriter metrics = new PrintWriter(
                    new File(context.getCacheDir(), "benchmark-result.txt"))) {
                metrics.println("audio_ms=56450 original_ms=" + originalMillis
                        + " streaming_ms=" + streamingMillis);
            }
        } finally {
            transcriber.free();
            file.delete();
        }
    }
}
