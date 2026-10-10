package com.roadsearch.openeditvideo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.roadsearch.openeditvideo.model.Easing
import kotlin.math.pow

@Composable
fun KeyframeCurveDialog(
    initialEasing: Easing,
    initialX1: Float,
    initialY1: Float,
    initialX2: Float,
    initialY2: Float,
    onDismiss: () -> Unit,
    onApply: (Easing, Float, Float, Float, Float) -> Unit,
) {
    var easing by remember(initialEasing) { mutableStateOf(initialEasing) }
    var x1 by remember(initialX1) { mutableStateOf(initialX1.coerceIn(0f, 1f)) }
    var y1 by remember(initialY1) { mutableStateOf(initialY1.coerceIn(-0.25f, 1.25f)) }
    var x2 by remember(initialX2) { mutableStateOf(initialX2.coerceIn(0f, 1f)) }
    var y2 by remember(initialY2) { mutableStateOf(initialY2.coerceIn(-0.25f, 1.25f)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 740.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Éditeur de courbe", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Réglez la vitesse entre cette image clé et la suivante. Déplacez les poignées ou utilisez les valeurs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Easing.values().forEach { option ->
                        FilterChip(
                            selected = easing == option,
                            onClick = { easing = option },
                            label = { Text(option.labelFr()) },
                        )
                    }
                }

                BezierGraph(
                    x1 = x1,
                    y1 = y1,
                    x2 = x2,
                    y2 = y2,
                    onHandle1 = { x, y -> x1 = x; y1 = y },
                    onHandle2 = { x, y -> x2 = x; y2 = y },
                )

                Text(
                    "P1 contrôle le début • P2 contrôle l'arrivée",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CurveValueSlider("P1 — X", x1, 0f..1f) { x1 = it }
                CurveValueSlider("P1 — Y", y1, -0.25f..1.25f) { y1 = it }
                CurveValueSlider("P2 — X", x2, 0f..1f) { x2 = it }
                CurveValueSlider("P2 — Y", y2, -0.25f..1.25f) { y2 = it }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onApply(easing, x1, y1, x2, y2) }) { Text("Appliquer") }
                }
            }
        }
    }
}

@Composable
private fun BezierGraph(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    onHandle1: (Float, Float) -> Unit,
    onHandle2: (Float, Float) -> Unit,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val curveColor = MaterialTheme.colorScheme.primary
    val firstHandleColor = MaterialTheme.colorScheme.tertiary
    val secondHandleColor = MaterialTheme.colorScheme.secondary
    var activeHandle by remember { mutableStateOf(1) }

    Canvas(
        Modifier.fillMaxWidth().height(190.dp).pointerInput(x1, y1, x2, y2) {
            detectDragGestures(
                onDragStart = { position ->
                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    val first = Offset(x1 * width, (1f - y1) * height)
                    val second = Offset(x2 * width, (1f - y2) * height)
                    val d1 = (first.x - position.x).pow(2) + (first.y - position.y).pow(2)
                    val d2 = (second.x - position.x).pow(2) + (second.y - position.y).pow(2)
                    activeHandle = if (d1 <= d2) 1 else 2
                },
            ) { change, _ ->
                val width = size.width.toFloat().coerceAtLeast(1f)
                val height = size.height.toFloat().coerceAtLeast(1f)
                val x = (change.position.x / width).coerceIn(0f, 1f)
                val y = (1f - change.position.y / height).coerceIn(-0.25f, 1.25f)
                if (activeHandle == 1) onHandle1(x, y) else onHandle2(x, y)
                change.consume()
            }
        },
    ) {
        val width = size.width
        val height = size.height
        val start = Offset(0f, height)
        val end = Offset(width, 0f)
        val first = Offset(x1 * width, (1f - y1) * height)
        val second = Offset(x2 * width, (1f - y2) * height)

        for (step in 1..3) {
            val fraction = step / 4f
            drawLine(gridColor, Offset(width * fraction, 0f), Offset(width * fraction, height), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(0f, height * fraction), Offset(width, height * fraction), strokeWidth = 1.dp.toPx())
        }
        drawLine(gridColor, start, end, strokeWidth = 1.dp.toPx())
        drawLine(firstHandleColor.copy(alpha = 0.8f), start, first, strokeWidth = 1.5.dp.toPx())
        drawLine(secondHandleColor.copy(alpha = 0.8f), end, second, strokeWidth = 1.5.dp.toPx())

        val path = Path().apply {
            moveTo(start.x, start.y)
            cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
        }
        drawPath(path, curveColor, style = Stroke(width = 3.dp.toPx()))
        drawCircle(firstHandleColor, radius = 7.dp.toPx(), center = first)
        drawCircle(secondHandleColor, radius = 7.dp.toPx(), center = second)
        drawCircle(curveColor, radius = 4.dp.toPx(), center = start)
        drawCircle(curveColor, radius = 4.dp.toPx(), center = end)
    }
}

@Composable
private fun CurveValueSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text("%.2f".format(value), style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

private fun Easing.labelFr(): String = when (this) {
    Easing.LINEAR -> "Linéaire"
    Easing.EASE_IN -> "Accélérer"
    Easing.EASE_OUT -> "Ralentir"
    Easing.EASE_IN_OUT -> "Fluide"
    Easing.CUBIC_BEZIER -> "Bézier"
    Easing.HOLD -> "Palier"
    Easing.BOUNCE -> "Rebond"
    Easing.ELASTIC -> "Élastique"
    Easing.STEPS -> "Par étapes"
}
