# What remains before OpenEditVideo is production-complete

This document intentionally distinguishes "code exists" from "feature is production-validated".

## 1. Render engine — biggest remaining block
- Implement the actual framebuffer-based two-input compositor.
- Wire source/destination blend equations into a VideoGraph or dedicated native GPU backend.
- Render positioned Multiply/Screen/Overlay/Add, not only full-frame blends.
- Render two-input transitions: crossfade, wipe, slide, zoom.
- Ensure preview/export use the same timing and color math.

## 2. Audio engine
- Waveform cache generated from PCM analysis.
- Volume/pan envelopes.
- Crossfades and fades.
- EQ, compressor, limiter, noise gate and ducking.
- Beat detection and time-based snapping.
- Audio-only/stem export validation.

## 3. Editor completeness
- Slip/slide/ripple editing.
- Multi-select drag across tracks.
- Track locking, visibility and solo/mute.
- Markers and snapping modes.
- Speed curves and reverse.
- Nested compositions / compound clips.
- Project archive/import and relink UI.

## 4. Text / overlays
- Rich text editor, fonts, stroke, shadow, background, alignment.
- Animated text presets.
- Sticker/image transforms with keyframes.
- Asset caching and missing-asset recovery.

## 5. AI-assisted tools
- Human/object segmentation.
- Motion tracking.
- Smart reframe.
- Auto captions with a real offline model.
- Silence removal / beat-sync helpers.
- Keep all models optional and offline-capable.

## 6. Color / HDR
- Color-management policy for SDR/HDR mixtures.
- BT.709/BT.2020 handling.
- Tone-map options.
- 10-bit export validation.
- Device capability matrix.

## 7. Production hardening
- Instrumented tests on physical Android devices.
- 16 KB page/ABI checks.
- Memory pressure tests with long 4K clips.
- Thermal throttling and export cancellation tests.
- Corrupt-media recovery tests.
- MediaStore, scoped storage and permission tests.
- Strict dependency/license/NOTICE audit.

## 8. Release engineering
- Gradle wrapper checked in.
- Reproducible release build.
- R8/proguard rules.
- Baseline Profile.
- Crash/ANR diagnostics.
- Signed release bundle/APK.
- Play metadata and privacy/data-safety review.
