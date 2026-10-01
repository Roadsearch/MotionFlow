# Optional FFmpeg backend

OpenEditVideo keeps Media3 as the default playback/export backend. FFmpeg is an optional escape hatch for operations that are awkward or unsupported in the stable Media3 path.

## Enable

```bash
./gradlew :app:assembleDebug -PenableFfmpeg=true
```

This adds:

```kotlin
implementation("dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9")
```

The Java API remains `com.arthenica.ffmpegkit.*` for source compatibility with the maintained fork.

## Why optional

The FFmpeg AAR is large compared with pure AndroidX dependencies. Keeping it conditional avoids forcing the native FFmpeg payload into every build.

## License

The non-GPL maintained artifact is published as LGPL. Do not switch to a `-gpl` artifact unless the complete application/distribution is intentionally GPL-compatible. Preserve the upstream license/notice/source-offer obligations and verify the exact codec/filter set used by the release.

## Current command builder

`FfmpegCommandBuilder` is deliberately pure Kotlin. It currently demonstrates:

- reverse video + audio (`reverse`, `areverse`)
- PCM mono extraction to WAV

The command builder is separated from the Media3 exporter so that future fallbacks can be selected by capability rather than silently replacing the default backend.
