package com.example.notify.stt;

/** Progress based on audio accepted by recognition; remaining time is an estimate. */
public final class TranscriptionProgress {
    private static final long SAMPLE_RATE = 16000;

    public final Integer percent;
    public final Long secondsRemaining;
    public final long secondsProcessed;

    private TranscriptionProgress(Integer percent, Long secondsRemaining, long secondsProcessed) {
        this.percent = percent;
        this.secondsRemaining = secondsRemaining;
        this.secondsProcessed = secondsProcessed;
    }

    public static TranscriptionProgress calculate(long processedSamples, long durationMillis,
                                                   long elapsedMillis) {
        long processed = Math.max(0, processedSamples);
        long secondsProcessed = processed / SAMPLE_RATE;
        if (durationMillis <= 0) return new TranscriptionProgress(null, null, secondsProcessed);

        double totalSamples = durationMillis * 16d;
        int percent = (int) Math.min(99, Math.floor(processed * 100d / totalSamples));
        Long secondsRemaining = null;
        if (processed >= SAMPLE_RATE * 5 && elapsedMillis >= 2000) {
            double remainingMillis = elapsedMillis * Math.max(0, totalSamples - processed) / processed;
            secondsRemaining = (long) Math.ceil(remainingMillis / 1000d);
        }
        return new TranscriptionProgress(percent, secondsRemaining, secondsProcessed);
    }
}
