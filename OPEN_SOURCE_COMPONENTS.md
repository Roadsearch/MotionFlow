# Composants open source repérés sur GitHub

Recherche effectuée (oct. 2026) pour combler les manques listés dans `OPENEDIT_REMAINING.md`.

## Déjà intégrés au projet
| Besoin | Projet | Licence | État |
|---|---|---|---|
| Pipeline lecture/rendu | [androidx/media3](https://github.com/androidx/media3) | Apache-2.0 | Intégré (1.11.1) |
| Moteur avancé d'export (filter graphs) | [arthenica/ffmpeg-kit](https://github.com/arthenica/ffmpeg-kit) → fork maintenu `dev.ffmpegkit-maintained:ffmpeg-kit-full` | LGPL-3.0 | Optionnel via `-PenableFfmpeg=true` |
| Successeur officiel FFmpegKit | [arthenica/ffmpeg-kit-next](https://github.com/arthenica/ffmpeg-kit-next) | LGPL-3.0 | À surveiller pour migration future |

## Candidats pour les blocs restants
| Bloc restant | Projet open source | Pourquoi |
|---|---|---|
| Formes d'onde audio réelles (PCM) | [lincollincol/compose-audiowaveform](https://github.com/lincollincol/compose-audiowaveform) (272★) | Waveform Compose prête à l'emploi, amplitudes réelles, remplace la pseudo-waveform actuelle |
| Référence d'architecture NLE | [devhyper/open-video-editor](https://github.com/devhyper/open-video-editor) (731★) | Éditeur vidéo open source Media3 + Compose — trim, filtres, transitions ; bonne référence de conception |
| Filtres GPU temps réel étendus | [pixpark/gpupixel](https://github.com/pixpark/gpupixel) (2,4k★) | Moteur de filtres GPU C++ (beauté, LUT), NDK, pour aller au-delà des effets Media3 |
| Filtres GLSL sur ExoPlayer | [MasayukiSuda/GPUVideo-android](https://github.com/MasayukiSuda/GPUVideo-android) (677★) | Patterns de shaders GLSL appliqués à la vidéo — utile pour le compositeur deux-entrées |
| Catalogue de filtres GPU | [wasabeef/android-gpuimage](https://github.com/wasabeef/android-gpuimage) (9,1k★) | Grande bibliothèque de filtres OpenGL à porter en shaders Media3 |
| Segmentation / tracking (IA) | ML Kit (Google) + modèles TFLite on-device | Reste offline-capable, condition posée par la roadmap |
| Sous-titres automatiques offline | [openai/whisper](https://github.com/openai/whisper) via whisper.cpp/TFLite | Transcription on-device pour auto-captions |

## Règles d'intégration
1. Toute nouvelle dépendance passe par l'audit licence de `THIRD_PARTY/NOTICE.md`.
2. Les moteurs lourds (FFmpeg, modèles IA) restent optionnels et offline-capables.
3. Preview et export doivent partager les mêmes mathématiques de timing et de couleur.
