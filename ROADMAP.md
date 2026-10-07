# MotionFlow — feuille de route (fusion CapCut + Alight Motion)

Document dérivé du *Rapport directeur* et du *Rapport de conception* (octobre 2026), confronté au code de la branche `feature/ui-home-screen` (PR #1). Légende : ✅ présent · 🟡 partiel · 🔴 absent.
L'état des lieux vient d'une lecture du code, pas d'une exécution : rien n'a été testé sur appareil.

## 1. Principe

Garder MotionFlow comme socle. Règle transversale du rapport (§44-45) : **chaque fonction a une capacité Aperçu et une capacité Export**, et ce qui n'est pas exportable est refusé explicitement, jamais dégradé en silence. Le garde-fou existe déjà (`ExportCapabilityAnalyzer`, `AdvancedRenderPlanner`) ; il doit rester aligné avec les moteurs (c'est ce qui était cassé pour les fondus).

## 2. État réel par domaine

| Domaine (sections du rapport) | État | Constat dans le code |
|---|---|---|
| Projet, multi-projets, sauvegarde locale (§60) | ✅ | Room + JSON versionné (v4), dépôt multi-projets dans la PR #1 |
| Package de projet, import/export, cloud (§61-64) | 🔴 | Rien. `exportSchema = false`, pas de migration Room |
| Import, trim, split, supprimer, dupliquer (§12) | ✅ | `EditorViewModel`, `TimelineMath` |
| Fusionner, remplacer (§12) | 🔴 | Non trouvés (seul l'échange de pistes `swapTracks` existe) |
| Timeline multipistes, marqueurs, aimant, verrou/mute/masquage (§55) | ✅ UI / 🟡 export | Pistes V2+ refusées à l'export |
| Timeline à deux niveaux, inspecteur universel (§56-57) | 🟡 | Barre contextuelle et tiroirs (PR #1), pas d'inspecteur unifié |
| Formats 9:16, 16:9, 1:1 (§10) | ✅ | `AspectRatio` ; 4:5 et personnalisé 🔴 |
| Vitesse, courbe de vitesse, freeze, reverse (§14) | 🔴 | Aucune notion de vitesse dans le modèle. Un `reverseVideo` FFmpeg isolé existe mais n'est pas relié au modèle |
| Keyframes, easing (§15-16) | 🟡 | 5 propriétés (x, y, scale, rotation, opacité) ; 4 easings. Pas de Bézier, ni bounce/elastic, ni éditeur de courbes |
| Parenting, Null Objects, groupes (§17-18) | 🔴 | Pas de `parentId`, ni de type de calque générique |
| Caméra, profondeur, focus, fog, motion blur (§19-20) | 🔴 | Aucun |
| Masques (§21) | 🟡 | Rectangle, cercle, ellipse, dégradés, inversion, feather ; un seul masque par clip, non animé |
| Blend modes (§23) | 🟡 | 7 modes ; plein cadre seulement, et FFmpeg obligatoire |
| Effets, pile d'effets (§24-26) | 🟡 | Réglages couleur + 5 filtres + flou ; pas de pile ordonnée/animable |
| Chroma key (§27) | 🟡 | Couleur, seuil, douceur ; pas de despill ni nettoyage des bords |
| Texte (§32-33) | 🟡 | Préréglages, police, couleur, taille, position ; pas de contour/ombre/fond/animation, pas d'import TTF/OTF |
| Sous-titres auto, traduction, TTS (§34-37) | 🔴 | Le tiroir « Outils IA » n'est qu'une liste : chaque outil affiche « bientôt disponible » |
| Audio : volume, pistes, aperçu (§29-30) | 🟡 | Volume 0–2 par clip ; pas de fondu, ducking, réduction de bruit, pitch |
| Forme d'onde, détection de beats (§31) | 🔴 | La forme d'onde de la timeline est un tracé pseudo-aléatoire de substitution (pas d'analyse PCM) ; aucune détection de beats |
| Transitions (§ 80) | 🟡 | Cross-fade et fade-through rendus via rampes d'opacité (PR #1) ; wipes refusés |
| Vectoriel, formes, dégradés (§42-45) | 🔴 | Aucun |
| Stickers, templates, éléments, presets, favoris (§38-41, 58-59) | 🔴 | Aucun |
| Stabilisation, tracking, suppression d'arrière-plan (§46, 50, 28) | 🔴 | Aucun |
| Export : MP4, résolution, fps, qualité (§65-70) | 🟡 | Tableau de bord d'export ; H.264 via Transformer. GIF, PNG, séquence, HEVC, presets sociaux 🔴 |
| Performance : proxy, cache, qualité d'aperçu (§71-75) | 🔴 | Rien |
| Release : R8, signature, tests sur appareil | 🔴 | Voir `OPENEDIT_REMAINING.md` §7-8 |

## 3. Ordre proposé

Chaque étape se termine par des tests unitaires et une règle d'export explicite.

**Étape 0 — Assainir (en cours, voir les commits de cette branche)**
- Fondus exportables, projets illisibles protégés, licences des polices.
- À faire ensuite : schéma Room exporté + test de migration, texte OFL complet, écran de licences, `allowBackup`, configuration de release (R8).

**Étape 1 — Modèle de calque (Scene Graph) — prérequis de tout le reste**
- Introduire `Layer` (id, type, parentId, début, durée, transform, opacité, blend, masques, effets, keyframes, visibilité, verrou) **à côté** de `VideoClip`/`TextOverlay`, avec un adaptateur et une version de format `5` du codec.
- Les clips actuels deviennent des calques de type vidéo/image/audio/texte sans changer le comportement.
- Critère : un projet v4 s'ouvre et se réexporte à l'identique.

**Étape 2 — Temps (niveau CapCut)**
- `TimeRemap` par clip : vitesse constante, reverse, freeze, puis courbe de vitesse.
- Le support Media3 des changements de vitesse doit être vérifié à la compilation avant de s'engager ; sinon passer par le backend FFmpeg avec refus explicite hors FFmpeg.

**Étape 3 — Compositeur multi-pistes**
- Lever le refus des pistes V2+ (chantier n°1 de `OPENEDIT_REMAINING.md`), puis blends positionnés et wipes.
- Une seule description de rendu partagée par l'aperçu et l'export.

**Étape 4 — Animation (niveau Alight Motion)**
- Propriétés animables génériques, éditeur de courbes (Bézier), parenting et Null Objects (réutilise `parentId`), pile d'effets.

**Étape 5 — Texte, audio, sous-titres**
- Contour/ombre/fond/animations, polices importées ; fondus, forme d'onde, ducking ; sous-titres hors ligne avec modèle optionnel et correction manuelle.

**Étape 6 — Performance et sorties**
- Proxy, aperçu 25/50/75/100 %, cache par segments ; presets d'export social, GIF/PNG, HEVC selon l'appareil.

**Étape 7 — Éléments, templates, packages, cloud**
- Après stabilisation du format de projet.

## 4. Points à décider

1. Un seul format de projet versionné (JSON actuel) ou format de package (zip) dès l'étape 1 ?
2. Backend FFmpeg : fork communautaire `ffmpegkit-maintained` à valider (licence LGPL/GPL, maintenance) avant d'en dépendre pour des fonctions « de base ».
3. Wolof et autres langues du §35 : aucun modèle fiable ne peut être promis ; à traiter comme optionnel et à valider avec des locuteurs.
4. Identité visuelle propre (§87) : rien de CapCut/Alight Motion ne doit être repris (icônes, animations, templates).
