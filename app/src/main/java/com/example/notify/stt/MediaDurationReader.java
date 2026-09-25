package com.example.notify.stt;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import com.arthenica.ffmpegkit.FFmpegKitConfig;
import com.arthenica.ffmpegkit.FFprobeKit;
import com.arthenica.ffmpegkit.MediaInformation;
import com.arthenica.ffmpegkit.MediaInformationSession;

/** Reads the media duration when available, without decoding the entire file. */
public final class MediaDurationReader {
    private MediaDurationReader() { }

    public static long readMillis(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            long duration = Long.parseLong(retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (duration > 0) return duration;
        } catch (Exception ignored) {
            // Try FFprobe for formats the device does not recognize.
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }

        try {
            String input = "content".equals(uri.getScheme())
                    ? FFmpegKitConfig.getSafParameterForRead(context, uri)
                    : uri.getPath();
            if (input == null) return 0;
            MediaInformationSession session = FFprobeKit.getMediaInformation(input);
            MediaInformation information = session.getMediaInformation();
            if (information == null) return 0;
            double seconds = Double.parseDouble(information.getDuration());
            return Double.isFinite(seconds) && seconds > 0 && seconds < Long.MAX_VALUE / 1000d
                    ? (long) (seconds * 1000) : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }
}
