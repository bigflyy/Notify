package com.example.notify.stt;

import android.content.Context;
import android.net.Uri;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

/** Streams formats unsupported by Android's decoder as mono 16 kHz PCM. */
public final class FfmpegAudioDecoder {
    private FfmpegAudioDecoder() { }

    public static void stream(Context context, Uri uri, Mono16kBuilder.ChunkConsumer consumer)
            throws IOException {
        String input = "content".equals(uri.getScheme())
                ? FFmpegKitConfig.getSafParameterForRead(context, uri)
                : uri.getPath();
        if (input == null || input.isEmpty()) throw new IOException("Cannot open the selected file");

        String pipe = FFmpegKitConfig.registerNewFFmpegPipe(context);
        if (pipe == null) throw new IOException("Cannot create an audio conversion pipe");
        AtomicReference<FFmpegSession> finished = new AtomicReference<>();
        FFmpegSession session = null;
        FileDescriptor reader = null;
        try {
            reader = Os.open(pipe, OsConstants.O_RDONLY | OsConstants.O_NONBLOCK, 0);
            session = FFmpegKit.executeWithArgumentsAsync(new String[] {
                    "-hide_banner", "-loglevel", "error", "-i", input,
                    "-map", "0:a:0", "-vn", "-ac", "1", "-ar", "16000",
                    "-c:a", "pcm_f32le", "-f", "f32le", "-y", pipe
            }, finished::set);
            FfmpegPcmReader.stream(reader, () -> finished.get() != null, consumer);
            FFmpegSession result = finished.get();
            if (result == null || !ReturnCode.isSuccess(result.getReturnCode())) {
                throw new IOException("FFmpeg could not decode this file"
                        + (result == null ? "" : ": " + result.getAllLogsAsString().trim()));
            }
        } catch (ErrnoException error) {
            throw new IOException("Cannot read converted audio", error);
        } finally {
            if (session != null && finished.get() == null) session.cancel();
            if (reader != null) {
                try { Os.close(reader); } catch (ErrnoException ignored) { }
            }
            FFmpegKitConfig.closeFFmpegPipe(pipe);
        }
    }
}
