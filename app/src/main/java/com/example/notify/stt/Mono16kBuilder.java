package com.example.notify.stt;

import java.io.IOException;
import java.util.Arrays;

/** Converts a stream of mono samples to the recognizer's 16 kHz input. */
public final class Mono16kBuilder {
    private static final int TARGET_RATE = 16000;

    @FunctionalInterface
    public interface ChunkConsumer {
        void accept(float[] samples) throws IOException;
    }

    private final double inputFramesPerOutput;
    private final ChunkConsumer consumer;
    private float[] output = new float[8192];
    private int size;
    private long inputFrame;
    private double nextOutputFrame;
    private float previous;

    public Mono16kBuilder(int inputRate) {
        this(inputRate, null);
    }

    public Mono16kBuilder(int inputRate, ChunkConsumer consumer) {
        if (inputRate <= 0) throw new IllegalArgumentException("Invalid audio sample rate");
        inputFramesPerOutput = inputRate / (double) TARGET_RATE;
        this.consumer = consumer;
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
        if (consumer != null) throw new IllegalStateException("Streaming audio has no single output array");
        if (size == 0) throw new IOException("No audio samples found in the file");
        return Arrays.copyOf(output, size);
    }

    public void finish() throws IOException {
        if (inputFrame == 0) throw new IOException("No audio samples found in the file");
        if (consumer != null && size > 0) {
            consumer.accept(Arrays.copyOf(output, size));
            size = 0;
        }
    }

    private void append(float sample) throws IOException {
        if (size == output.length) {
            if (consumer != null) {
                consumer.accept(output);
                output = new float[8192];
                size = 0;
            } else {
                output = Arrays.copyOf(output, output.length * 2);
            }
        }
        output[size++] = sample;
    }
}
