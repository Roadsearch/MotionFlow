package com.roadsearch.openeditvideo.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.core.editor.KeyframeEditor
import com.roadsearch.openeditvideo.model.AnimatedKeyframe
import com.roadsearch.openeditvideo.model.AnimatedProperty
import com.roadsearch.openeditvideo.model.Easing
import com.roadsearch.openeditvideo.model.at
import com.roadsearch.openeditvideo.model.effectiveAnimation
import com.roadsearch.openeditvideo.model.end
import com.roadsearch.openeditvideo.model.keyframes
import com.roadsearch.openeditvideo.model.normalized
import com.roadsearch.openeditvideo.model.selectedClip
import kotlin.math.abs

private fun AnimatedProperty.sliderRange(): ClosedFloatingPointRange<Float> = when (this) {
    AnimatedProperty.X, AnimatedProperty.Y -> -1000f..1000f
    AnimatedProperty.SCALE -> 0.1f..4f
    AnimatedProperty.ROTATION -> -360f..360f
    AnimatedProperty.OPACITY -> 0f..1f
}

private fun AnimatedProperty.display(value: Float): String = when (this) {
    AnimatedProperty.X, AnimatedProperty.Y -> "%.0f px".format(value)
    AnimatedProperty.SCALE, AnimatedProperty.OPACITY -> "%.0f %%".format(value * 100f)
    AnimatedProperty.ROTATION -> "%.0f°".format(value)
}

/** Presets offered in the compact panel; the full list lives in the curve editor. */
private val QuickEasings = listOf(Easing.LINEAR, Easing.EASE_IN, Easing.EASE_OUT, Easing.EASE_IN_OUT, Easing.HOLD)

