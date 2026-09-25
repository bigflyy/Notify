package com.example.notify.stt;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Reads the simple WAV format accepted by the current Russian speech model. */
public final class PcmWavReader {
    private PcmWavReader() { }

    public static float[] read(InputStream source) throws IOException {
        List<float[]> chunks = new ArrayList<>();
        stream(source, chunks::add);
        long sampleCount = 0;
        for (float[] chunk : chunks) sampleCount += chunk.length;
        if (sampleCount > Integer.MAX_VALUE) throw new IOException("WAV is too large for one audio array");
        float[] audio = new float[(int) sampleCount];
        int position = 0;
        for (float[] chunk : chunks) {
            System.arraycopy(chunk, 0, audio, position, chunk.length);
            position += chunk.length;
        }
        return audio;
    }

    public static void stream(InputStream source, Mono16kBuilder.ChunkConsumer consumer) throws IOException {
        DataInputStream input = new DataInputStream(new BufferedInputStream(source));
        if (!"RIFF".equals(readId(input))) {
            throw new IOException("The file is not a WAV recording");
        }
        readInt(input); // RIFF size
        if (!"WAVE".equals(readId(input))) {
            throw new IOException("The file is not a WAV recording");
        }

        boolean hasFormat = false;
        while (true) {
            String chunkId;
            try {
                chunkId = readId(input);
            } catch (EOFException e) {
                throw new IOException("The WAV file has no audio data", e);
            }
            long chunkSize = readInt(input) & 0xffffffffL;
            if ("fmt ".equals(chunkId)) {
                if (chunkSize < 16) throw new IOException("Invalid WAV format header");
                int encoding = readShort(input);
                int channels = readShort(input);
                long sampleRate = readInt(input) & 0xffffffffL;
                readInt(input); // byte rate
                readShort(input); // block alignment
                int bitDepth = readShort(input);
                skip(input, chunkSize - 16);
                if (encoding != 1 || channels != 1 || sampleRate != 16000 || bitDepth != 16) {
                    throw new IOException("Use a 16 kHz mono, 16-bit PCM WAV file");
                }
                hasFormat = true;
            } else if ("data".equals(chunkId)) {
                if (!hasFormat) throw new IOException("Invalid WAV file: format header is missing");
                if (chunkSize == 0 || chunkSize % 2 != 0) {
                    throw new IOException("The WAV audio is empty or invalid");
                }
                long remaining = chunkSize / 2;
                while (remaining > 0) {
                    int count = (int) Math.min(remaining, 8192);
                    float[] audio = new float[count];
                    for (int i = 0; i < count; i++) {
                        int low = input.readUnsignedByte();
                        int high = input.readUnsignedByte();
                        audio[i] = (short) (low | (high << 8)) / 32768.0f;
                    }
                    consumer.accept(audio);
                    remaining -= count;
                }
                return;
            } else {
                skip(input, chunkSize);
            }
            if ((chunkSize & 1) != 0) skip(input, 1);
        }
    }

    private static String readId(DataInputStream input) throws IOException {
        byte[] id = new byte[4];
        input.readFully(id);
        return new String(id, StandardCharsets.US_ASCII);
    }

    private static int readShort(DataInputStream input) throws IOException {
        return Short.reverseBytes(input.readShort()) & 0xffff;
    }

    private static int readInt(DataInputStream input) throws IOException {
        return Integer.reverseBytes(input.readInt());
    }

    private static void skip(DataInputStream input, long count) throws IOException {
        while (count > 0) {
            int skipped = input.skipBytes((int) Math.min(count, Integer.MAX_VALUE));
            if (skipped == 0) {
                if (input.read() == -1) throw new EOFException("Truncated WAV file");
                skipped = 1;
            }
            count -= skipped;
        }
    }
}
