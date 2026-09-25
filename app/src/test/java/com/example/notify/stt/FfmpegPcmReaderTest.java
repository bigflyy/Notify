package com.example.notify.stt;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class FfmpegPcmReaderTest {
    @Test public void streamsPartialFramesAndBoundsChunkSize() throws IOException {
        ByteBuffer data = ByteBuffer.allocate(8200 * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < 8200; i++) data.putFloat(i == 3 ? Float.NaN : i / 8200f);
        InputStream input = new ByteArrayInputStream(data.array()) {
            @Override public synchronized int read(byte[] bytes, int offset, int length) {
                return super.read(bytes, offset, Math.min(length, 5));
            }
        };
        List<float[]> chunks = new ArrayList<>();
        FfmpegPcmReader.stream(input, () -> true, chunks::add);

        assertEquals(2, chunks.size());
        assertEquals(8192, chunks.get(0).length);
        assertEquals(8, chunks.get(1).length);
        assertEquals(0f, chunks.get(0)[3], 0f);
        assertEquals(8199 / 8200f, chunks.get(1)[7], 0f);
    }
}
