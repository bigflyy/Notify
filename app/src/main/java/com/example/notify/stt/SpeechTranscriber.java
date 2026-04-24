package com.example.notify.stt;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.example.notify.utils.AssetUtils;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SpeechTranscriber {
    private static final int SAMPLE_RATE = 16000;
    // Движок распознования речи
    private final SherpaOnnxEngine sttEngine;
    // Системный класс для доступа к микорофону и записи аудио
    private AudioRecord audioRecord;
    private boolean isRecording = false;
    private Thread recordingThread;
    private String lastAudioPath;

    public SpeechTranscriber(SherpaOnnxEngine sttEngine) {
        if (sttEngine != null) {
            this.sttEngine = sttEngine;
        }
        else
        {
            this.sttEngine = new SherpaOnnxEngine();
        }
    }
    public boolean init(Context context){
        String modelDir = context.getFilesDir().getAbsolutePath() + "/model";
        try {
            AssetUtils.copyAssets(context, "sherpa-onnx-nemo-transducer-punct-giga-am-v3-russian-2025-12-16", modelDir);
            return sttEngine.init(context, modelDir);
        } catch (IOException e) {
            Log.e("MainViewModel", "Error loading model", e);
            return false;
        }
    }
    public void free(){
        sttEngine.free();
    }
    @SuppressLint("MissingPermission")
    public void startRecording(String outputFilePath) {
        this.lastAudioPath = outputFilePath;
        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, 
                AudioFormat.CHANNEL_IN_MONO, 
                AudioFormat.ENCODING_PCM_16BIT);
        
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 
                SAMPLE_RATE, 
                AudioFormat.CHANNEL_IN_MONO, 
                AudioFormat.ENCODING_PCM_16BIT, 
                bufferSize);

        audioRecord.startRecording();
        isRecording = true;
        audioData.clear();

        recordingThread = new Thread(() -> {
            short[] buffer = new short[bufferSize];
            try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(outputFilePath))) {
                while (isRecording) {
                    int read = audioRecord.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        for (int i = 0; i < read; i++) {
                            short s = buffer[i];
                            // convert to float [-1, 1] as required by model
                            audioData.add(s / 32768.0f);
                            // Write Little-Endian (low byte first)
                            // we want to transform 16 bit short in two separate bytes
                            // first the last 8 bits
                            // 0xFF is a mask like 00000000 11111111
                            dos.writeByte(s & 0xFF);
                            // we shift 8 bits to the right and do the same mask
                            dos.writeByte((s >> 8) & 0xFF);
                        }
                    }
                }
            } catch (IOException e) {
                Log.e("SpeechTranscriber", "Error writing audio file", e);
            }
        });
        recordingThread.start();
    }

    public String getLastAudioPath() {
        return lastAudioPath;
    }

    public String stopRecording() {
        isRecording = false;
        try {
            if (recordingThread != null) {
                recordingThread.join();
            }
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
        } catch (InterruptedException e) {
            Log.e("SpeechTranscriber", "Error stopping recording", e);
        }

        if (audioData.isEmpty()) {
            return "";
        }

        float[] audioArray = new float[audioData.size()];
        for (int i = 0; i < audioData.size(); i++) {
            audioArray[i] = audioData.get(i);
        }
        audioData.clear();

        return sttEngine.transcribe(audioArray, SAMPLE_RATE);
    }

    private final List<Float> audioData = new ArrayList<>();
}
