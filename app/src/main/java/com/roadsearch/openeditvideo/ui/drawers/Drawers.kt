package com.roadsearch.openeditvideo.ui.drawers

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.roadsearch.openeditvideo.model.EditorUiState
import com.roadsearch.openeditvideo.ui.EditorViewModel
import com.roadsearch.openeditvideo.ui.theme.MfColors

/** Contextual bottom drawers of the editor. */
internal enum class Drawer { AUDIO, TEXT_NEW, TEXT_EDIT, MASK, AI, EXPORT, FILTERS, HELP, COVER }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorDrawers(
    drawer: Drawer?,
    onDismiss: () -> Unit,
    state: EditorUiState,
    vm: EditorViewModel,
    pickAudioFile: () -> Unit,
) {
    if (drawer == null) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MfColors.Surface,
        contentColor = Color.White,
    ) {
        when (drawer) {
            Drawer.AUDIO -> AudioDrawer(vm, onPickFile = { pickAudioFile(); onDismiss() }, onClose = onDismiss)
            Drawer.TEXT_NEW -> TextDrawer(state, vm, editing = false, onClose = onDismiss)
            Drawer.TEXT_EDIT -> TextDrawer(state, vm, editing = true, onClose = onDismiss)
            Drawer.MASK -> MaskDrawer(state, vm, onClose = onDismiss)
            Drawer.AI -> AiToolsDrawer(onClose = onDismiss)
            Drawer.EXPORT -> ExportDrawer(state, vm, onClose = onDismiss)
            Drawer.FILTERS -> FiltersDrawer(state, vm, onClose = onDismiss)
            Drawer.HELP -> HelpDrawer(onClose = onDismiss)
            Drawer.COVER -> CoverDrawer(state, vm, onClose = onDismiss)
        }
    }
}
