package com.roadsearch.openeditvideo.ui.shell

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.ui.home.EmptyProjects
import com.roadsearch.openeditvideo.ui.home.HomeActions
import com.roadsearch.openeditvideo.ui.home.HomeUiState
import com.roadsearch.openeditvideo.ui.home.RecentProjectRow
import com.roadsearch.openeditvideo.ui.theme.LocalUiScale
import com.roadsearch.openeditvideo.ui.theme.sc
import com.roadsearch.openeditvideo.ui.theme.uiScaleFor

@Composable
fun ProjectsScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalUiScale provides uiScaleFor(maxWidth.value)) {
            LazyColumn(
                Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp.sc(), vertical = 12.dp.sc()),
            ) {
                item(key = "title") { Text("PROJETS", color = Color.White, style = MaterialTheme.typography.headlineLarge) }
                if (state.projects.isEmpty() && !state.loading) item(key = "empty") { EmptyProjects() }
                items(state.projects, key = { it.id }) { p ->
                    RecentProjectRow(
                        p,
                        onOpen = { actions.onOpenProject(p.id) },
                        onRename = { actions.onRename(p.id, it) },
                        onDuplicate = { actions.onDuplicate(p.id) },
                        onDelete = { actions.onDelete(p.id) },
                    )
                }
            }
        }
    }
}
