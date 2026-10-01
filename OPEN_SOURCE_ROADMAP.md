# Open-source engine roadmap

## Integrated / core
- Media3 1.11.1: player, Transformer, Composition, Effect, MultipleInputVideoGraph.
- Coil 3 video: thumbnails/filmstrips.
- Room + WorkManager + Hilt: project state, background exports, DI.
- Lottie 6.7.1: animated templates/stickers.
- FFmpeg maintained fork: optional advanced filter/format backend; use the LGPL artifact for a non-GPL app.

## High-value candidates (kept behind optional boundaries)
- OpenCV (Apache-2.0): motion/feature tracking, stabilization analysis, optical flow helpers, geometry/color tools. OpenCV 4.14.0 was released July 2026; keep the Android SDK isolated because native size and ABI cost are material.
- MediaPipe (Apache-2.0): on-device segmentation and vision tasks; suitable for smart cutout/reframe analysis, not the primary realtime render path. 
- SAM 2 (Apache-2.0): video segmentation candidate for offline mask generation; model files have to be tracked separately from code.
- libplacebo (LGPL): future native color-management/HDR/shader backend after an NDK boundary is established.

## License rule
For every third-party engine, record repository URL, exact version/commit, code license, model/license terms, NOTICE obligations, ABI impact and whether the feature can run without network access. Do not copy source code merely because a repository is public.
