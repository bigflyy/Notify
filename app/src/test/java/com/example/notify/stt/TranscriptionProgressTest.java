package com.example.notify.stt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TranscriptionProgressTest {
    @Test public void estimatesFromProcessedAudioAndElapsedTime() {
        TranscriptionProgress progress = TranscriptionProgress.calculate(30L * 16000, 120000, 10000);
        assertEquals(Integer.valueOf(25), progress.percent);
        assertEquals(Long.valueOf(30), progress.secondsRemaining);
        assertEquals(30, progress.secondsProcessed);
    }

    @Test public void doesNotInventPercentageForUnknownDuration() {
        TranscriptionProgress progress = TranscriptionProgress.calculate(30L * 16000, 0, 10000);
        assertNull(progress.percent);
        assertNull(progress.secondsRemaining);
        assertEquals(30, progress.secondsProcessed);
    }

    @Test public void waitsForEnoughDataAndLeavesCompletionToTheResult() {
        assertNull(TranscriptionProgress.calculate(2L * 16000, 120000, 1000).secondsRemaining);
        assertEquals(Integer.valueOf(99),
                TranscriptionProgress.calculate(121L * 16000, 120000, 10000).percent);
    }

    @Test public void tracksTwoHourFilesWithoutACap() {
        TranscriptionProgress progress = TranscriptionProgress.calculate(
                60L * 60 * 16000, 2L * 60 * 60 * 1000, 20L * 60 * 1000);
        assertEquals(Integer.valueOf(50), progress.percent);
        assertEquals(Long.valueOf(20L * 60), progress.secondsRemaining);
    }
}
