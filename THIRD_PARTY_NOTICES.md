# FFmpeg for imported media

Notify uses `dev.ffmpegkit-maintained:ffmpeg-kit-audio:8.1.7` to decode imported audio and video when Android's built-in decoder cannot. The FFmpegKit audio package is licensed under LGPL-3.0. Its source and build instructions are available at the [v8.1.7 release](https://github.com/ffmpegkit-maintained/ffmpeg/releases/tag/v8.1.7). The package includes license texts for FFmpegKit and bundled components in its Android `res/raw/license*.txt` resources.

Notify also uses `com.arthenica:smart-exception-java:0.2.1` and its `smart-exception-common` dependency, required by FFmpegKit. They use the BSD-3-Clause license; their source is in the [Smart Exception repository](https://github.com/tanersener/smart-exception).

If distributing an APK, review [FFmpeg's LGPL distribution checklist](https://ffmpeg.org/legal.html) and provide the corresponding source and notices alongside the binary.
