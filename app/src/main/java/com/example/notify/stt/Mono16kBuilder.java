package com.example.notify.stt;

import java.io.IOException;
import java.util.Arrays;

/** Converts a stream of mono samples to the recognizer's 16 kHz input. */
public final class Mono16kBuilder {
    private static final int TARGET_RATE = 16000;
    private static final int MAX_SAMPLES = TARGET_RATE * 60 * 30;

    private final double inputFramesPerOutput;
    private float[] output = new float[8192];
    private int size;
    private long inputFrame;
    private double nextOutputFrame;
    private float previous;

    public Mono16kBuilder(int inputRate) {
        if (inputRate <= 0) throw new IllegalArgumentException("Invalid audio sample rate");
        inputFramesPerOutput = inputRate / (double) TARGET_RATE;
    }

    public void addFrame(float sample) throws IOException {
        if (inputFrame == 0) {
            append(sample);
            nextOutputFrame = inputFramesPerOutput;
        } else {
            while (nextOutputFrame <= inputFrame) {
                double fraction = nextOutputFrame - (inputFrame - 1);
                append((float) (previous + (sample - previous) * fraction));
                nextOutputFrame += inputFramesPerOutput;
            }
        }
        previous = sample;
        inputFrame++;
    }

    public float[] toArray() throws IOException {
        if (size == 0) throw new IOException("No audio samples found in the file");
        return Arrays.copyOf(output, size);
    }

    private void append(float sample) throws IOException {
        if (size == MAX_SAMPLES) throw new IOException("Audio longer than 30 minutes is not supported");
        if (size == output.length) output = Arrays.copyOf(output, Math.min(MAX_SAMPLES, output.length * 2));
        output[size++] = sample;
    }
}
