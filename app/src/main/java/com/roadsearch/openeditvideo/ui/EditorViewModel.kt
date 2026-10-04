package com.roadsearch.openeditvideo.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.roadsearch.openeditvideo.core.TimelineMath
import com.roadsearch.openeditvideo.data.ProjectRepository
import com.roadsearch.openeditvideo.data.EditorStateCodec
import com.roadsearch.openeditvideo.export.ExportKeys
import com.roadsearch.openeditvideo.export.MediaSourceValidator
import com.roadsearch.openeditvideo.export.VideoExportWorker
import com.roadsearch.openeditvideo.media.MediaProbe
import com.roadsearch.openeditvideo.media.MediaEngine
import com.roadsearch.openeditvideo.model.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class EditorViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val repository: ProjectRepository,
    private val mediaEngine: MediaEngine,
    private val workManager: WorkManager,
) : ViewModel() {
    companion object {
        private const val MAX_HISTORY = 100
        const val EXPORT_WORK_NAME = ExportKeys.EXPORT_WORK_NAME
    }

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state

    private var hydrated = false
    private var hydratedId: String? = null
    private var pendingImport: Pair<Uri, String>? = null

    private data class Snapshot(
        val clips: List<VideoClip>,
        val audio: List<AudioClip>,
        val text: List<TextOverlay>,
        val selected: Long?,
        val selectedIds: Set<Long>,
        val zoom: Float,
        val effects: EffectSettings,
        val transitions: List<Transition>,
        val easing: Easing,
        val masks: Map<Long, MaskSettings>,
        val blendModes: Map<Long, BlendMode>,
        val chromaKeys: Map<Long, ChromaKeySettings>,
        val markers: List<Marker>,
        val trackStates: Map<Int, TrackState>,
        val snappingEnabled: Boolean,
    )

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()

    init {
        viewModelScope.launch {
            repository.observe().collectLatest { entity ->
                if (entity == null || hydratedId == entity.id) return@collectLatest
                // New project: swap the whole editor state. No suspension point below, so the save pipeline
                // always tags the restored state with the new id.
                hydrated = false
                undoStack.clear(); redoStack.clear()
                _state.value = runCatching { EditorStateCodec.decode(entity.documentJson) }.getOrNull() ?: EditorUiState()
                hydratedId = entity.id
                hydrated = true
                pendingImport?.let { (uri, name) -> pendingImport = null; import(uri, name) }
            }
        }

        viewModelScope.launch {
            state
                .drop(1)
                .map { EditorStateCodec.encode(it) to hydratedId }
                .distinctUntilChanged()
                .debounce(650L)
                .collectLatest { (json, id) ->
                    if (hydrated && id != null) repository.saveJson(json, id)
                }
        }

        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(EXPORT_WORK_NAME).collectLatest { infos ->
                val info = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                    ?: infos.firstOrNull()
                    ?: return@collectLatest
                val progress = info.progress.getInt(ExportKeys.PROGRESS, -1)
                when (info.state) {
                    WorkInfo.State.ENQUEUED -> _state.update {
                        it.copy(exportProgress = if (progress >= 0) progress / 100f else 0f, exportMessage = "Export en attente…")
                    }
                    WorkInfo.State.RUNNING -> _state.update {
                        it.copy(exportProgress = if (progress >= 0) progress / 100f else 0f, exportMessage = "Export de la vidéo…")
                    }
                    WorkInfo.State.SUCCEEDED -> _state.update {
                        it.copy(exportProgress = 1f, exportMessage = "Vidéo exportée dans la galerie")
                    }
                    WorkInfo.State.FAILED -> _state.update {
                        it.copy(exportProgress = null, exportMessage = info.outputData.getString(ExportKeys.ERROR) ?: "Erreur d’export")
                    }
                    WorkInfo.State.CANCELLED -> _state.update {
                        it.copy(exportProgress = null, exportMessage = "Export annulé")
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun snapshot(s: EditorUiState = _state.value) = Snapshot(
        clips = s.clips,
        audio = s.audioClips,
        text = s.textOverlays,
        selected = s.selectedClipId,
        selectedIds = s.selectedClipIds,
        zoom = s.zoom,
        effects = s.effects,
        transitions = s.transitions,
        easing = s.easing,
        masks = s.masks,
        blendModes = s.blendModes,
        chromaKeys = s.chromaKeys,
        markers = s.markers,
        trackStates = s.trackStates,
        snappingEnabled = s.snappingEnabled,
    )

    private fun record() {
        undoStack.addLast(snapshot())
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
    }

    private fun restore(s: Snapshot) {
        _state.update {
            it.copy(
                clips = s.clips,
                audioClips = s.audio,
                textOverlays = s.text,
                selectedClipId = s.selected,
                selectedClipIds = s.selectedIds,
                zoom = s.zoom,
                effects = s.effects,
                transitions = s.transitions,
                easing = s.easing,
                masks = s.masks,
                blendModes = s.blendModes,
                chromaKeys = s.chromaKeys,
                markers = s.markers,
                trackStates = s.trackStates,
                snappingEnabled = s.snappingEnabled,
            )
        }
    }

    /** Imports [uri] as soon as the project being opened has been loaded (avoids racing the hydration). */
    fun queueImport(uri: Uri, name: String) { pendingImport = uri to name }

    /** Writes the current state immediately (call before leaving the editor; the debounced save may still be pending). */
    fun flushSave() {
        val id = hydratedId ?: return
        val json = EditorStateCodec.encode(_state.value)
        viewModelScope.launch { repository.saveJson(json, id) }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.addLast(snapshot())
            restore(undoStack.removeLast())
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.addLast(snapshot())
            restore(redoStack.removeLast())
        }
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()

    fun import(uri: Uri, name: String, audioOnly: Boolean = false) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val id = System.nanoTime()
        val context = app
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val duration = MediaProbe.durationMs(context, uri)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                record()
                _state.update { s ->
                    val insertionPoint = s.totalTimelineDuration()
                    if (audioOnly) {
                        s.copy(
                            audioClips = s.audioClips + AudioClip(
                                id, uri, name, endMs = duration, timelineStartMs = insertionPoint,
                            ),
                        )
                    } else {
                        s.copy(
                            clips = s.clips + VideoClip(
                                id = id,
                                uri = uri,
                                name = name,
                                endMs = duration,
                                sourceDurationMs = duration,
                                timelineStartMs = insertionPoint,
                            ),
                            selectedClipId = id,
                            selectedClipIds = setOf(id),
                            positionMs = insertionPoint,
                            durationMs = maxOf(s.durationMs, insertionPoint + duration),
                        )
                    }
                }
            }
        }
    }

    fun select(id: Long) = _state.update { state ->
        val clip = state.clips.firstOrNull { it.id == id }
        val clipEnd = clip?.let { it.timelineStartMs + TimelineMath.duration(it, it.sourceDurationMs.coerceAtLeast(state.durationMs)) }
        val inside = clip != null && state.positionMs >= clip.timelineStartMs && state.positionMs < (clipEnd ?: 0L)
        state.copy(
            selectedClipId = id,
            selectedClipIds = setOf(id),
            selectedAudioId = null,
            selectedTextId = null,
            // Keep the playhead where it is when it already sits inside the clip.
            positionMs = if (clip != null && !inside) clip.timelineStartMs else state.positionMs,
            effects = clip?.effects ?: state.effects,
        )
    }

    fun selectAudio(id: Long) = _state.update {
        it.copy(selectedAudioId = id, selectedTextId = null, selectedClipId = null, selectedClipIds = emptySet())
    }

    fun selectText(id: Long) = _state.update {
        it.copy(selectedTextId = id, selectedAudioId = null, selectedClipId = null, selectedClipIds = emptySet())
    }

    fun toggleSelect(id: Long) = _state.update { state ->
        val selected = state.selectedClipIds.toMutableSet()
        if (!selected.add(id)) selected.remove(id)
        state.copy(selectedClipIds = selected, selectedClipId = selected.lastOrNull())
    }

    fun tool(tool: Tool) = _state.update { it.copy(activeTool = tool) }

    fun zoom(delta: Float) {
        record()
        _state.update { it.copy(zoom = (it.zoom + delta).coerceIn(.65f, 4f)) }
    }

    fun setZoom(value: Float) = _state.update { it.copy(zoom = value.coerceIn(.65f, 4f)) }

    private var gestureOpen = false

    fun beginEditGesture() {
        if (!gestureOpen) {
            record()
            gestureOpen = true
        }
    }

    fun commitEditGesture() { gestureOpen = false }

    fun cancelEditGesture() {
        if (gestureOpen && undoStack.isNotEmpty()) {
            restore(undoStack.removeLast())
            redoStack.clear()
        }
        gestureOpen = false
    }

    fun moveSelected(deltaMs: Long) = moveClip(_state.value.selectedClipId, deltaMs)

    fun moveClip(id: Long?, deltaMs: Long) {
        if (id == null) return
        val state = _state.value
        val clip = state.clips.firstOrNull { it.id == id } ?: return
        if (isTrackLocked(clip.track)) return
        if (!gestureOpen) record()
        val moved = TimelineMath.move(
            clip = clip,
            deltaMs = deltaMs,
            others = state.clips.filter { it.id != id && it.track == clip.track },
            fallbackDurationMs = state.durationMs,
        )
        _state.update { it.copy(clips = it.clips.map { item -> if (item.id == id) moved else item }) }
    }

    fun updateClip(clip: VideoClip) {
        record()
        _state.update { s -> s.copy(clips = s.clips.map { if (it.id == clip.id) clip else it }) }
    }

    fun setTimelineStart(id: Long, startMs: Long) {
        val current = _state.value.clips.firstOrNull { it.id == id }?.timelineStartMs ?: return
        moveClip(id, startMs - current)
    }

    fun trimLeft(id: Long, deltaMs: Long) {
        val state = _state.value
        val clip = state.clips.firstOrNull { it.id == id } ?: return
        if (isTrackLocked(clip.track)) return
        if (!gestureOpen) record()
        val trimmed = TimelineMath.clampTrimStart(clip, deltaMs, clip.sourceDurationMs.coerceAtLeast(state.durationMs))
        _state.update { it.copy(clips = it.clips.map { item -> if (item.id == id) trimmed else item }) }
    }

    fun trimRight(id: Long, deltaMs: Long) {
        val state = _state.value
        val clip = state.clips.firstOrNull { it.id == id } ?: return
        if (isTrackLocked(clip.track)) return
        if (!gestureOpen) record()
        val trimmed = TimelineMath.clampTrimEnd(clip, deltaMs, clip.sourceDurationMs.coerceAtLeast(state.durationMs))
        _state.update { it.copy(clips = it.clips.map { item -> if (item.id == id) trimmed else item }) }
    }

    // ---- Audio & text lanes: move / trim ------------------------------------

    fun moveAudio(id: Long, deltaMs: Long) {
        if (_state.value.audioClips.none { it.id == id }) return
        if (!gestureOpen) record()
        _state.update { s -> s.copy(audioClips = s.audioClips.map { if (it.id == id) it.copy(timelineStartMs = (it.timelineStartMs + deltaMs).coerceAtLeast(0L)) else it }) }
    }

    fun trimAudioLeft(id: Long, deltaMs: Long) {
        val a = _state.value.audioClips.firstOrNull { it.id == id } ?: return
        if (!gestureOpen) record()
        val end = if (a.endMs > a.startMs) a.endMs else a.sourceDurationMs
        val newStart = (a.startMs + deltaMs).coerceIn(0L, (end - TimelineMath.MIN_CLIP_DURATION_MS).coerceAtLeast(a.startMs))
        val shift = newStart - a.startMs
        _state.update { s -> s.copy(audioClips = s.audioClips.map { if (it.id == id) it.copy(startMs = newStart, endMs = end, timelineStartMs = (it.timelineStartMs + shift).coerceAtLeast(0L)) else it }) }
    }

    fun trimAudioRight(id: Long, deltaMs: Long) {
        val a = _state.value.audioClips.firstOrNull { it.id == id } ?: return
        if (!gestureOpen) record()
        val end = if (a.endMs > a.startMs) a.endMs else a.sourceDurationMs
        val minEnd = a.startMs + TimelineMath.MIN_CLIP_DURATION_MS
        val limit = if (a.sourceDurationMs > 0L) maxOf(a.sourceDurationMs, minEnd) else Long.MAX_VALUE
        val newEnd = (end + deltaMs).coerceIn(minEnd, limit)
        _state.update { s -> s.copy(audioClips = s.audioClips.map { if (it.id == id) it.copy(endMs = newEnd) else it }) }
    }

    fun moveText(id: Long, deltaMs: Long) {
        val t = _state.value.textOverlays.firstOrNull { it.id == id } ?: return
        if (!gestureOpen) record()
        val start = (t.startMs + deltaMs).coerceAtLeast(0L)
        val length = t.endMs - t.startMs
        _state.update { s -> s.copy(textOverlays = s.textOverlays.map { if (it.id == id) it.copy(startMs = start, endMs = start + length) else it }) }
    }

    fun trimTextLeft(id: Long, deltaMs: Long) {
        val t = _state.value.textOverlays.firstOrNull { it.id == id } ?: return
        if (!gestureOpen) record()
        val start = (t.startMs + deltaMs).coerceIn(0L, (t.endMs - TimelineMath.MIN_CLIP_DURATION_MS).coerceAtLeast(0L))
        _state.update { s -> s.copy(textOverlays = s.textOverlays.map { if (it.id == id) it.copy(startMs = start) else it }) }
    }

    fun trimTextRight(id: Long, deltaMs: Long) {
        val t = _state.value.textOverlays.firstOrNull { it.id == id } ?: return
        if (!gestureOpen) record()
        val end = (t.endMs + deltaMs).coerceAtLeast(t.startMs + TimelineMath.MIN_CLIP_DURATION_MS)
        _state.update { s -> s.copy(textOverlays = s.textOverlays.map { if (it.id == id) it.copy(endMs = end) else it }) }
    }

    fun toggleMute() = _state.update { it.copy(muted = !it.muted) }
    fun setPlaying(value: Boolean) = _state.update { it.copy(playing = value && it.timelineEndMs() > 0L) }
    fun setPosition(position: Long) = _state.update { it.copy(positionMs = position.coerceAtLeast(0L)) }
    fun setDuration(duration: Long) = _state.update { it.copy(durationMs = duration.coerceAtLeast(0L)) }
    fun seekTo(position: Long) = _state.update {
        val snapped = if (it.snappingEnabled) {
            TimelineMath.snapToMarkers(position, it.markers.map { m -> m.positionMs })
        } else {
            position
        }
        it.copy(positionMs = snapped.coerceAtLeast(0L), seekNonce = it.seekNonce + 1)
    }

    // ---- Markers, snapping & track state ------------------------------------

    fun isTrackLocked(track: Int): Boolean = _state.value.trackStates[track]?.locked == true

    fun toggleSnapping() = _state.update { it.copy(snappingEnabled = !it.snappingEnabled) }

    fun addMarkerAtPlayhead(label: String = "") {
        record()
        _state.update { s ->
            val marker = Marker(
                id = System.nanoTime(),
                positionMs = s.positionMs.coerceAtLeast(0L),
                label = label,
            )
            s.copy(markers = (s.markers + marker).sortedBy { it.positionMs })
        }
    }

    fun removeMarker(id: Long) {
        record()
        _state.update { s -> s.copy(markers = s.markers.filterNot { it.id == id }) }
    }

    fun seekToNextMarker() {
        val s = _state.value
        s.markers.firstOrNull { it.positionMs > s.positionMs }?.let { seekTo(it.positionMs + 1) }
    }

    fun seekToPreviousMarker() {
        val s = _state.value
        s.markers.lastOrNull { it.positionMs < s.positionMs }?.let { seekTo(it.positionMs) }
    }

    private fun updateTrackState(track: Int, transform: (TrackState) -> TrackState) {
        record()
        _state.update { s ->
            val current = s.trackStates[track] ?: TrackState()
            s.copy(trackStates = s.trackStates + (track to transform(current)))
        }
    }

    fun toggleTrackLock(track: Int) = updateTrackState(track) { it.copy(locked = !it.locked) }
    fun toggleTrackMute(track: Int) = updateTrackState(track) { it.copy(muted = !it.muted) }
    fun toggleTrackVisibility(track: Int) = updateTrackState(track) { it.copy(hidden = !it.hidden) }

    fun setEasing(easing: Easing) {
        record()
        _state.update { it.copy(easing = easing) }
    }

    fun setEffects(
        rotation: Float? = null,
        contrast: Float? = null,
        saturation: Float? = null,
        brightness: Float? = null,
        hue: Float? = null,
        blur: Float? = null,
        filter: VideoFilter? = null,
    ) {
        val id = _state.value.selectedClipId ?: return
        record()
        _state.update { s ->
            val current = s.clips.firstOrNull { it.id == id }?.effects ?: s.effects
            val next = current.copy(
                rotation = rotation ?: current.rotation,
                contrast = contrast ?: current.contrast,
                saturation = saturation ?: current.saturation,
                brightness = brightness ?: current.brightness,
                hue = hue ?: current.hue,
                blur = blur ?: current.blur,
                filter = filter ?: current.filter,
            )
            s.copy(
                effects = next,
                clips = s.clips.map { clip -> if (clip.id == id) clip.copy(effects = next) else clip },
            )
        }
    }

    fun setMask(value: MaskSettings) {
        val id = _state.value.selectedClipId ?: return
        record()
        _state.update { it.copy(masks = it.masks + (id to value)) }
    }

    fun setBlendMode(value: BlendMode) {
        val id = _state.value.selectedClipId ?: return
        record()
        _state.update { it.copy(blendModes = it.blendModes + (id to value)) }
    }

    fun setChromaKey(value: ChromaKeySettings) {
        val id = _state.value.selectedClipId ?: return
        record()
        _state.update { it.copy(chromaKeys = it.chromaKeys + (id to value)) }
    }

    fun addText(text: String) {
        if (text.isBlank()) return
        record()
        _state.update { s ->
            s.copy(textOverlays = s.textOverlays + TextOverlay(System.nanoTime(), text.trim(), s.positionMs, s.positionMs + 3_000L))
        }
    }

    fun duplicateSelected() {
        val state = _state.value
        val clip = state.selectedClip() ?: return
        if (isTrackLocked(clip.track)) return
        val length = TimelineMath.duration(clip, clip.sourceDurationMs.coerceAtLeast(state.durationMs))
        if (length <= 0L) return
        record()
        val copy = clip.copy(id = System.nanoTime(), name = "${clip.name} (copie)", timelineStartMs = clip.timelineStartMs + length)
        _state.update { s ->
            val shifted = s.clips.map { c ->
                if (clip.track == 0 && c.track == 0 && c.id != clip.id && c.timelineStartMs >= clip.timelineStartMs + length) {
                    c.copy(timelineStartMs = c.timelineStartMs + length)
                } else c
            }
            val index = shifted.indexOfFirst { it.id == clip.id }
            if (index < 0) s else s.copy(
                clips = shifted.toMutableList().apply { add(index + 1, copy) },
                selectedClipId = copy.id,
                selectedClipIds = setOf(copy.id),
                durationMs = maxOf(s.durationMs, copy.timelineStartMs + length),
            )
        }
    }

    fun setSelectedVolume(value: Float) {
        val clip = _state.value.selectedClip() ?: return
        updateClip(clip.copy(volume = value.coerceIn(0f, 2f)))
    }

    fun setAudioVolume(value: Float) {
        if (_state.value.audioClips.isEmpty()) return
        record()
        _state.update { s -> s.copy(audioClips = s.audioClips.map { it.copy(volume = value.coerceIn(0f, 2f)) }) }
    }

    fun removeLastAudio() {
        if (_state.value.audioClips.isEmpty()) return
        record()
        _state.update { s ->
            val id = s.selectedAudioId
            s.copy(audioClips = if (id != null) s.audioClips.filterNot { it.id == id } else s.audioClips.dropLast(1), selectedAudioId = null)
        }
    }

    fun removeLastText() {
        if (_state.value.textOverlays.isEmpty()) return
        record()
        _state.update { s ->
            val id = s.selectedTextId
            s.copy(textOverlays = if (id != null) s.textOverlays.filterNot { it.id == id } else s.textOverlays.dropLast(1), selectedTextId = null)
        }
    }

    fun importOverlay(uri: Uri, name: String) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val id = System.nanoTime()
        val context = app
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val duration = MediaProbe.durationMs(context, uri).takeIf { it > 0L } ?: 5_000L
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                record()
                _state.update { s ->
                    val start = s.positionMs
                    s.copy(
                        clips = s.clips + VideoClip(
                            id = id, uri = uri, name = name, endMs = duration, sourceDurationMs = duration,
                            track = 1, timelineStartMs = start,
                        ),
                        selectedClipId = id,
                        selectedClipIds = setOf(id),
                        durationMs = maxOf(s.durationMs, start + duration),
                    )
                }
            }
        }
    }

    fun trimStart() = updateSelected { clip, state ->
        val newStart = state.positionMs
            .let { (it - clip.timelineStartMs + clip.startMs) }
            .coerceIn(clip.startMs, clip.end(state.durationMs) - TimelineMath.MIN_CLIP_DURATION_MS)
        clip.copy(
            startMs = newStart,
            timelineStartMs = (clip.timelineStartMs + (newStart - clip.startMs)).coerceAtLeast(0L),
        )
    }

    fun trimEnd() = updateSelected { clip, state ->
        val newEnd = (state.positionMs - clip.timelineStartMs + clip.startMs)
            .coerceIn(clip.startMs + TimelineMath.MIN_CLIP_DURATION_MS, clip.end(state.durationMs))
        clip.copy(endMs = newEnd)
    }

    private fun updateSelected(transform: (VideoClip, EditorUiState) -> VideoClip) {
        val state = _state.value
        val selected = state.selectedClip() ?: return
        record()
        _state.update { it.copy(clips = it.clips.map { clip -> if (clip.id == selected.id) transform(selected, state) else clip }) }
    }

    fun split() {
        val state = _state.value
        val clip = state.selectedClip() ?: return
        val end = clip.end(clip.sourceDurationMs.coerceAtLeast(state.durationMs))
        val split = state.positionMs.coerceIn(clip.timelineStartMs + TimelineMath.MIN_CLIP_DURATION_MS, clip.timelineStartMs + (end - clip.startMs) - TimelineMath.MIN_CLIP_DURATION_MS)
        val sourceSplit = clip.startMs + (split - clip.timelineStartMs)
        if (sourceSplit <= clip.startMs || sourceSplit >= end) return
        record()
        val first = clip.copy(id = System.nanoTime(), endMs = sourceSplit, name = "${clip.name} · 1")
        val second = clip.copy(id = System.nanoTime() + 1, startMs = sourceSplit, endMs = clip.endMs, timelineStartMs = split, name = "${clip.name} · 2")
        val index = state.clips.indexOfFirst { it.id == clip.id }
        val list = state.clips.toMutableList().apply {
            removeAt(index)
            add(index, first)
            add(index + 1, second)
        }
        _state.update { it.copy(clips = list, selectedClipId = second.id, selectedClipIds = setOf(second.id)) }
    }

    fun deleteSelected() {
        val state = _state.value
        state.selectedAudioId?.let { id ->
            record()
            _state.update { it.copy(audioClips = it.audioClips.filterNot { a -> a.id == id }, selectedAudioId = null) }
            return
        }
        state.selectedTextId?.let { id ->
            record()
            _state.update { it.copy(textOverlays = it.textOverlays.filterNot { t -> t.id == id }, selectedTextId = null) }
            return
        }
        val ids = if (state.selectedClipIds.isNotEmpty()) state.selectedClipIds else setOfNotNull(state.selectedClipId)
        if (ids.isEmpty()) return
        record()
        val removed = state.clips.filter { it.id in ids && it.track == 0 }
        val shifts = removed
            .sortedBy { it.timelineStartMs }
            .map { TimelineMath.duration(it, it.sourceDurationMs.coerceAtLeast(state.durationMs)) to it.timelineStartMs }
        var list = state.clips.filterNot { it.id in ids }
        for ((duration, start) in shifts) {
            list = list.map { clip ->
                if (clip.track == 0 && clip.timelineStartMs > start) {
                    clip.copy(timelineStartMs = (clip.timelineStartMs - duration).coerceAtLeast(0L))
                } else clip
            }
        }
        val next = list.firstOrNull { it.track == 0 }
        _state.update {
            it.copy(
                clips = list,
                selectedClipId = next?.id,
                selectedClipIds = next?.let { setOf(it.id) } ?: emptySet(),
                playing = false,
            )
        }
    }

    fun setKeyframeProperty(
        x: Float? = null,
        y: Float? = null,
        scale: Float? = null,
        rotation: Float? = null,
        opacity: Float? = null,
    ) {
        val state = _state.value
        val clip = state.selectedClip() ?: return
        val local = (state.positionMs - clip.timelineStartMs + clip.startMs)
            .coerceIn(clip.startMs, clip.end(state.durationMs))
        val current = clip.keyframesAt(state.positionMs)
        record()

        fun put(list: List<AnimatedKeyframe>, value: Float, easing: Easing): List<AnimatedKeyframe> {
            val k = AnimatedKeyframe(local, value, easing)
            return (list.filterNot { it.timeMs == local } + k).sortedBy { it.timeMs }
        }

        val target = if (listOf(x, y, scale, rotation, opacity).all { it == null }) {
            listOf(current.x, current.y, current.scale, current.rotation, current.opacity)
        } else {
            listOf(x, y, scale, rotation, opacity).mapIndexed { index, value ->
                value ?: when (index) {
                    0 -> current.x
                    1 -> current.y
                    2 -> current.scale
                    3 -> current.rotation
                    else -> current.opacity
                }
            }
        }

        _state.update { root ->
            root.copy(clips = root.clips.map { item ->
                if (item.id != clip.id) item else item.copy(animation = item.animation.copy(
                    x = put(item.animation.x, target[0], state.easing),
                    y = put(item.animation.y, target[1], state.easing),
                    scale = put(item.animation.scale, target[2], state.easing),
                    rotation = put(item.animation.rotation, target[3], state.easing),
                    opacity = put(item.animation.opacity, target[4], state.easing),
                ))
            })
        }
    }

    fun removeKeyframeAtPlayhead() {
        val state = _state.value
        val clip = state.selectedClip() ?: return
        val local = state.positionMs - clip.timelineStartMs + clip.startMs
        val has = listOf(clip.animation.x, clip.animation.y, clip.animation.scale, clip.animation.rotation, clip.animation.opacity)
            .any { values -> values.any { it.timeMs == local } }
        if (!has) return
        record()
        _state.update {
            it.copy(clips = it.clips.map { item ->
                if (item.id == clip.id) item.copy(animation = item.animation.copy(
                    x = item.animation.x.filterNot { it.timeMs == local },
                    y = item.animation.y.filterNot { it.timeMs == local },
                    scale = item.animation.scale.filterNot { it.timeMs == local },
                    rotation = item.animation.rotation.filterNot { it.timeMs == local },
                    opacity = item.animation.opacity.filterNot { it.timeMs == local },
                )) else item
            })
        }
    }

    fun clearKeyframes() {
        val clip = _state.value.selectedClip() ?: return
        if (clip.animation == TransformAnimation()) return
        record()
        _state.update {
            it.copy(clips = it.clips.map { item ->
                if (item.id == clip.id) item.copy(animation = TransformAnimation(), keyframes = emptyList()) else item
            })
        }
    }

    fun addTransition(type: TransitionType = TransitionType.CROSS_FADE, durationMs: Long = 500L) {
        val state = _state.value
        val ordered = state.clips.filter { it.track == 0 }.sortedBy { it.timelineStartMs }
        val from = state.selectedClipId?.let { id -> ordered.firstOrNull { it.id == id } } ?: ordered.firstOrNull() ?: return
        val fromEnd = from.timelineStartMs + TimelineMath.duration(from, state.durationMs)
        val to = ordered.firstOrNull { it.timelineStartMs >= fromEnd } ?: return
        record()
        val transition = Transition(System.nanoTime(), from.id, to.id, durationMs.coerceIn(100L, 3_000L), type)
        _state.update { it.copy(transitions = it.transitions.filterNot { t -> t.fromClipId == from.id || t.toClipId == to.id } + transition) }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    fun previewEffects(clip: VideoClip): List<androidx.media3.common.Effect> =
        mediaEngine.previewEffects(_state.value, clip)

    fun exportSelected() {
        val snapshot = _state.value
        if (snapshot.clips.none { it.track == 0 }) return
        val capabilityErrors = com.roadsearch.openeditvideo.export.ExportCapabilityAnalyzer.errors(snapshot)
        val report = MediaSourceValidator.validate(app, snapshot.clips, snapshot.audioClips)
        if (capabilityErrors.isNotEmpty() || !report.isValid) {
            _state.update { it.copy(exportProgress = null, exportMessage = (capabilityErrors + report.errors).distinct().joinToString(" ")) }
            return
        }
        viewModelScope.launch {
            try {
                repository.save(snapshot, hydratedId ?: repository.currentId.value)
            } catch (error: Throwable) {
                _state.update { it.copy(exportProgress = null, exportMessage = "Impossible d'enregistrer le projet : ${error.message ?: "erreur inconnue"}") }
                return@launch
            }
            val request = OneTimeWorkRequestBuilder<VideoExportWorker>()
                .setInputData(
                    workDataOf(
                        ExportKeys.PROJECT_ID to (hydratedId ?: repository.currentId.value),
                        ExportKeys.OUTPUT_NAME to "OpenEditVideo_${System.currentTimeMillis()}.mp4",
                    )
                )
                .build()
            workManager.enqueueUniqueWork(EXPORT_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
            _state.update { it.copy(playing = false, exportProgress = 0f, exportMessage = "Export en attente…") }
        }
    }

    fun cancelExport() {
        workManager.cancelUniqueWork(EXPORT_WORK_NAME)
    }

    fun clearExportMessage() = _state.update { it.copy(exportProgress = null, exportMessage = null) }

    private fun VideoClip.end(fallbackDurationMs: Long): Long =
        TimelineMath.end(this, fallbackDurationMs)

    private fun EditorUiState.totalTimelineDuration(): Long {
        val videoEnd = clips.maxOfOrNull {
            it.timelineStartMs + TimelineMath.duration(it, it.sourceDurationMs.coerceAtLeast(durationMs))
        } ?: 0L
        val audioEnd = audioClips.maxOfOrNull {
            it.timelineStartMs + (it.endMs - it.startMs).coerceAtLeast(TimelineMath.MIN_CLIP_DURATION_MS)
        } ?: 0L
        val textEnd = textOverlays.maxOfOrNull { it.endMs } ?: 0L
        return maxOf(durationMs, videoEnd, audioEnd, textEnd)
    }
}
