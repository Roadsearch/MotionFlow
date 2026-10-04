package com.roadsearch.openeditvideo.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.roadsearch.openeditvideo.ui.components.MainTab
import com.roadsearch.openeditvideo.ui.components.MotionBottomBar
import com.roadsearch.openeditvideo.ui.home.HomeActions
import com.roadsearch.openeditvideo.ui.home.HomeScreen
import com.roadsearch.openeditvideo.ui.home.HomeUiState
import com.roadsearch.openeditvideo.ui.theme.MfColors

/** App shell: content area + bottom navigation (Accueil / Projets / Modèles / Moi). */
@Composable
fun MainShell(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    var aiOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = aiOpen) { aiOpen = false }

    val shellActions = HomeActions(
        onNewProject = actions.onNewProject,
        onImportMedia = actions.onImportMedia,
        onOpenProject = actions.onOpenProject,
        onSeeAll = { tab = MainTab.PROJECTS },
        onTemplates = { tab = MainTab.TEMPLATES },
        onAiTools = { aiOpen = true },
        onRename = actions.onRename,
        onDuplicate = actions.onDuplicate,
        onDelete = actions.onDelete,
    )

    Column(modifier.fillMaxSize().background(MfColors.Background)) {
        Box(Modifier.weight(1f)) {
            if (aiOpen) PlaceholderScreen("Outils IA") else Crossfade(tab, label = "tab") { current ->
                when (current) {
                    MainTab.HOME -> HomeScreen(state, shellActions)
                    MainTab.PROJECTS -> ProjectsScreen(state, shellActions)
                    MainTab.TEMPLATES -> PlaceholderScreen("Modèles")
                    MainTab.PROFILE -> PlaceholderScreen("Moi")
                }
            }
        }
        MotionBottomBar(selected = tab, onSelect = { aiOpen = false; tab = it })
    }
}
