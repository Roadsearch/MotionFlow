package com.roadsearch.openeditvideo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.model.Easing
import com.roadsearch.openeditvideo.model.TransformAnimation
import com.roadsearch.openeditvideo.model.AnimatedKeyframe
import com.roadsearch.openeditvideo.model.keyframesAt
import com.roadsearch.openeditvideo.model.selectedClip

@Composable
fun AnimationPanel(vm: EditorViewModel) {
    val state by vm.state.collectAsState()
    val clip = state.selectedClip() ?: return
    var easing by remember(clip.id, state.easing) { mutableStateOf(state.easing) }
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=20.dp), horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Animation", style=MaterialTheme.typography.titleLarge, color=MaterialTheme.colorScheme.onSurface)
            Icon(Icons.Rounded.Animation, null)
        }
        Spacer(Modifier.height(8.dp))
        Text("Keyframe à ${formatTime(state.positionMs)}", modifier=Modifier.padding(horizontal=20.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp), horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf(Easing.LINEAR,Easing.EASE_IN,Easing.EASE_OUT,Easing.EASE_IN_OUT).forEach { e ->
                FilterChip(selected=e==easing,onClick={easing=e;vm.setEasing(e)},label={Text(e.name.replace('_',' '))})
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button({ vm.setKeyframeProperty() }, modifier=Modifier.weight(1f)) { Text("◆ Ajouter") }
            OutlinedButton({ vm.removeKeyframeAtPlayhead() }, modifier=Modifier.weight(1f)) { Icon(Icons.Rounded.DeleteOutline,null); Spacer(Modifier.width(4.dp)); Text("Supprimer") }
        }
        Spacer(Modifier.height(8.dp))
        TransformSlider("Position X", clip.keyframesAt(state.positionMs).x, -1000f..1000f) { vm.setKeyframeProperty(x=it) }
        TransformSlider("Position Y", clip.keyframesAt(state.positionMs).y, -1000f..1000f) { vm.setKeyframeProperty(y=it) }
        TransformSlider("Échelle", clip.keyframesAt(state.positionMs).scale, .1f..4f) { vm.setKeyframeProperty(scale=it) }
        TransformSlider("Rotation", clip.keyframesAt(state.positionMs).rotation, -360f..360f) { vm.setKeyframeProperty(rotation=it) }
        TransformSlider("Opacité", clip.keyframesAt(state.positionMs).opacity, 0f..1f) { vm.setKeyframeProperty(opacity=it) }
    }
}

@Composable private fun TransformSlider(label:String,value:Float,range:ClosedFloatingPointRange<Float>,onChange:(Float)->Unit){
    Text("$label ${"%.2f".format(value)}",modifier=Modifier.padding(horizontal=20.dp))
    Slider(value=value.coerceIn(range.start,range.endInclusive),onValueChange=onChange,valueRange=range,modifier=Modifier.padding(horizontal=16.dp))
}
