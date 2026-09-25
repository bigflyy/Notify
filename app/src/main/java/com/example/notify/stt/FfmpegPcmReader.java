package com.example.notify.stt;

import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.function.BooleanSupplier;

/** Reads a nonblocking FFmpeg pipe without storing the converted file. */
final class FfmpegPcmReader {
    private static final int CHUNK_SAMPLES = 8192;

    private FfmpegPcmReader() { }

    static void stream(FileDescriptor pipe, BooleanSupplier conversionFinished,
                       Mono16kBuilder.ChunkConsumer consumer) throws IOException {
        stream(new InputStream() {
            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) == -1 ? -1 : one[0] & 0xff;
            }

            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                try {
                    return Os.read(pipe, bytes, offset, length);
                } catch (ErrnoException error) {
                    if (error.errno == OsConstants.EAGAIN) return 0;
                    throw new IOException("Cannot read converted audio", error);
                }
            }
        }, conversionFinished, consumer);
    }

    static void stream(InputStream input, BooleanSupplier conversionFinished,
                       Mono16kBuilder.ChunkConsumer consumer) throws IOException {
        byte[] bytes = new byte[CHUNK_SAMPLES * 4];
        float[] samples = new float[CHUNK_SAMPLES];
        int pending = 0;
        int count = 0;
        while (true) {
            int size = input.read(bytes, pending, bytes.length - pending);
            if (size <= 0) {
                if (conversionFinished.getAsBoolean()) break;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Audio conversion interrupted", error);
                }
                continue;
            }
            int limit = pending + size;
            int position = 0;
            while (position + 4 <= limit) {
                int bits = (bytes[position] & 0xff) | ((bytes[position + 1] & 0xff) << 8)
                        | ((bytes[position + 2] & 0xff) << 16) | (bytes[position + 3] << 24);
                float sample = Float.intBitsToFloat(bits);
                samples[count++] = Float.isFinite(sample) ? sample : 0f;
                position += 4;
                if (count == samples.length) {
                    consumer.accept(samples);
                    samples = new float[CHUNK_SAMPLES];
                    count = 0;
                }
            }
            pending = limit - position;
            System.arraycopy(bytes, position, bytes, 0, pending);
        }
        if (pending != 0) throw new IOException("Incomplete converted audio sample");
        if (count > 0) consumer.accept(Arrays.copyOf(samples, count));
    }
}
