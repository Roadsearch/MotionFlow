package com.roadsearch.openeditvideo.ui.home

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class RecentProject(
    val id: String,
    val name: String,
    val durationMs: Long,
    val resolutionLabel: String,
    val updatedAt: Long,
    val thumbnailUri: Uri?,
)

@Immutable
data class HomeUiState(
    val projects: List<RecentProject> = emptyList(),
    val loading: Boolean = true,
)
