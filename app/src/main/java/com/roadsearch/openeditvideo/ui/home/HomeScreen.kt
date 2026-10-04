package com.roadsearch.openeditvideo.ui.home

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.roadsearch.openeditvideo.ui.components.MotionFlowMark
import com.roadsearch.openeditvideo.ui.theme.LocalUiScale
import com.roadsearch.openeditvideo.ui.theme.MfColors
import com.roadsearch.openeditvideo.ui.theme.sc
import com.roadsearch.openeditvideo.ui.theme.uiScaleFor

/** Actions the Home screen can trigger. Navigation and persistence stay outside the composable. */
@Stable
class HomeActions(
    val onNewProject: () -> Unit,
    val onImportMedia: (Uri, String) -> Unit,
    val onOpenProject: (String) -> Unit,
    val onSeeAll: () -> Unit,
    val onTemplates: () -> Unit,
    val onAiTools: () -> Unit,
    val onRename: (String, String) -> Unit,
    val onDuplicate: (String) -> Unit,
    val onDelete: (String) -> Unit,
)

private const val RECENT_COUNT = 4

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showPro by remember { mutableStateOf(false) }

    fun displayName(uri: Uri, fallback: String): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: fallback

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { actions.onImportMedia(it, displayName(it, "Vidéo")) }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.data?.let { actions.onImportMedia(it, "Capture") }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val scale = uiScaleFor(maxWidth.value)
        CompositionLocalProvider(LocalUiScale provides scale) {
            LazyColumn(
                Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxSize().statusBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp.sc(), vertical = 8.dp.sc()),
            ) {
                item(key = "header") { HomeHeader(onPro = { showPro = true }) }
                item(key = "new") { Spacer(Modifier.height(18.dp.sc())); NewProjectButton(actions.onNewProject) }
                item(key = "tools") {
                    Spacer(Modifier.height(14.dp.sc()))
                    QuickToolsRow { tool ->
                        when (tool) {
                            QuickTool.CAMERA -> try {
                                cameraLauncher.launch(Intent(MediaStore.ACTION_VIDEO_CAPTURE))
                            } catch (_: ActivityNotFoundException) { /* no camera app available */ }
                            QuickTool.AI -> actions.onAiTools()
                            QuickTool.TEMPLATES -> actions.onTemplates()
                            QuickTool.IMPORT -> importPicker.launch(arrayOf("video/*", "image/*"))
                        }
                    }
                }
                item(key = "recent-header") {
                    Spacer(Modifier.height(24.dp.sc()))
                    SectionHeader("Projets récents", action = if (state.projects.size > RECENT_COUNT) "Tout voir" else null, onAction = actions.onSeeAll)
                    Spacer(Modifier.height(6.dp.sc()))
                }
                if (state.projects.isEmpty() && !state.loading) {
                    item(key = "empty") { EmptyProjects() }
                } else {
                    items(state.projects.take(RECENT_COUNT), key = { it.id }) { p ->
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

    if (showPro) {
        AlertDialog(
            onDismissRequest = { showPro = false },
            containerColor = MfColors.CardHigh,
            title = { Text("MotionFlow Pro", color = Color.White) },
            text = { Text("L'abonnement Pro n'est pas encore disponible.", color = MfColors.TextSecondary) },
            confirmButton = { TextButton({ showPro = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun HomeHeader(onPro: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        MotionFlowMark(size = 26.dp.sc())
        Spacer(Modifier.width(8.dp))
        Text("MotionFlow", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        ProBadge(onPro)
    }
}
