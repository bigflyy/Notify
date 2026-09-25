package com.example.notify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.notify.stt.FfmpegAudioDecoder;
import com.example.notify.stt.AndroidAudioDecoder;
import com.example.notify.stt.MediaDurationReader;
import com.example.notify.stt.PcmWavReader;
import com.example.notify.stt.SherpaOnnxEngine;
import com.example.notify.stt.SpeechTranscriber;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class StreamingParityInstrumentedTest {
    @Test public void originalAndStreamingRecognitionMatchOnMp3() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context testContext = InstrumentationRegistry.getInstrumentation().getContext();
        File mp3 = new File(context.getCacheDir(), "parity-example.mp3");
        try (InputStream input = testContext.getAssets().open("example.mp3");
             OutputStream output = new FileOutputStream(mp3)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        SpeechTranscriber transcriber = new SpeechTranscriber(null);
        try {
            assertTrue(transcriber.init(context));
            List<float[]> chunks = new ArrayList<>();
            AndroidAudioDecoder.stream(context, Uri.fromFile(mp3), chunks::add);
            int length = 0;
            for (float[] chunk : chunks) length += chunk.length;
            float[] oneArray = new float[length];
            int position = 0;
            for (float[] chunk : chunks) {
                System.arraycopy(chunk, 0, oneArray, position, chunk.length);
                position += chunk.length;
            }
            String original = transcriber.transcribeSamples(oneArray);
            String streaming = transcriber.transcribeMedia(context, Uri.fromFile(mp3));
            assertFalse(original.isEmpty());
            assertEquals(original, streaming);
        } finally {
            transcriber.free();
            mp3.delete();
        }
    }

    @Test public void originalAndStreamingRecognitionMatchOnRussianWav() throws IOException {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File wav = new File(context.getCacheDir(), "parity-example.wav");
        try (InputStream input = context.getAssets().open(
                "sherpa-onnx-nemo-transducer-punct-giga-am-v3-russian-2025-12-16/test_wavs/example.wav");
             OutputStream output = new FileOutputStream(wav)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        SherpaOnnxEngine engine = new SherpaOnnxEngine();
        SpeechTranscriber transcriber = new SpeechTranscriber(engine);
        try {
            assertTrue(transcriber.init(context));
            float[] samples;
            try (InputStream input = new FileInputStream(wav)) {
                samples = PcmWavReader.read(input);
            }
            String original = transcriber.transcribeSamples(samples);
            List<Long> progress = new ArrayList<>();
            String streaming = transcriber.transcribeMedia(context, Uri.fromFile(wav), progress::add);
            assertFalse(original.isEmpty());
            assertEquals(original, streaming);
            assertFalse(progress.isEmpty());
            assertTrue(progress.get(progress.size() - 1) >= samples.length);
            try (SherpaOnnxEngine.StreamingSession session = engine.startStreaming(16000)) {
                FfmpegAudioDecoder.stream(context, Uri.fromFile(wav), session::accept);
                assertEquals(original, session.finish());
            }
            long duration = MediaDurationReader.readMillis(context, Uri.fromFile(wav));
            assertTrue(duration >= 11000 && duration <= 12000);
        } finally {
            transcriber.free();
            wav.delete();
        }
    }
}