@Composable
fun AnimationPanel(vm: EditorViewModel) {
    val state by vm.state.collectAsState()
    val clip = state.selectedClip() ?: return
    var parentMenuExpanded by remember(clip.id) { mutableStateOf(false) }
    var controllerParentMenuExpanded by remember(clip.id) { mutableStateOf(false) }
    val parent = state.nullObjects.firstOrNull { it.id == clip.parentId }
    val controllerParent = parent?.parentId?.let { id -> state.nullObjects.firstOrNull { it.id == id } }
    var activeProperty by remember(clip.id) { mutableStateOf(AnimatedProperty.SCALE) }
    var showCurveEditor by remember(clip.id) { mutableStateOf(false) }
    var showClearPropertyDialog by remember(clip.id) { mutableStateOf(false) }
    var advancedExpanded by remember(clip.id) { mutableStateOf(false) }
    var segmentOverrideMs by remember(clip.id, activeProperty) { mutableStateOf<Long?>(null) }

    // A slider drag that is still open when the panel leaves the screen must not swallow the next undo step.
    DisposableEffect(Unit) { onDispose { vm.commitEditGesture() } }

    val animation = clip.effectiveAnimation()
    val keyframes = animation.keyframes(activeProperty).normalized()
    val info = KeyframeEditor.info(state, activeProperty)
    val sourceEnd = clip.end(state.durationMs).coerceAtLeast(clip.startMs + 1L)
    val sourceTime = state.positionMs - clip.timelineStartMs + clip.startMs
    val segmentStarts = keyframes.dropLast(1)
    val currentSegment = segmentStarts.firstOrNull { it.timeMs == segmentOverrideMs } ?: info.segmentStart
    val canEdit = info.playheadOnClip || (info.keyframeCount == 0 && !state.autoKeyframe)
    val range = activeProperty.sliderRange()

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Animation, contentDescription = null)
                Text("Animation", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Auto-keyframe", style = MaterialTheme.typography.labelLarge)
                Switch(
                    checked = state.autoKeyframe,
                    onCheckedChange = { vm.setAutoKeyframe(it) },
                    modifier = Modifier.semantics { contentDescription = "Auto-keyframe" },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedProperty.values().forEach { property ->
                val count = animation.keyframes(property).size
                FilterChip(
                    selected = property == activeProperty,
                    onClick = { activeProperty = property },
                    label = { Text(if (count > 0) property.label + " · " + count else property.label) },
                )
            }
        }

        KeyframeTrackStrip(
            keys = keyframes,
            selectedMs = sourceTime.takeIf { info.keyAtPlayhead },
            sourceStartMs = clip.startMs,
            sourceEndMs = sourceEnd,
            timelineStartMs = clip.timelineStartMs,
            playheadMs = state.positionMs,
            onSeek = vm::seekToExact,
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KeyframeButton(
                text = "◂◆",
                description = "Image clé précédente",
                enabled = info.canGoPrevious,
                modifier = Modifier.weight(0.8f),
                onClick = { vm.seekToPreviousKeyframe(activeProperty) },
            )
            KeyframeButton(
                text = "◆+",
                description = "Ajouter une image clé",
                enabled = info.canAdd,
                highlighted = info.canAdd,
                modifier = Modifier.weight(1f),
                onClick = { vm.addKeyframeAtPlayhead(activeProperty) },
            )
            KeyframeButton(
                text = "◆−",
                description = "Supprimer l'image clé sous la tête de lecture",
                enabled = info.canRemove,
                highlighted = info.canRemove,
                modifier = Modifier.weight(1f),
                onClick = { vm.removeKeyframeAtPlayhead(activeProperty) },
            )
            KeyframeButton(
                text = "◆▸",
                description = "Image clé suivante",
                enabled = info.canGoNext,
                modifier = Modifier.weight(0.8f),
                onClick = { vm.seekToNextKeyframe(activeProperty) },
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(activeProperty.label + " · " + formatTime(state.positionMs), style = MaterialTheme.typography.bodyMedium)
            Text(
                activeProperty.display(info.value),
                style = MaterialTheme.typography.titleSmall,
                color = if (info.keyAtPlayhead) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = info.value.coerceIn(range.start, range.endInclusive),
            onValueChange = { vm.setPropertyValue(activeProperty, it, continuous = true) },
            onValueChangeFinished = { vm.commitEditGesture() },
            valueRange = range,
            enabled = canEdit,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            when {
                !info.playheadOnClip && info.keyframeCount > 0 -> "Placez la tête de lecture sur le clip pour modifier cette propriété."
                !info.playheadOnClip && state.autoKeyframe -> "Placez la tête de lecture sur le clip pour créer une image clé."
                info.keyAtPlayhead -> "Image clé sélectionnée : toute modification la met à jour."
                info.keyframeCount > 0 -> "Modifier la valeur crée une image clé à cet instant."
                state.autoKeyframe -> "Auto-keyframe actif : la première modification crée une image clé."
                else -> "Aucune image clé : la valeur est fixe. Touchez ◆+ pour l'animer."
            },
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextButton(
            onClick = { advancedExpanded = !advancedExpanded },
            modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp),
        ) { Text(if (advancedExpanded) "Interpolation et courbes ▴" else "Interpolation et courbes ▾") }
        AnimatedVisibility(visible = advancedExpanded) {
            Column {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val shown = currentSegment?.easingToNext ?: state.easing
                    QuickEasings.forEach { option ->
                        FilterChip(
                            selected = option == shown,
                            onClick = { vm.setSegmentEasing(activeProperty, option) },
                            label = { Text(option.labelFr()) },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedButton(
                        onClick = { showCurveEditor = true },
                        enabled = currentSegment != null,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Courbe avancée") }
                    OutlinedButton(
                        onClick = { showClearPropertyDialog = true },
                        enabled = keyframes.isNotEmpty(),
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) { Text("Tout supprimer") }
                }
            }
        }

        HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        Text("Parentage", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { parentMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(parent?.name ?: "Aucun parent", maxLines = 1)
                }
                DropdownMenu(expanded = parentMenuExpanded, onDismissRequest = { parentMenuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Aucun parent") },
                        onClick = { vm.setSelectedClipParent(null); parentMenuExpanded = false },
                    )
                    state.nullObjects.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.name) },
                            onClick = { vm.setSelectedClipParent(item.id); parentMenuExpanded = false },
                        )
                    }
                }
            }
            Button(onClick = { vm.createNullParentForSelectedClip() }) { Text("Créer Null") }
        }
        if (parent != null) {
            val transform = parent.animation.at(state.positionMs)
            Text("Animation du contrôleur · ${parent.name}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 20.dp))
            Text("Parent du contrôleur", modifier = Modifier.padding(start = 20.dp, top = 8.dp))
            Box(Modifier.padding(horizontal = 16.dp)) {
                OutlinedButton(onClick = { controllerParentMenuExpanded = true }) {
                    Text(controllerParent?.name ?: "Aucun parent")
                }
                DropdownMenu(
                    expanded = controllerParentMenuExpanded,
                    onDismissRequest = { controllerParentMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Aucun parent") },
                        onClick = { vm.setNullParent(parent.id, null); controllerParentMenuExpanded = false },
                    )
                    state.nullObjects.filter { it.id != parent.id }.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.name) },
                            onClick = { vm.setNullParent(parent.id, item.id); controllerParentMenuExpanded = false },
                        )
                    }
                }
            }
            Button(
                onClick = { vm.setNullKeyframeProperty(parent.id) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            ) { Text("◆ Ajouter une image clé au Null") }
            TransformSlider("Null · Position X", transform.x, -1000f..1000f) { vm.setNullKeyframeProperty(parent.id, x = it) }
            TransformSlider("Null · Position Y", transform.y, -1000f..1000f) { vm.setNullKeyframeProperty(parent.id, y = it) }
            TransformSlider("Null · Échelle", transform.scale, .1f..4f) { vm.setNullKeyframeProperty(parent.id, scale = it) }
            TransformSlider("Null · Rotation", transform.rotation, -360f..360f) { vm.setNullKeyframeProperty(parent.id, rotation = it) }
            TransformSlider("Null · Opacité", transform.opacity, 0f..1f) { vm.setNullKeyframeProperty(parent.id, opacity = it) }
        }
    }

    if (showClearPropertyDialog) {
        AlertDialog(
            onDismissRequest = { showClearPropertyDialog = false },
            title = { Text("Supprimer les images clés ?") },
            text = {
                Text("Supprimer toutes les images clés de « ${activeProperty.label} » ? La propriété garde sa valeur actuelle et les autres propriétés ne sont pas modifiées.")
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearKeyframes(activeProperty)
                    showClearPropertyDialog = false
                }) { Text("Tout supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showClearPropertyDialog = false }) { Text("Annuler") }
            },
        )
    }

    if (showCurveEditor) {
        val selectedSegment = currentSegment
        if (selectedSegment != null) {
            val index = segmentStarts.indexOfFirst { it.timeMs == selectedSegment.timeMs }.coerceAtLeast(0)
            KeyframeCurveDialog(
                initialEasing = selectedSegment.easingToNext,
                initialX1 = selectedSegment.curveX1,
                initialY1 = selectedSegment.curveY1,
                initialX2 = selectedSegment.curveX2,
                initialY2 = selectedSegment.curveY2,
                segmentIndex = index,
                segmentCount = segmentStarts.size,
                onSelectSegment = { newIndex -> segmentOverrideMs = segmentStarts.getOrNull(newIndex)?.timeMs },
                onDismiss = { showCurveEditor = false },
                onApply = { newEasing, x1, y1, x2, y2 ->
                    vm.updateKeyframeCurve(
                        property = activeProperty,
                        keyframeTimeMs = selectedSegment.timeMs,
                        easing = newEasing,
                        curveX1 = x1,
                        curveY1 = y1,
                        curveX2 = x2,
                        curveY2 = y2,
                        alsoUseAsDefault = true,
                    )
                    showCurveEditor = false
                },
            )
        } else {
            LaunchedEffect(clip.id) { showCurveEditor = false }
        }
    }
}

