# Open source stack selected for OpenEditVideo

This project keeps Media3 as the primary playback/render/export stack and adds permissively licensed libraries only when they solve a concrete problem.

## Integrated

- AndroidX Media3 1.11.1: playback, effects, Transformer, composition.
- Coil 3.6.3 (Apache-2.0): asynchronous image/video-frame loading and caching. Coil 3.6.3 specifically fixes builds with AGP 9.4/R8 and `coil-video` can extract frames at a requested timestamp.
- Lottie 6.7.1 (Apache-2.0): ready for animated title/sticker templates.
- Dagger/Hilt 2.60.1 (Apache-2.0): dependency injection and lifecycle-safe workers/view models.
- Kotlinx Serialization 1.11.0: structured project state serialization; legacy JSON remains readable through the existing codec.
- DataStore 1.2.1: lightweight settings which do not belong in Room.
- Room 2.8.5: project persistence.
- WorkManager 2.12.0: background export lifecycle.

## High-value open-source references evaluated

- ClearCut / WizardCut: MIT. Very useful reference for a robust multi-track editor, timeline transaction handling, export validation, OpenGL transitions/effects, audio DSP, captions and project interchange. We use the architecture ideas as reference; we do not copy source files wholesale.
- ActionCut: open-source Kotlin/Compose/Media3 editor using Clean Architecture, Hilt, Room, WorkManager, Coil and kotlinx.serialization. Its repository is a strong reference for separating a pure TimelineEditor from Android media adapters.
- LibreCuts: MIT. Particularly useful reference for overlays, masking, chroma key, captions, layer management and audio ducking. Its older releases bundle FFmpeg binaries, so native FFmpeg licensing/build provenance must be handled separately.
- ONNX Runtime: MIT. Candidate for offline AI such as segmentation, denoise, smart reframing and future object removal. Keep it optional until a concrete model + CPU/GPU/NPU execution path is selected because it materially increases binary/model size.
- MediaPipe: Apache-2.0. Candidate for segmentation/smart reframe. Device-specific native loading issues have existed in the 0.10.26 era, so enable it only behind a capability check and after physical-device validation.
- libplacebo: LGPL-2.1-or-later. Very strong future candidate for high-quality HDR/color management/upscaling/shader processing, but native integration is significantly more complex than Media3 Effects and should be a later rendering backend.

## Explicitly not integrated

- KEditor: GPL-2.0. It is useful technically (FFmpeg trim/crop/filters/etc.) but its project license is not suitable for an otherwise permissively licensed app unless the whole distribution strategy is deliberately made GPL-compatible.
- Retired Arthenica FFmpegKit artifacts: not used. The optional fallback uses the maintained community fork and pins 8.1.9; verify the exact LGPL/GPL variant and codec set before release.

## Sources checked on 2026-10-01

- Coil 3.6.3 and `coil-video`: https://coil-kt.github.io/coil/videos/
- Hilt 2.60.1: https://github.com/google/dagger/releases
- DataStore 1.2.1: https://developer.android.com/jetpack/androidx/releases/datastore
- Room 2.8.5: https://developer.android.com/jetpack/androidx/releases/room
- WorkManager 2.12.0: https://developer.android.com/jetpack/androidx/releases/work
- ClearCut: https://github.com/SysAdminDoc/ClearCut
- ActionCut: https://github.com/naveenneog/ActionCut
- LibreCuts: https://github.com/tharunbirla/LibreCuts
- ONNX Runtime: https://github.com/microsoft/onnxruntime
- MediaPipe: https://github.com/google-ai-edge/mediapipe
- libplacebo: https://github.com/haasn/libplacebo


## New recommendation after V41–V45

### Highest-value references
- **ClearCut** — MIT, architecture très avancée : timeline multi-track, export Media3/FFmpeg, OpenGL shaders, audio DSP, captions, interchange et garde-fous de capacité.
- **ActionCut** — open source Kotlin/Compose/Media3, particulièrement utile pour le découplage `TimelineEditor` + adapters média et pour les presets d’export.
- **DigitorAndroid** — référence intéressante pour un pipeline GPU-first, `MultipleInputVideoGraph`, contrat preview/export et fallback CPU; à étudier pour notre prochain compositeur.
- **LibreCuts** — utile comme référence fonctionnelle pour overlays, masking, chroma-key, captions et audio ducking.
- **FFmpegKit-maintained 8.1.9** — continuation communautaire de FFmpegKit; Maven Central publie des artefacts LGPL distincts des variantes GPL. Il reste optionnel afin de contenir la taille de l'APK.

### Not adopted as default
- libplacebo : puissant pour HDR/color management/upscaling, mais intégration native lourde; à isoler dans un backend avancé.
- ONNX Runtime / MediaPipe : excellents pour segmentation, smart reframe, ASR et IA locale, mais à brancher derrière des interfaces de capacité et tests appareils.


## Optional FFmpeg fallback (checked 2026-10-01)
The build can opt in with `-PenableFfmpeg=true` and uses `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9`. The maintained fork keeps the `com.arthenica.ffmpegkit` Java package for source compatibility. The non-GPL artifact is LGPL; GPL variants carry separate GPL obligations.
