package com.example.notify.stt;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Decodes a media file's audio track and converts it to mono 16 kHz samples. */
public final class AndroidAudioDecoder {
    private AndroidAudioDecoder() { }

    public static void stream(Context context, Uri uri, Mono16kBuilder.ChunkConsumer consumer) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            try {
                extractor.setDataSource(context, uri, null);
            } catch (IOException | RuntimeException error) {
                // Some devices cannot extract a simple PCM WAV; keep that format usable.
                try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                    if (input != null) {
                        PcmWavReader.stream(input, consumer);
                        return;
                    }
                } catch (IOException ignored) { }
                throw new IOException("This device cannot read the selected media file", error);
            }

            for (int track = 0; track < extractor.getTrackCount(); track++) {
                MediaFormat format = extractor.getTrackFormat(track);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("audio/")) continue;
                extractor.selectTrack(track);
                if ("audio/raw".equals(mime)) readRaw(extractor, format, consumer);
                else decode(extractor, format, mime, consumer);
                return;
            }
            throw new IOException("The selected file has no supported audio track");
        } catch (RuntimeException error) {
            throw new IOException("This device cannot decode the selected audio format", error);
        } finally {
            extractor.release();
        }
    }

    private static void readRaw(MediaExtractor extractor, MediaFormat format,
                                Mono16kBuilder.ChunkConsumer consumer) throws IOException {
        int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int encoding = pcmEncoding(format);
        Mono16kBuilder result = new Mono16kBuilder(rate, consumer);
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024 * 1024);
        while (true) {
            buffer.clear();
            int size = extractor.readSampleData(buffer, 0);
            if (size < 0) break;
            buffer.position(0);
            buffer.limit(size);
            appendPcm(buffer, channels, encoding, result);
            extractor.advance();
        }
        result.finish();
    }

    private static void decode(MediaExtractor extractor, MediaFormat format, String mime,
                               Mono16kBuilder.ChunkConsumer consumer) throws IOException {
        MediaCodec codec = MediaCodec.createDecoderByType(mime);
        boolean started = false;
        try {
            codec.configure(format, null, null, 0);
            codec.start();
            started = true;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            Mono16kBuilder result = null;
            int outputRate = 0;
            int channels = 0;
            int encoding = AudioFormat.ENCODING_PCM_16BIT;
            boolean inputEnded = false;
            boolean outputEnded = false;
            int idleCycles = 0;

            while (!outputEnded) {
                boolean progressed = false;
                if (!inputEnded) {
                    int inputIndex = codec.dequeueInputBuffer(10000);
                    if (inputIndex >= 0) {
                        ByteBuffer input = codec.getInputBuffer(inputIndex);
                        if (input == null) throw new IOException("Audio decoder input is unavailable");
                        input.clear();
                        int size = extractor.readSampleData(input, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEnded = true;
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                        progressed = true;
                    }
                }

                int outputIndex = codec.dequeueOutputBuffer(info, 10000);
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat output = codec.getOutputFormat();
                    int rate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (result == null) result = new Mono16kBuilder(rate, consumer);
                    else if (outputRate != rate) throw new IOException("The audio sample rate changed during decoding");
                    outputRate = rate;
                    channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    encoding = pcmEncoding(output);
                    progressed = true;
                } else if (outputIndex >= 0) {
                    if (result == null) {
                        MediaFormat output = codec.getOutputFormat();
                        outputRate = output.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        channels = output.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        encoding = pcmEncoding(output);
                        result = new Mono16kBuilder(outputRate, consumer);
                    }
                    if (info.size > 0) {
                        ByteBuffer output = codec.getOutputBuffer(outputIndex);
                        if (output == null) throw new IOException("Audio decoder output is unavailable");
                        output.position(info.offset);
                        output.limit(info.offset + info.size);
                        appendPcm(output, channels, encoding, result);
                    }
                    outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    codec.releaseOutputBuffer(outputIndex, false);
                    progressed = true;
                }
                idleCycles = progressed ? 0 : idleCycles + 1;
                if (idleCycles > 500) throw new IOException("Audio decoding stopped responding");
            }
            if (result == null) throw new IOException("No audio samples found in the file");
            result.finish();
        } finally {
            if (started) codec.stop();
            codec.release();
        }
    }

    private static int pcmEncoding(MediaFormat format) {
        return format.containsKey(MediaFormat.KEY_PCM_ENCODING)
                ? format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                : AudioFormat.ENCODING_PCM_16BIT;
    }

    private static void appendPcm(ByteBuffer buffer, int channels, int encoding, Mono16kBuilder result)
            throws IOException {
        if (channels <= 0) throw new IOException("Invalid audio channel count");
        buffer.order(ByteOrder.nativeOrder());
        int bytesPerSample;
        switch (encoding) {
            case AudioFormat.ENCODING_PCM_8BIT: bytesPerSample = 1; break;
            case AudioFormat.ENCODING_PCM_16BIT: bytesPerSample = 2; break;
            case AudioFormat.ENCODING_PCM_24BIT_PACKED: bytesPerSample = 3; break;
            case AudioFormat.ENCODING_PCM_32BIT:
            case AudioFormat.ENCODING_PCM_FLOAT: bytesPerSample = 4; break;
            default: throw new IOException("Unsupported decoded PCM audio format");
        }
        int frameBytes = channels * bytesPerSample;
        if (buffer.remaining() % frameBytes != 0) throw new IOException("Invalid decoded audio frame");
        while (buffer.remaining() >= frameBytes) {
            float mono = 0f;
            for (int channel = 0; channel < channels; channel++) {
                float sample;
                switch (encoding) {
                    case AudioFormat.ENCODING_PCM_8BIT:
                        sample = (buffer.get() & 0xff) / 128f - 1f;
                        break;
                    case AudioFormat.ENCODING_PCM_16BIT:
                        sample = buffer.getShort() / 32768f;
                        break;
                    case AudioFormat.ENCODING_PCM_24BIT_PACKED:
                        int value = (buffer.get() & 0xff) | ((buffer.get() & 0xff) << 8) | (buffer.get() << 16);
                        sample = value / 8388608f;
                        break;
                    case AudioFormat.ENCODING_PCM_32BIT:
                        sample = (float) (buffer.getInt() / 2147483648.0);
                        break;
                    default:
                        sample = buffer.getFloat();
                }
                if (Float.isNaN(sample) || Float.isInfinite(sample)) sample = 0f;
                mono += sample;
            }
            result.addFrame(mono / channels);
        }
    }
}