/** One of the four keyframe commands: highlighted = primary/selected action, disabled = nothing to do right now. */
@Composable
private fun KeyframeButton(
    text: String,
    description: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val buttonModifier = modifier.heightIn(min = 48.dp).semantics { contentDescription = description }
    val padding = PaddingValues(horizontal = 8.dp)
    if (highlighted) {
        FilledTonalButton(onClick = onClick, enabled = enabled, modifier = buttonModifier, contentPadding = padding) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = buttonModifier, contentPadding = padding) { Text(text) }
    }
}

@Composable
private fun KeyframeTrackStrip(
    keys: List<AnimatedKeyframe>,
    selectedMs: Long?,
    sourceStartMs: Long,
    sourceEndMs: Long,
    timelineStartMs: Long,
    playheadMs: Long,
    onSeek: (Long) -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val keyColor = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val cursorColor = MaterialTheme.colorScheme.tertiary
    val span = (sourceEndMs - sourceStartMs).coerceAtLeast(1L).toFloat()
    Canvas(
        Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 18.dp).pointerInput(
            keys, sourceStartMs, sourceEndMs, timelineStartMs,
        ) {
            detectTapGestures { offset ->
                val width = size.width.toFloat().coerceAtLeast(1f)
                val tolerance = 16.dp.toPx()
                // A tap close to a diamond lands exactly on it; elsewhere it seeks proportionally.
                val nearest = keys
                    .filter { it.timeMs in sourceStartMs..sourceEndMs }
                    .minByOrNull { abs((it.timeMs - sourceStartMs) / span * width - offset.x) }
                val local = if (nearest != null && abs((nearest.timeMs - sourceStartMs) / span * width - offset.x) <= tolerance) {
                    nearest.timeMs
                } else {
                    sourceStartMs + (span * (offset.x / width).coerceIn(0f, 1f)).toLong()
                }
                onSeek((timelineStartMs + local - sourceStartMs).coerceAtLeast(0L))
            }
        },
    ) {
        val width = size.width
        val midY = size.height / 2f
        val stroke = 2.dp.toPx()
        drawLine(lineColor, Offset(0f, midY), Offset(width, midY), strokeWidth = stroke)
        // Keyframes outside the trimmed clip are kept in the project but not shown here.
        keys.filter { it.timeMs in sourceStartMs..sourceEndMs }.forEach { frame ->
            val x = ((frame.timeMs - sourceStartMs).toFloat() / span).coerceIn(0f, 1f) * width
            val selected = frame.timeMs == selectedMs
            val d = if (selected) 9.dp.toPx() else 6.dp.toPx()
            val diamond = Path().apply {
                moveTo(x, midY - d)
                lineTo(x + d, midY)
                lineTo(x, midY + d)
                lineTo(x - d, midY)
                close()
            }
            drawPath(diamond, if (selected) selectedColor else keyColor)
        }
        val sourceCursor = playheadMs - timelineStartMs + sourceStartMs
        val cursorX = ((sourceCursor - sourceStartMs).toFloat() / span).coerceIn(0f, 1f) * width
        drawLine(cursorColor, Offset(cursorX, 1.dp.toPx()), Offset(cursorX, size.height - 1.dp.toPx()), strokeWidth = 2.dp.toPx())
    }
}

@Composable
private fun TransformSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    active: Boolean = false,
    onActivate: () -> Unit = {},
    onChange: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onActivate).padding(horizontal = 20.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("%.2f".format(value), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Slider(
        value = value.coerceIn(range.start, range.endInclusive),
        onValueChange = {
            onActivate()
            onChange(it)
        },
        valueRange = range,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
