package com.roadsearch.openeditvideo.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.data.EditorStateCodec
import com.roadsearch.openeditvideo.data.ProjectEntity
import com.roadsearch.openeditvideo.data.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Presentation state for Home/Projects. Knows nothing about Media3: it only reads persisted project documents. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: ProjectRepository,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = repository.observeAll()
        .map { list -> HomeUiState(projects = list.mapNotNull { it.toRecent() }, loading = false) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    suspend fun create(name: String = "Nouveau projet"): String = repository.create(name)
    fun open(id: String) = repository.open(id)
    fun rename(id: String, name: String) { viewModelScope.launch { repository.rename(id, name) } }
    fun duplicate(id: String) { viewModelScope.launch { repository.duplicate(id) } }
    fun delete(id: String) { viewModelScope.launch { repository.delete(id) } }

    private fun ProjectEntity.toRecent(): RecentProject? {
        val doc = runCatching { EditorStateCodec.decode(documentJson) }.getOrNull()
        // Hide the legacy auto-created blank "default" project.
        if (id == ProjectRepository.DEFAULT_PROJECT_ID && (doc == null || (doc.clips.isEmpty() && doc.audioClips.isEmpty()))) return null
        val clips = doc?.clips.orEmpty()
        val duration = clips.maxOfOrNull { it.timelineStartMs + TimelineMath.duration(it, it.sourceDurationMs) } ?: 0L
        val first = clips.filter { it.track == 0 }.minByOrNull { it.timelineStartMs } ?: clips.firstOrNull()
        val cover = doc?.coverMs ?: 0L
        val mainTrack = clips.minOfOrNull { it.track }
        val coverClip = clips.filter { it.track == mainTrack }.firstOrNull { c ->
            val length = TimelineMath.duration(c, c.sourceDurationMs.coerceAtLeast(doc?.durationMs ?: 0L))
            cover >= c.timelineStartMs && cover < c.timelineStartMs + length
        } ?: first
        val thumbMs = coverClip?.let { it.startMs + (cover - it.timelineStartMs).coerceAtLeast(0L) } ?: 0L
        return RecentProject(id, name, duration, "1080p", updatedAt, coverClip?.uri, thumbMs)
    }
}
