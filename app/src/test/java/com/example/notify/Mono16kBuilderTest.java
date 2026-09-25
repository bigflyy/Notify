package com.example.notify;

import com.example.notify.stt.Mono16kBuilder;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class Mono16kBuilderTest {
    @Test
    public void upsamplesEightKhzAudio() throws IOException {
        Mono16kBuilder audio = new Mono16kBuilder(8000);
        audio.addFrame(0f);
        audio.addFrame(1f);
        assertArrayEquals(new float[]{0f, 0.5f, 1f}, audio.toArray(), 0.0001f);
    }

    @Test
    public void downsamplesFortyEightKhzAudio() throws IOException {
        Mono16kBuilder audio = new Mono16kBuilder(48000);
        for (int i = 0; i < 7; i++) audio.addFrame(i / 6f);
        assertArrayEquals(new float[]{0f, 0.5f, 1f}, audio.toArray(), 0.0001f);
    }

    @Test
    public void preservesOneSecondAtFortyFourPointOneKhz() throws IOException {
        Mono16kBuilder audio = new Mono16kBuilder(44100);
        for (int i = 0; i < 44100; i++) audio.addFrame(0.25f);
        float[] result = audio.toArray();
        assertEquals(16000, result.length);
        assertEquals(0.25f, result[15999], 0.0001f);
    }
}
