# What remains before OpenEditVideo is production-complete

This document intentionally distinguishes "code exists" from "feature is production-validated".

Last audited against `main` @ 4a2789e (2026-10-23). Status legend:
`[x]` done, `[~]` partial, `[ ]` open (no code found), `[?]` not verified in this audit.
"Done" means the code exists, not that it was validated on a physical device.

## Done since the previous version
- [x] Independent per-property keyframes (X, Y, scale, rotation, opacity) with Bezier curves and presets (V77).
- [x] One shared `ClipTransform` for preview and export (single transform pass).
- [x] Persistent timeline markers and marker snapping (V76).
- [x] Track lock / mute / hide honoured at export (V76).
- [x] Gradle wrapper checked in; CI workflow in `.github/workflows/android-ci.yml`.

## 1. Render engine - biggest remaining block
- [ ] Implement the actual framebuffer-based two-input compositor.
- [ ] Wire source/destination blend equations into a VideoGraph or dedicated native GPU backend.
- [~] Render positioned Multiply/Screen/Overlay/Add, not only full-frame blends. (Full-frame blends only, through the optional FFmpeg backend.)
- [ ] Render two-input transitions: crossfade, wipe, slide, zoom. (Only fade in/out exist today.)
- [~] Ensure preview/export use the same timing and color math. (Transform and animation are shared; color math not verified.)

## 2. Audio engine
- [ ] Waveform cache generated from PCM analysis. (The timeline draws a deterministic placeholder waveform.)
- [?] Volume/pan envelopes.
- [~] Crossfades and fades. (Fade curves exist in `AudioFadeCurve`; crossfades not verified.)
- [ ] EQ, compressor, limiter, noise gate and ducking.
- [ ] Beat detection and time-based snapping.
- [?] Audio-only/stem export validation.

## 3. Editor completeness
- [?] Slip/slide/ripple editing.
- [~] Multi-select drag across tracks. (The selection model holds a set of clips; cross-track drag not verified.)
- [x] Track locking, visibility and mute. (Solo not verified.)
- [~] Markers and snapping modes. (Markers and marker snapping done; other snapping modes not verified.)
- [?] Speed curves and reverse. (Speed is handled by the FFmpeg command builder.)
- [ ] Nested compositions / compound clips.
- [ ] Project archive/import and relink UI.

## 4. Text / overlays
- [~] Rich text editor, fonts, stroke, shadow, background, alignment. (Text shadow exists; the rest not verified.)
- [?] Animated text presets.
- [ ] Sticker/image transforms with keyframes. (No sticker support found.)
- [?] Asset caching and missing-asset recovery.

## 5. AI-assisted tools
- [ ] Human/object segmentation.
- [ ] Motion tracking.
- [ ] Smart reframe.
- [ ] Auto captions with a real offline model.
- [ ] Silence removal / beat-sync helpers.
- Keep all models optional and offline-capable.

## 6. Color / HDR
- [?] Color-management policy for SDR/HDR mixtures.
- [?] BT.709/BT.2020 handling.
- [?] Tone-map options.
- [?] 10-bit export validation.
- [?] Device capability matrix.

## 7. Production hardening
- [ ] Instrumented tests on physical Android devices. (No `androidTest` source set.)
- [?] 16 KB page/ABI checks.
- [?] Memory pressure tests with long 4K clips.
- [?] Thermal throttling and export cancellation tests.
- [?] Corrupt-media recovery tests.
- [?] MediaStore, scoped storage and permission tests.
- [?] Strict dependency/license/NOTICE audit.

## 8. Release engineering
- [x] Gradle wrapper checked in.
- [?] Reproducible release build.
- [ ] R8/proguard rules. (No `proguard-rules.pro` and no minify configuration found.)
- [ ] Baseline Profile.
- [?] Crash/ANR diagnostics.
- [?] Signed release bundle/APK.
- [?] Play metadata and privacy/data-safety review.
