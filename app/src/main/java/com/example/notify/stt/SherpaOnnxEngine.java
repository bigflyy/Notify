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

        // Create VAD instance
        Vad vad = new Vad(null, vadConfig);
        
        // Window-based processing
        for (int i = 0; i + windowSize <= audio.length; i += windowSize) {
            float[] window = Arrays.copyOfRange(audio, i, i + windowSize);
            vad.acceptWaveform(window);
        }
        
        // Handle the last partial window if it exists
        int remaining = audio.length % windowSize;
        if (remaining > 0) {
            float[] lastWindow = Arrays.copyOfRange(audio, audio.length - remaining, audio.length);
            vad.acceptWaveform(lastWindow);
        }

        vad.flush();

        StringBuilder resultText = new StringBuilder();

        // Iterate through detected segments
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

    public void free() {
        if (recognizer != null) {
            recognizer.release();
            recognizer = null;
        }
    }
}
