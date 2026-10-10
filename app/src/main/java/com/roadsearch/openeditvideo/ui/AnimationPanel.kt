package com.roadsearch.openeditvideo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.model.AnimatedKeyframe
import com.roadsearch.openeditvideo.model.AnimatedProperty
import com.roadsearch.openeditvideo.model.Easing
import com.roadsearch.openeditvideo.model.keyframes
import com.roadsearch.openeditvideo.model.keyframesAt
import com.roadsearch.openeditvideo.model.selectedClip
import com.roadsearch.openeditvideo.model.end
import com.roadsearch.openeditvideo.model.effectiveAnimation

@Composable
fun AnimationPanel(vm: EditorViewModel) {
    val state by vm.state.collectAsState()
    val clip = state.selectedClip() ?: return
    var easing by remember(clip.id, state.easing) { mutableStateOf(state.easing) }
    var activeProperty by remember(clip.id) { mutableStateOf(AnimatedProperty.SCALE) }
    var showCurveEditor by remember(clip.id) { mutableStateOf(false) }
    var showClearPropertyDialog by remember(clip.id) { mutableStateOf(false) }
    val keyframes = clip.effectiveAnimation().keyframes(activeProperty).sortedBy { it.timeMs }
    val sourceEnd = clip.end(state.durationMs).coerceAtLeast(clip.startMs + 1L)
    val sourceTime = (state.positionMs - clip.timelineStartMs + clip.startMs)
        .coerceIn(clip.startMs, sourceEnd)
    val keyExistsAtPlayhead = keyframes.any { it.timeMs == sourceTime }
    val lastAtOrBefore = keyframes.indexOfLast { it.timeMs <= sourceTime }
    val curveStart = if (lastAtOrBefore == keyframes.lastIndex && lastAtOrBefore > 0) keyframes[lastAtOrBefore - 1] else keyframes.getOrNull(lastAtOrBefore) ?: keyframes.firstOrNull()

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Animation", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Rounded.Animation, contentDescription = "Animation par images clés")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Curseur : " + formatTime(state.positionMs) + "  •  " + activeProperty.label,
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.bodyMedium,
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AnimatedProperty.values().forEach { property ->
                FilterChip(
                    selected = property == activeProperty,
                    onClick = { activeProperty = property },
                    label = { Text(property.label) },
                )
            }
        }

        KeyframeTrackStrip(
            keys = keyframes,
            sourceStartMs = clip.startMs,
            sourceEndMs = sourceEnd,
            timelineStartMs = clip.timelineStartMs,
            playheadMs = state.positionMs,
            onSeek = vm::seekTo,
        )
        Text(
            keyframes.size.toString() + " keyframe(s) sur cette propriété",
            modifier = Modifier.padding(horizontal = 20.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(
                onClick = { vm.seekToPreviousKeyframe(activeProperty) },
                modifier = Modifier.weight(0.8f),
            ) { Text("◂ ◆") }
            OutlinedButton(
                onClick = {
                    val current = clip.keyframesAt(state.positionMs)
                    when (activeProperty) {
                        AnimatedProperty.X -> vm.setKeyframeProperty(x = current.x)
                        AnimatedProperty.Y -> vm.setKeyframeProperty(y = current.y)
                        AnimatedProperty.SCALE -> vm.setKeyframeProperty(scale = current.scale)
                        AnimatedProperty.ROTATION -> vm.setKeyframeProperty(rotation = current.rotation)
                        AnimatedProperty.OPACITY -> vm.setKeyframeProperty(opacity = current.opacity)
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !keyExistsAtPlayhead,
            ) { Text("◆+") }
            OutlinedButton(
                onClick = { vm.removeKeyframeAtPlayhead(activeProperty) },
                modifier = Modifier.weight(1f),
                enabled = keyExistsAtPlayhead,
            ) { Text("◆−") }
            OutlinedButton(
                onClick = { vm.seekToNextKeyframe(activeProperty) },
                modifier = Modifier.weight(0.8f),
            ) { Text("◆ ▸") }

        Spacer(Modifier.height(8.dp))
        Text("Interpolation rapide", modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelLarge)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(Easing.LINEAR, Easing.EASE_IN, Easing.EASE_OUT, Easing.EASE_IN_OUT).forEach { option ->
                FilterChip(
                    selected = option == easing,
                    onClick = {
                        easing = option
                        vm.setEasing(option)
                        vm.setSegmentEasing(activeProperty, option)
                    },
                    label = { Text(option.name.replace('_', ' ')) },
                )
            }
            OutlinedButton(
                onClick = { showCurveEditor = true },
                enabled = keyframes.size >= 2,
            ) { Text("Courbe avancée") }
        }

        Spacer(Modifier.height(8.dp))
        TransformSlider(
            label = "Position X",
            value = clip.keyframesAt(state.positionMs).x,
            range = -1000f..1000f,
            active = activeProperty == AnimatedProperty.X,
            onActivate = { activeProperty = AnimatedProperty.X },
        ) { vm.setKeyframeProperty(x = it) }
        TransformSlider(
            label = "Position Y",
            value = clip.keyframesAt(state.positionMs).y,
            range = -1000f..1000f,
            active = activeProperty == AnimatedProperty.Y,
            onActivate = { activeProperty = AnimatedProperty.Y },
        ) { vm.setKeyframeProperty(y = it) }
        TransformSlider(
            label = "Échelle",
            value = clip.keyframesAt(state.positionMs).scale,
            range = 0.1f..4f,
            active = activeProperty == AnimatedProperty.SCALE,
            onActivate = { activeProperty = AnimatedProperty.SCALE },
        ) { vm.setKeyframeProperty(scale = it) }
        TransformSlider(
            label = "Rotation",
            value = clip.keyframesAt(state.positionMs).rotation,
            range = -360f..360f,
            active = activeProperty == AnimatedProperty.ROTATION,
            onActivate = { activeProperty = AnimatedProperty.ROTATION },
        ) { vm.setKeyframeProperty(rotation = it) }
        TransformSlider(
            label = "Opacité",
            value = clip.keyframesAt(state.positionMs).opacity,
            range = 0f..1f,
            active = activeProperty == AnimatedProperty.OPACITY,
            onActivate = { activeProperty = AnimatedProperty.OPACITY },
        ) { vm.setKeyframeProperty(opacity = it) }
    }

    if (showClearPropertyDialog) {
        AlertDialog(
            onDismissRequest = { showClearPropertyDialog = false },
            title = { Text("Supprimer les images clés ?") },
            text = {
                Text("Supprimer toutes les images clés de « ${activeProperty.label} » ? Les autres propriétés ne seront pas modifiées.")
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
        val selectedSegment = curveStart
        if (selectedSegment != null && keyframes.size >= 2) {
            KeyframeCurveDialog(
                initialEasing = selectedSegment.easingToNext,
                initialX1 = selectedSegment.curveX1,
                initialY1 = selectedSegment.curveY1,
                initialX2 = selectedSegment.curveX2,
                initialY2 = selectedSegment.curveY2,
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
                    )
                    vm.setEasing(newEasing)
                    easing = newEasing
                    showCurveEditor = false
                },
            )
        } else {
            LaunchedEffect(clip.id) { showCurveEditor = false }
        }
    }
}

@Composable
private fun KeyframeTrackStrip(
    keys: List<AnimatedKeyframe>,
    sourceStartMs: Long,
    sourceEndMs: Long,
    timelineStartMs: Long,
    playheadMs: Long,
    onSeek: (Long) -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val keyColor = MaterialTheme.colorScheme.primary
    val cursorColor = MaterialTheme.colorScheme.tertiary
    Canvas(
        Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 18.dp).pointerInput(
            keys, sourceStartMs, sourceEndMs, timelineStartMs,
        ) {
            detectTapGestures { offset ->
                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                val local = sourceStartMs + ((sourceEndMs - sourceStartMs) * fraction).toLong()
                onSeek((timelineStartMs + local - sourceStartMs).coerceAtLeast(0L))
            }
        },
    ) {
        val width = size.width
        val midY = size.height / 2f
        val stroke = 2.dp.toPx()
        drawLine(lineColor, Offset(0f, midY), Offset(width, midY), strokeWidth = stroke)
        keys.forEach { frame ->
            val fraction = ((frame.timeMs - sourceStartMs).toFloat() / (sourceEndMs - sourceStartMs).coerceAtLeast(1L))
                .coerceIn(0f, 1f)
            val x = fraction * width
            val d = 5.dp.toPx()
            val diamond = Path().apply {
                moveTo(x, midY - d)
                lineTo(x + d, midY)
                lineTo(x, midY + d)
                lineTo(x - d, midY)
                close()
            }
            drawPath(diamond, keyColor)
        }
        val sourceCursor = playheadMs - timelineStartMs + sourceStartMs
        val cursorFraction = ((sourceCursor - sourceStartMs).toFloat() / (sourceEndMs - sourceStartMs).coerceAtLeast(1L))
            .coerceIn(0f, 1f)
        val cursorX = cursorFraction * width
        drawLine(cursorColor, Offset(cursorX, 1.dp.toPx()), Offset(cursorX, size.height - 1.dp.toPx()), strokeWidth = 2.dp.toPx())
    }
}

@Composable
private fun TransformSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    active: Boolean,
    onActivate: () -> Unit,
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
