package com.example.notify;

import com.example.notify.stt.PcmWavReader;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class PcmWavReaderTest {
    @Test
    public void readsLittleEndianSamples() throws IOException {
        float[] audio = PcmWavReader.read(new ByteArrayInputStream(wav(1, 16000, (short) -32768, (short) 16384)));
        assertArrayEquals(new float[]{-1.0f, 0.5f}, audio, 0.0001f);
    }

    @Test
    public void readsBundledExampleRecording() throws IOException {
        String path = "src/main/assets/sherpa-onnx-nemo-transducer-punct-giga-am-v3-russian-2025-12-16/test_wavs/example.wav";
        try (FileInputStream input = new FileInputStream(path)) {
            assertEquals(180640, PcmWavReader.read(input).length);
        }
    }

    @Test
    public void streamsBundledExampleRecording() throws IOException {
        String path = "src/main/assets/sherpa-onnx-nemo-transducer-punct-giga-am-v3-russian-2025-12-16/test_wavs/example.wav";
        long[] count = {0};
        try (FileInputStream input = new FileInputStream(path)) {
            PcmWavReader.stream(input, chunk -> {
                if (chunk.length > 8192) throw new AssertionError("WAV chunk is too large");
                count[0] += chunk.length;
            });
        }
        assertEquals(180640, count[0]);
    }

    @Test
    public void rejectsUnsupportedSampleRate() throws IOException {
        IOException error = assertThrows(IOException.class,
                () -> PcmWavReader.read(new ByteArrayInputStream(wav(1, 44100, (short) 100))));
        assertEquals("Use a 16 kHz mono, 16-bit PCM WAV file", error.getMessage());
    }

    private static byte[] wav(int channels, int sampleRate, short... samples) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeBytes("RIFF");
        output.writeInt(Integer.reverseBytes(36 + samples.length * 2));
        output.writeBytes("WAVEfmt ");
        output.writeInt(Integer.reverseBytes(16));
        output.writeShort(Short.reverseBytes((short) 1));
        output.writeShort(Short.reverseBytes((short) channels));
        output.writeInt(Integer.reverseBytes(sampleRate));
        output.writeInt(Integer.reverseBytes(sampleRate * channels * 2));
        output.writeShort(Short.reverseBytes((short) (channels * 2)));
        output.writeShort(Short.reverseBytes((short) 16));
        output.writeBytes("data");
        output.writeInt(Integer.reverseBytes(samples.length * 2));
        for (short sample : samples) output.writeShort(Short.reverseBytes(sample));
        return bytes.toByteArray();
    }
}
