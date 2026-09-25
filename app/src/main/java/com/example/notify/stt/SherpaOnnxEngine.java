package com.example.notify.stt;

import android.content.Context;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.SpeechSegment;
import com.k2fsa.sherpa.onnx.Vad;
import com.k2fsa.sherpa.onnx.VadModelConfig;
import com.k2fsa.sherpa.onnx.SileroVadModelConfig;

import android.util.Log;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SherpaOnnxEngine {
    // Распознователь речи
    private OfflineRecognizer recognizer;
    // Настройки модели обнаружение голосовой активности
    private VadModelConfig vadConfig;
    // Размер окна модели обнаружения голосовой активности. Влияет на точность и нагрузку на CPU
    private final int windowSize = 512;
    // Тэг для логирования
    private static final String TAG = "SherpaOnnxEngine";

    public boolean init(Context context, String modelDir) {
        try {
            Log.d(TAG, "Initializing SherpaOnnxEngine with modelDir: " + modelDir);
            
            // Check if model files exist
            String[] requiredFiles = {
                "/encoder.int8.onnx",
                "/decoder.onnx",
                "/joiner.onnx",
                "/tokens.txt",
                "/silero_vad_v6_2.onnx"
            };
            
            for (String file : requiredFiles) {
                File f = new File(modelDir + file);
                if (!f.exists()) {
                    Log.e(TAG, "Missing model file: " + f.getAbsolutePath());
                    return false;
                }
            }

            OfflineRecognizerConfig config = new OfflineRecognizerConfig();
            
            // Transducer Config
            config.getModelConfig().getTransducer().setEncoder(modelDir + "/encoder.int8.onnx");
            config.getModelConfig().getTransducer().setDecoder(modelDir + "/decoder.onnx");
            config.getModelConfig().getTransducer().setJoiner(modelDir + "/joiner.onnx");
            config.getModelConfig().setTokens(modelDir + "/tokens.txt");
            config.getModelConfig().setNumThreads(4);
            config.getModelConfig().setDebug(false);

            recognizer = new OfflineRecognizer(null, config);

            // VAD Config
            SileroVadModelConfig sileroConfig = new SileroVadModelConfig(
                    modelDir + "/silero_vad_v6_2.onnx",
                    0.5f,  // threshold
                    2.0f,  // minSilenceDuration
                    0.25f, // minSpeechDuration
                    windowSize,
                    30.0f  // maxSpeechDuration
            );

            vadConfig = new VadModelConfig();
            vadConfig.setSileroVadModelConfig(sileroConfig);
            vadConfig.setSampleRate(16000);
            vadConfig.setNumThreads(1);
            vadConfig.setDebug(false);
            
            Log.d(TAG, "SherpaOnnxEngine initialized successfully");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error initializing SherpaOnnxEngine", e);
            return false;
        }
    }

    public String transcribe(float[] audio, int sampleRate) {
        if (recognizer == null) return "Engine not initialized";

        Vad vad = new Vad(null, vadConfig);
        for (int i = 0; i + windowSize <= audio.length; i += windowSize) {
            float[] window = Arrays.copyOfRange(audio, i, i + windowSize);
            vad.acceptWaveform(window);
        }
        int remaining = audio.length % windowSize;
        if (remaining > 0) {
            float[] lastWindow = Arrays.copyOfRange(audio, audio.length - remaining, audio.length);
            vad.acceptWaveform(lastWindow);
        }
        vad.flush();

        StringBuilder resultText = new StringBuilder();
        while (!vad.empty()) {
            SpeechSegment segment = vad.front();
            float[] segmentSamples = segment.getSamples();

            OfflineStream stream = recognizer.createStream();
            stream.acceptWaveform(segmentSamples, sampleRate);
            recognizer.decode(stream);

            String text = recognizer.getResult(stream).getText();
            if (!text.isEmpty()) {
                resultText.append(text).append(" ");
            }

            stream.release();
            vad.pop();
        }
        vad.release();
        return resultText.toString().trim();
    }

    public StreamingSession startStreaming(int sampleRate) {
        return startStreaming(sampleRate, text -> { }, () -> false);
    }

    public StreamingSession startStreaming(int sampleRate, Consumer<String> onSegment,
                                           BooleanSupplier isCancelled) {
        if (recognizer == null) throw new IllegalStateException("Engine not initialized");
        return new StreamingSession(sampleRate, onSegment, isCancelled);
    }

    /** Keeps VAD state across decoder chunks, so a word can cross a chunk boundary. */
    public final class StreamingSession implements AutoCloseable {
        private final Vad vad = new Vad(null, vadConfig);
        private final int sampleRate;
        private final float[] window = new float[windowSize];
        private final StringBuilder resultText = new StringBuilder();
        private final Consumer<String> onSegment;
        private final BooleanSupplier isCancelled;
        private int windowLength;
        private boolean closed;

        private StreamingSession(int sampleRate, Consumer<String> onSegment,
                                 BooleanSupplier isCancelled) {
            this.sampleRate = sampleRate;
            this.onSegment = onSegment;
            this.isCancelled = isCancelled;
        }

        private void checkCancelled() {
            if (isCancelled.getAsBoolean()) throw new CancellationException("Transcription cancelled");
        }

        public void accept(float[] audio) {
            if (closed) throw new IllegalStateException("Transcription session is closed");
            checkCancelled();
            int position = 0;
            while (position < audio.length) {
                checkCancelled();
                int count = Math.min(windowSize - windowLength, audio.length - position);
                System.arraycopy(audio, position, window, windowLength, count);
                windowLength += count;
                position += count;
                if (windowLength == windowSize) {
                    vad.acceptWaveform(Arrays.copyOf(window, windowSize));
                    windowLength = 0;
                    drainSegments();
                }
            }
        }

        public String finish() {
            if (closed) throw new IllegalStateException("Transcription session is closed");
            checkCancelled();
            if (windowLength > 0) {
                vad.acceptWaveform(Arrays.copyOf(window, windowLength));
                windowLength = 0;
            }
            vad.flush();
            drainSegments();
            checkCancelled();
            return resultText.toString().trim();
        }

        private void drainSegments() {
            while (!vad.empty()) {
                checkCancelled();
                SpeechSegment segment = vad.front();
                OfflineStream stream = recognizer.createStream();
                try {
                    stream.acceptWaveform(segment.getSamples(), sampleRate);
                    recognizer.decode(stream);
                    checkCancelled();
                    String text = recognizer.getResult(stream).getText();
                    if (!text.isEmpty()) {
                        resultText.append(text).append(' ');
                        onSegment.accept(text);
                    }
                } finally {
                    stream.release();
                    vad.pop();
                }
            }
        }

        @Override public void close() {
            if (!closed) {
                vad.release();
                closed = true;
            }
        }
    }

    public void free() {
        if (recognizer != null) {
            recognizer.release();
            recognizer = null;
        }
    }
}
