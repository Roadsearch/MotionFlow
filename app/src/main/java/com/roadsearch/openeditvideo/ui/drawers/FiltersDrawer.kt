package com.roadsearch.openeditvideo.ui.drawers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.model.VideoFilter
import com.roadsearch.openeditvideo.ui.EditorViewModel

@Composable
internal fun FiltersDrawer(state: EditorUiState, vm: EditorViewModel, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 24.dp)) {
        DrawerHeader("Filtres", onClose)
        if (state.clips.none { it.id == state.selectedClipId }) {
            EmptyNote("Sélectionnez un clip dans la timeline pour lui appliquer un filtre.")
        } else {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.effects.filter == f,
                        onClick = { vm.setEffects(filter = f) },
                        label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }.replace('_', ' ')) },
                    )
                }
            }
        }
    }
}
