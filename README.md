# MotionFlow — OpenEditVideo (V77)

Éditeur vidéo Android open source — Kotlin + Jetpack Compose + Media3 1.11.1.

## Architecture de rendu
- Media3 reste le pipeline Android principal.
- CompositionPlayer + MultipleInputVideoGraph pour la prévisualisation multi-entrées.
- Les effets Media3 restent des effets à une entrée ; les mélanges source/destination arbitraires sont isolés derrière un contrat de compositeur programmable.
- Les blends plein cadre avancés ont un backend optionnel de filter-graph FFmpeg (`-PenableFfmpeg=true`).
- Les médias `content://` Android sont copiés dans le cache privé avant exécution FFmpeg.
- Aucune fonctionnalité non supportée n'est dégradée silencieusement.

## Nouveautés V77
- Keyframes indépendantes par propriété : X, Y, échelle, rotation et opacité.
- Interpolations rapides de type CapCut et courbes Bézier éditables de type Alight Motion.
- Préréglages avancés : palier, rebond, élastique et mouvement par étapes.
- Navigation précédente/suivante entre les images clés et mini-piste de propriété.
- Aperçu et export alimentés par le même modèle d'animation.
- Migration du format de projet V4 vers V5 en conservant l'interpolation des anciens projets.

## Nouveautés V76
- Marqueurs de timeline persistants (ajout, navigation, suppression).
- Aimantation du curseur sur les marqueurs (toggle « Aimant »).
- Pistes verrouillables / muettes / masquables, prises en compte à l'export.
- Workflow CI prêt dans \`ci/android-ci.yml\` (à copier dans \`.github/workflows/\`).
## Build
```bash
# Générer le wrapper une fois (Gradle >= 9.4 installé localement) :
gradle wrapper --gradle-version 9.4.0
# Puis :
./gradlew :app:assembleDebug        # build standard
./gradlew :app:testDebugUnitTest    # tests unitaires
./gradlew :app:assembleDebug -PenableFfmpeg=true   # avec backend FFmpeg
```

## Documentation
- `V66-V75.md`, `V76.md` — notes de version
- `OPENEDIT_REMAINING.md` — ce qui reste avant production
- `OPEN_SOURCE_COMPONENTS.md` — projets open source repérés pour combler les manques
- `OPEN_SOURCE_ROADMAP.md`, `OPEN_SOURCE_STACK.md`, `OPEN_SOURCE_ENGINEERING.md`
- `FFMPEG_OPTIONAL.md` — backend FFmpeg optionnel

## Licence
Apache-2.0 (voir `LICENSE`). Dépendances tierces : `THIRD_PARTY/NOTICE.md`.
