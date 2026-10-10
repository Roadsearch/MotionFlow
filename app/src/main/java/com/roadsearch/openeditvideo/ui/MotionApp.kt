package com.roadsearch.openeditvideo.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roadsearch.openeditvideo.ui.home.HomeActions
import com.roadsearch.openeditvideo.ui.home.HomeViewModel
import com.roadsearch.openeditvideo.ui.shell.MainShell
import com.roadsearch.openeditvideo.ui.theme.MfColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import com.roadsearch.openeditvideo.media.ProjectBackgrounds
import com.roadsearch.openeditvideo.ui.home.NewProjectSheet

/**
 * Root composable: Home shell <-> Editor.
 * The editor ViewModel stays activity-scoped (it owns the Media3 pipeline and debounced autosave) and is
 * re-pointed at a project through [HomeViewModel.open]; presentation state for Home lives in [HomeViewModel].
 */
@Composable
fun MotionApp(editorVm: EditorViewModel, homeVm: HomeViewModel = viewModel()) {
    val homeState by homeVm.state.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNewProject by rememberSaveable { mutableStateOf(false) }
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()

    LaunchedEffect(openId) { openId?.let(homeVm::open) }

    fun closeEditor() { editorVm.flushSave(); openId = null }
    BackHandler(enabled = openId != null) { closeEditor() }

    val actions = remember(homeVm, editorVm) {
        HomeActions(
            onNewProject = { showNewProject = true }, // a photo/video or a background is required first
            onImportMedia = { uri, name ->
                scope.launch {
                    val id = homeVm.create(name.substringBeforeLast('.').ifBlank { "Nouveau projet" })
                    editorVm.queueImport(uri, name)
                    openId = id
                }
            },
            onOpenProject = { openId = it },
            onSeeAll = {}, onTemplates = {}, onAiTools = {}, // overridden by MainShell (tab navigation)
            onRename = homeVm::rename,
            onDuplicate = homeVm::duplicate,
            onDelete = homeVm::delete,
        )
    }

    Box(Modifier.fillMaxSize().background(MfColors.Background)) {
        AnimatedContent(
            targetState = openId != null,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "route",
        ) { editing ->
            if (editing) Box(Modifier.fillMaxSize().systemBarsPadding()) { EditorScreen(editorVm, onBack = ::closeEditor) }
            else MainShell(homeState, actions)
        }

        if (showNewProject) {
            NewProjectSheet(
                onDismiss = { showNewProject = false },
                onMedia = { uri, name ->
                    showNewProject = false
                    actions.onImportMedia(uri, name)
                },
                onBackground = { preset, aspect ->
                    showNewProject = false
                    scope.launch {
                        val uri = withContext(Dispatchers.IO) { ProjectBackgrounds.uriFor(appContext, preset, aspect) }
                        val id = homeVm.create()
                        editorVm.queueImport(uri, "Fond ${preset.label}", aspect)
                        openId = id
                    }
                },
            )
        }
    }
}
