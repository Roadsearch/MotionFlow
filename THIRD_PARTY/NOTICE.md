# Third-party notices

OpenEditVideo uses third-party open-source libraries. Keep each upstream license/notice when redistributing the application. This file records the selected libraries and their upstream license families; the exact license text should be generated/checked by the build's dependency reporting before release.

Media3 — Apache-2.0
AndroidX/Compose/Room/WorkManager/DataStore — Apache-2.0
Kotlinx Serialization — Apache-2.0
Coil — Apache-2.0
Lottie Android — Apache-2.0
Dagger/Hilt — Apache-2.0
ONNX Runtime (when enabled) — MIT
MediaPipe (when enabled) — Apache-2.0
FFmpegKit-maintained 8.1.9 (optional) — LGPL when using the non-GPL artifact; GPL-licensed variants must not be enabled for a non-GPL distribution. Keep upstream notices/source-offer obligations and verify the exact variant before release.

## Bundled fonts (SIL Open Font License 1.1)

The following fonts are bundled in `app/src/main/res/font/`. The copyright lines below are taken from the font files' own metadata.

- Inter (`inter_variable.ttf`, version 4.001) — Copyright 2016 The Inter Project Authors (https://github.com/rsms/inter)
- Bebas Neue (`bebas_neue_regular.ttf`, version 2.000) — Copyright 2019 The Bebas Neue Project Authors (https://github.com/dharmatype/Bebas-Neue)

Both are licensed under the SIL Open Font License, Version 1.1 (https://openfontlicense.org). The OFL requires that each redistributed copy of the font software is accompanied by its copyright notice and the license text, and that the fonts are not sold on their own.

TODO before any public release: add the verbatim `OFL.txt` of each font project (taken from the upstream repositories listed above) next to this file, and expose these notices inside the app (for example an "Open source licences" screen).
