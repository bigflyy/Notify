package com.example.notify;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.notify.stt.PcmWavReader;
import com.example.notify.utils.AssetUtils;
import com.k2fsa.sherpa.onnx.SileroVadModelConfig;
import com.k2fsa.sherpa.onnx.Vad;
import com.k2fsa.sherpa.onnx.VadModelConfig;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class LongVadParityInstrumentedTest {
    @Test public void drainingDuringTwoHoursDoesNotChangeSpeechSegments() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String asset = "sherpa-onnx-nemo-transducer-punct-giga-am-v3-russian-2025-12-16";
        String modelDir = context.getFilesDir().getAbsolutePath() + "/model";
        AssetUtils.copyAssets(context, asset, modelDir);
        float[] speech;
        try (InputStream input = context.getAssets().open(asset + "/test_wavs/example.wav")) {
            speech = PcmWavReader.read(input);
        }

        VadModelConfig config = new VadModelConfig();
        config.setSileroVadModelConfig(new SileroVadModelConfig(
                modelDir + "/silero_vad_v6_2.onnx", 0.5f, 2.0f, 0.25f, 512, 30.0f));
        config.setSampleRate(16000);
        config.setNumThreads(1);
        config.setDebug(false);
        Vad original = new Vad(null, config);
        Vad streaming = new Vad(null, config);
        try {
            List<float[]> originalSegments = new ArrayList<>();
            List<float[]> streamingSegments = new ArrayList<>();
            long totalSamples = 2L * 60 * 60 * 16000;
            long[] speechStarts = { 0, 30L * 60 * 16000 - 5 * 16000,
                    60L * 60 * 16000 - 5 * 16000, 90L * 60 * 16000 - 5 * 16000,
                    totalSamples - speech.length };
            for (long start = 0; start < totalSamples; start += 512) {
                float[] window = new float[512];
                for (long speechStart : speechStarts) {
                    long first = Math.max(start, speechStart);
                    long last = Math.min(start + 512, speechStart + speech.length);
                    if (first < last) {
                        System.arraycopy(speech, (int) (first - speechStart), window,
                                (int) (first - start), (int) (last - first));
                    }
                }
                original.acceptWaveform(window);
                streaming.acceptWaveform(window);
                drain(streaming, streamingSegments);
            }
            original.flush();
            streaming.flush();
            drain(original, originalSegments);
            drain(streaming, streamingSegments);
            assertFalse(originalSegments.isEmpty());
            assertEquals(originalSegments.size(), streamingSegments.size());
            for (int i = 0; i < originalSegments.size(); i++) {
                assertArrayEquals(originalSegments.get(i), streamingSegments.get(i), 0f);
            }
        } finally {
            original.release();
            streaming.release();
        }
    }

    private static void drain(Vad vad, List<float[]> segments) {
        while (!vad.empty()) {
            segments.add(vad.front().getSamples());
            vad.pop();
        }
    }
}
