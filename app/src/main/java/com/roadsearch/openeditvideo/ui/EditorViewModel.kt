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
import com.roadsearch.openeditvideo.core.TimelineValidator
import com.roadsearch.openeditvideo.media.ProjectBackgrounds
import com.roadsearch.openeditvideo.core.editor.TimelineEditor
import com.roadsearch.openeditvideo.core.editor.TimelineOps
import com.roadsearch.openeditvideo.data.ProjectRepository
import com.roadsearch.openeditvideo.data.EditorStateCodec
import com.roadsearch.openeditvideo.export.ExportKeys
import com.roadsearch.openeditvideo.export.ExportSettings
import kotlinx.coroutines.flow.asStateFlow
import com.roadsearch.openeditvideo.export.MediaSourceValidator
import com.roadsearch.openeditvideo.export.VideoExportWorker
import com.roadsearch.openeditvideo.media.MediaProbe
import com.roadsearch.openeditvideo.media.MediaEngine
import com.roadsearch.openeditvideo.model.*
import com.roadsearch.openeditvideo.scene.SceneGraph
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
    private var pendingImport: Triple<Uri, String, AspectRatio?>? = null

    private data class Snapshot(
        val clips: List<VideoClip>,
        val audio: List<AudioClip>,
        val text: List<TextOverlay>,
        val selected: Long?,
        val selectedIds: Set<Long>,
        val zoom: Float,
        val aspect: AspectRatio,
        val effects: EffectSettings,
        val transitions: List<Transition>,
        val easing: Easing,
        val masks: Map<Long, MaskSettings>,
        val blendModes: Map<Long, BlendMode>,
        val chromaKeys: Map<Long, ChromaKeySettings>,
        val markers: List<Marker>,
        val trackStates: Map<Int, TrackState>,
        val snappingEnabled: Boolean,
        val nullObjects: List<NullObject>,
    )

    private val editor = TimelineEditor()
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
                _state.value = runCatching { TimelineValidator.repair(EditorStateCodec.decode(entity.documentJson)) }.fold(
                    onSuccess = { it },
                    onFailure = { error ->
                        // Never turn a decode failure into an empty project that autosave could overwrite.
                        val reason = "Projet illisible, il n'est pas modifié : ${error.message ?: "format inconnu"}"
                        EditorUiState(loadError = reason, exportMessage = reason)
                    },
                )
                hydratedId = entity.id
                hydrated = true
                pendingImport?.let { (uri, name, aspect) ->
                    pendingImport = null
                    if (aspect != null) _state.update { s -> s.copy(aspect = aspect) }
                    import(uri, name)
                }
            }
        }

        viewModelScope.launch {
            state
                .drop(1)
                .map { EditorStateCodec.encode(it) to hydratedId }
                .distinctUntilChanged()
                .debounce(650L)
                .collectLatest { (json, id) ->
                    if (hydrated && id != null && _state.value.loadError == null) repository.saveJson(json, id)
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
        aspect = s.aspect,
        effects = s.effects,
        transitions = s.transitions,
        easing = s.easing,
        masks = s.masks,
        blendModes = s.blendModes,
        chromaKeys = s.chromaKeys,
        markers = s.markers,
        trackStates = s.trackStates,
        snappingEnabled = s.snappingEnabled,
        nullObjects = s.nullObjects,
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
                aspect = s.aspect,
                effects = s.effects,
                transitions = s.transitions,
                easing = s.easing,
                masks = s.masks,
                blendModes = s.blendModes,
                chromaKeys = s.chromaKeys,
                markers = s.markers,
                trackStates = s.trackStates,
                snappingEnabled = s.snappingEnabled,
                nullObjects = s.nullObjects,
            )
        }
    }

    /** Imports [uri] as soon as the project being opened has been loaded (avoids racing the hydration). */
    /** Media to import as soon as the project being opened is hydrated; [aspect] optionally sets the canvas first. */
    fun queueImport(uri: Uri, name: String, aspect: AspectRatio? = null) { pendingImport = Triple(uri, name, aspect) }

    /** Writes the current state immediately (call before leaving the editor; the debounced save may still be pending). */
    fun flushSave() {
        if (_state.value.loadError != null) return
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
            // A photo has no duration of its own: it gets a default length and the "still" source marker.
            val still = !audioOnly && MediaProbe.isImage(context, uri)
            val duration = if (still) STILL_DEFAULT_MS else MediaProbe.durationMs(context, uri)
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
                                sourceDurationMs = if (still) STILL_SOURCE_MS else duration,
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
        val moved = TimelineMath.move(
            clip = clip,
            deltaMs = deltaMs,
            others = state.clips.filter { it.id != id && it.track == clip.track },
            fallbackDurationMs = state.durationMs,
        )
        val applied = moved.timelineStartMs - clip.timelineStartMs
        val groupId = clip.groupId
        // A grouped clip stays put when one of its followers would land on another clip of its own track.
        if (groupId != null && !TimelineOps.followersCanShift(state, groupId, applied, id, state.trackStates.filterValues { it.locked }.keys)) return
        if (!gestureOpen) record()
        _state.update { it.copy(clips = it.clips.map { item -> if (item.id == id) moved else item }) }
        propagateGroupMove(groupId, applied, skipClipId = id)
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
        val audio = _state.value.audioClips.firstOrNull { it.id == id } ?: return
        if (!gestureOpen) record()
        val newStart = (audio.timelineStartMs + deltaMs).coerceAtLeast(0L)
        _state.update { s -> s.copy(audioClips = s.audioClips.map { if (it.id == id) it.copy(timelineStartMs = newStart) else it }) }
        propagateGroupMove(audio.groupId, newStart - audio.timelineStartMs, skipAudioId = id)
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
        propagateGroupMove(t.groupId, start - t.startMs, skipTextId = id)
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

    /** Swaps the stacking order of two video tracks (clips and their lock/visibility/mute state follow). */
    fun swapTracks(a: Int, b: Int) {
        if (a == b) return
        record()
        _state.update { s ->
            val states = s.trackStates.toMutableMap()
            val sa = states[a]; val sb = states[b]
            if (sb != null) states[a] = sb else states.remove(a)
            if (sa != null) states[b] = sa else states.remove(b)
            s.copy(
                clips = s.clips.map { c -> when (c.track) { a -> c.copy(track = b); b -> c.copy(track = a); else -> c } },
                trackStates = states,
            )
        }
    }

    /** Project cover = the frame of the main track at [ms] (shown in the Home list). */
    fun setCover(ms: Long) = _state.update { it.copy(coverMs = ms.coerceAtLeast(0L)) }

    fun setAspect(aspect: AspectRatio) {
        if (_state.value.aspect == aspect) return
        record()
        _state.update { it.copy(aspect = aspect) }
        retargetBackgrounds(aspect)
    }

    /** Generated backgrounds are re-rendered at the new canvas ratio so they keep filling the frame. */
    private fun retargetBackgrounds(aspect: AspectRatio) {
        val generated = _state.value.clips.mapNotNull { c -> ProjectBackgrounds.presetOf(c.uri)?.let { c.uri to it } }.distinct()
        if (generated.isEmpty()) return
        val context = app
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val mapping = generated.associate { (old, preset) -> old to ProjectBackgrounds.uriFor(context, preset, aspect) }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                // Ignore a stale result if the ratio changed again in the meantime.
                _state.update { s ->
                    if (s.aspect != aspect) s
                    else s.copy(clips = s.clips.map { c -> mapping[c.uri]?.let { c.copy(uri = it) } ?: c })
                }
            }
        }
    }

    /** Sets (or, with CUT, removes) the transition between two adjacent main-track clips. */
    fun setTransition(fromId: Long, toId: Long, type: TransitionType, durationMs: Long) {
        record()
        _state.update { s ->
            val rest = s.transitions.filterNot { it.fromClipId == fromId || it.toClipId == toId }
            s.copy(transitions = if (type == TransitionType.CUT) rest else rest + Transition(System.nanoTime(), fromId, toId, durationMs.coerceIn(100L, 3_000L), type))
        }
    }

    /** Removes the wipe transitions (the only kind the export engine cannot render). */
    fun clearWipeTransitions() {
        record()
        _state.update { s -> s.copy(transitions = s.transitions.filterNot { it.type == TransitionType.WIPE_LEFT || it.type == TransitionType.WIPE_RIGHT }) }
    }

    /** Removes every transition. */
    fun clearTransitions() {
        record()
        _state.update { it.copy(transitions = emptyList()) }
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

    fun addText(text: String, style: TextStyleSpec, parentId: Long? = null) {
        if (text.isBlank()) return
        if (!SceneGraph.canParentClip(parentId, _state.value.nullObjects)) return
        record()
        val id = System.nanoTime()
        _state.update { s ->
            s.copy(
                textOverlays = s.textOverlays + TextOverlay(id, text.trim(), s.positionMs, s.positionMs + 3_000L, style, parentId),
                selectedTextId = id, selectedAudioId = null, selectedClipId = null, selectedClipIds = emptySet(),
            )
        }
    }

    fun updateText(id: Long, text: String, style: TextStyleSpec, parentId: Long? = _state.value.textOverlays.firstOrNull { it.id == id }?.parentId) {
        if (text.isBlank() || _state.value.textOverlays.none { it.id == id }) return
        if (!SceneGraph.canParentClip(parentId, _state.value.nullObjects)) return
        record()
        _state.update { s -> s.copy(textOverlays = s.textOverlays.map { if (it.id == id) it.copy(text = text.trim(), style = style, parentId = parentId) else it }) }
    }

    fun setTextParent(id: Long, parentId: Long?): Boolean {
        val state = _state.value
        val overlay = state.textOverlays.firstOrNull { it.id == id } ?: return false
        if (!SceneGraph.canParentClip(parentId, state.nullObjects)) return false
        if (overlay.parentId == parentId) return true
        record()
        _state.update { current -> current.copy(textOverlays = current.textOverlays.map { if (it.id == id) it.copy(parentId = parentId) else it }) }
        return true
    }

    fun clearSelection() = _state.update {
        it.copy(selectedClipId = null, selectedClipIds = emptySet(), selectedAudioId = null, selectedTextId = null)
    }

    fun duplicateSelected() {
        val state = _state.value
        val clip = state.selectedClip() ?: return
        if (isTrackLocked(clip.track)) return
        val newId = System.nanoTime()
        if (TimelineOps.duplicate(state, clip.id, newId) == null) return
        record()
        _state.update { s -> TimelineOps.duplicate(s, clip.id, newId) ?: s }
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
            val still = MediaProbe.isImage(context, uri)
            val duration = if (still) STILL_DEFAULT_MS else MediaProbe.durationMs(context, uri)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
                record()
                _state.update { s ->
                    val start = s.positionMs
                    s.copy(
                        clips = s.clips + VideoClip(
                            id = id, uri = uri, name = name, endMs = duration,
                            sourceDurationMs = if (still) STILL_SOURCE_MS else duration,
                            track = TimelineOps.freeOverlayTrack(s.clips, start, duration, s.durationMs), timelineStartMs = start,
                        ),
                        selectedClipId = id,
                        selectedClipIds = setOf(id),
                        durationMs = maxOf(s.durationMs, start + duration),
                    )
                }
            }
        }
    }

    /** Overlay clip: extend its left edge back to time 0 (videos as far as their source allows, stills freely). */
    fun stretchToStart() = updateSelected { clip, state ->
        val wanted = clip.timelineStartMs
        when {
            wanted <= 0L -> clip
            clip.sourceDurationMs >= STILL_SOURCE_MS ->
                clip.copy(timelineStartMs = 0L, endMs = clip.end(state.durationMs) + wanted)
            else -> {
                val ext = minOf(clip.startMs, wanted)
                clip.copy(startMs = clip.startMs - ext, timelineStartMs = clip.timelineStartMs - ext)
            }
        }
    }

    /** Overlay clip: extend its right edge to the end of the main track. */
    fun stretchToEnd() = updateSelected { clip, state ->
        val main = state.clips.minOf { it.track }
        val target = state.clips.filter { it.track == main }
            .maxOfOrNull { it.timelineStartMs + (it.end(state.durationMs) - it.startMs) } ?: state.timelineEndMs()
        val currentEnd = clip.timelineStartMs + (clip.end(state.durationMs) - clip.startMs)
        val need = target - currentEnd
        if (need <= 0L) clip else {
            val limit = if (clip.sourceDurationMs > 0L) clip.sourceDurationMs else Long.MAX_VALUE
            clip.copy(endMs = minOf(clip.end(state.durationMs) + need, limit))
        }
    }

    /** Trims the start of the selected clip to the playhead (no-op when the playhead is not inside the clip). */
    fun trimStart() {
        val state = _state.value
        val clip = state.selectedClip() ?: return
        if (isTrackLocked(clip.track)) return
        val trimmed = editor.trimStartAt(clip, state.positionMs, clip.sourceDurationMs.coerceAtLeast(state.durationMs)) ?: return
        record()
        _state.update { s -> s.copy(clips = s.clips.map { if (it.id == clip.id) trimmed else it }) }
    }

    /** Trims the end of the selected clip to the playhead (no-op when the playhead is not inside the clip). */
    fun trimEnd() {
        val state = _state.value
        val clip = state.selectedClip() ?: return
        if (isTrackLocked(clip.track)) return
        val trimmed = editor.trimEndAt(clip, state.positionMs, clip.sourceDurationMs.coerceAtLeast(state.durationMs)) ?: return
        record()
        _state.update { s -> s.copy(clips = s.clips.map { if (it.id == clip.id) trimmed else it }) }
    }

    private fun updateSelected(transform: (VideoClip, EditorUiState) -> VideoClip) {
        val state = _state.value
        val selected = state.selectedClip() ?: return
        record()
        _state.update { it.copy(clips = it.clips.map { clip -> if (clip.id == selected.id) transform(selected, state) else clip }) }
    }

    /** Cuts the selected clip at the playhead. Does nothing when the playhead is outside the clip or too close to an edge. */
    fun split() {
        val state = _state.value
        val clip = state.selectedClip() ?: return
        if (isTrackLocked(clip.track)) return
        val newId = System.nanoTime()
        if (TimelineOps.split(state, clip.id, state.positionMs, newId) == null) return
        record()
        _state.update { s -> TimelineOps.split(s, clip.id, s.positionMs, newId) ?: s }
    }

    /**
     * Deletes the selection. On the main track [ripple] closes the gap (Ripple Delete); with false a hole is left
     * (Delete). Clips on locked tracks are never deleted.
     */
    fun deleteSelected(ripple: Boolean = true) {
        val state = _state.value
        state.selectedAudioId?.let { id ->
            record()
            _state.update { TimelineOps.normalizeGroups(it.copy(audioClips = it.audioClips.filterNot { a -> a.id == id }, selectedAudioId = null)) }
            return
        }
        state.selectedTextId?.let { id ->
            record()
            _state.update { TimelineOps.normalizeGroups(it.copy(textOverlays = it.textOverlays.filterNot { t -> t.id == id }, selectedTextId = null)) }
            return
        }
        val wanted = if (state.selectedClipIds.isNotEmpty()) state.selectedClipIds else setOfNotNull(state.selectedClipId)
        val ids = state.clips.filter { it.id in wanted && !isTrackLocked(it.track) }.map { it.id }.toSet()
        if (ids.isEmpty()) return
        record()
        _state.update { s -> TimelineOps.delete(s, ids, ripple).copy(playing = false) }
    }

    /** Delete that closes the gap on the main track. */
    fun rippleDeleteSelected() = deleteSelected(ripple = true)

    /** Delete that leaves an empty space where the clip was. */
    fun deleteSelectedKeepingGap() = deleteSelected(ripple = false)

    // ---- Link groups (linked audio / video / text move together) ---------------------------------------------

    /** Links the multi-selected video clips together. */
    fun groupSelectedClips() {
        val ids = _state.value.selectedClipIds
        if (ids.size < 2) return
        record()
        val groupId = System.nanoTime()
        _state.update { TimelineOps.group(it, ids, emptySet(), emptySet(), groupId) }
    }

    /** Links the selected audio or text to the main-track clip under the playhead. */
    fun linkSelectedToVideo() {
        val s = _state.value
        val audioIds = setOfNotNull(s.selectedAudioId)
        val textIds = setOfNotNull(s.selectedTextId)
        if (audioIds.isEmpty() && textIds.isEmpty()) return
        val main = s.clips.filter { it.track == TimelineOps.MAIN_TRACK }
        val target = main.firstOrNull { c ->
            s.positionMs >= c.timelineStartMs &&
                s.positionMs < c.timelineStartMs + TimelineMath.duration(c, c.sourceDurationMs.coerceAtLeast(s.durationMs))
        } ?: main.firstOrNull() ?: return
        record()
        val groupId = target.groupId ?: System.nanoTime()
        _state.update { TimelineOps.group(it, setOf(target.id), audioIds, textIds, groupId) }
    }

    /** Releases the group of the selected element. */
    fun ungroupSelected() {
        val groupId = TimelineOps.selectedGroupId(_state.value) ?: return
        record()
        _state.update { TimelineOps.ungroup(it, groupId) }
    }

    /** Followers of a linked group move by the amount the dragged element really moved (never before 0). */
    private fun propagateGroupMove(groupId: Long?, deltaMs: Long, skipClipId: Long? = null, skipAudioId: Long? = null, skipTextId: Long? = null) {
        if (groupId == null || deltaMs == 0L) return
        _state.update { s ->
            TimelineOps.shiftMates(s, groupId, deltaMs, skipClipId, skipAudioId, skipTextId, s.trackStates.filterValues { it.locked }.keys)
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
        val baseAnimation = clip.effectiveAnimation()
        val addAllProperties = x == null && y == null && scale == null && rotation == null && opacity == null
        record()

        fun put(list: List<AnimatedKeyframe>, value: Float): List<AnimatedKeyframe> {
            val key = AnimatedKeyframe(local, value, state.easing)
            return (list.filterNot { it.timeMs == local } + key).sortedBy { it.timeMs }
        }

        val animation = baseAnimation.copy(
            x = if (addAllProperties || x != null) put(baseAnimation.x, x ?: current.x) else baseAnimation.x,
            y = if (addAllProperties || y != null) put(baseAnimation.y, y ?: current.y) else baseAnimation.y,
            scale = if (addAllProperties || scale != null) put(baseAnimation.scale, scale ?: current.scale) else baseAnimation.scale,
            rotation = if (addAllProperties || rotation != null) put(baseAnimation.rotation, rotation ?: current.rotation) else baseAnimation.rotation,
            opacity = if (addAllProperties || opacity != null) put(baseAnimation.opacity, opacity ?: current.opacity) else baseAnimation.opacity,
        )

        _state.update { root ->
            root.copy(clips = root.clips.map { item ->
                if (item.id == clip.id) item.copy(animation = animation, keyframes = emptyList()) else item
            })
        }
    }

    /** Applies an easing curve to the segment starting at a specific keyframe. */
    fun updateKeyframeCurve(
        property: AnimatedProperty,
        keyframeTimeMs: Long,
        easing: Easing,
        curveX1: Float,
        curveY1: Float,
        curveX2: Float,
        curveY2: Float,
    ) {
        val clip = _state.value.selectedClip() ?: return
        val baseAnimation = clip.effectiveAnimation()
        val keys = baseAnimation.keyframes(property)
        if (keys.none { it.timeMs == keyframeTimeMs }) return
        record()
        val updatedKeys = keys.map { key ->
            if (key.timeMs == keyframeTimeMs) key.copy(
                easingToNext = easing,
                curveX1 = curveX1.coerceIn(0f, 1f),
                curveY1 = curveY1.coerceIn(-2f, 3f),
                curveX2 = curveX2.coerceIn(0f, 1f),
                curveY2 = curveY2.coerceIn(-2f, 3f),
            ) else key
        }
        _state.update { root ->
            root.copy(clips = root.clips.map { item ->
                if (item.id == clip.id) {
                    item.copy(animation = item.effectiveAnimation().withKeyframes(property, updatedKeys), keyframes = emptyList())
                } else item
            })
        }
    }

    /** Quick preset: update the active segment and the default used for newly placed keyframes. */
    fun setSegmentEasing(property: AnimatedProperty, easing: Easing) {
        val state = _state.value
        val clip = state.selectedClip() ?: return
        val local = (state.positionMs - clip.timelineStartMs + clip.startMs)
            .coerceIn(clip.startMs, clip.end(state.durationMs))
        val keys = clip.effectiveAnimation().keyframes(property).sortedBy { it.timeMs }
        val lastAtOrBefore = keys.indexOfLast { it.timeMs <= local }
        val start = if (lastAtOrBefore == keys.lastIndex && lastAtOrBefore > 0) {
            keys[lastAtOrBefore - 1]
        } else {
            keys.getOrNull(lastAtOrBefore) ?: keys.firstOrNull()
        } ?: return
        updateKeyframeCurve(
            property = property,
            keyframeTimeMs = start.timeMs,
            easing = easing,
            curveX1 = start.curveX1,
            curveY1 = start.curveY1,
            curveX2 = start.curveX2,
            curveY2 = start.curveY2,
        )
    }

    fun seekToPreviousKeyframe(property: AnimatedProperty) = seekToAdjacentKeyframe(property, forward = false)

    fun seekToNextKeyframe(property: AnimatedProperty) = seekToAdjacentKeyframe(property, forward = true)

    private fun seekToAdjacentKeyframe(property: AnimatedProperty, forward: Boolean) {
        val state = _state.value
        val clip = state.selectedClip() ?: return
        val local = state.positionMs - clip.timelineStartMs + clip.startMs
        val keys = clip.effectiveAnimation().keyframes(property).sortedBy { it.timeMs }
        val target = (if (forward) keys.firstOrNull { it.timeMs > local } else keys.lastOrNull { it.timeMs < local })
            ?: return
        val position = (clip.timelineStartMs + target.timeMs - clip.startMs).coerceAtLeast(clip.timelineStartMs)
        _state.update { it.copy(positionMs = position, playing = false, seekNonce = it.seekNonce + 1) }
    }

    /** Deletes one property's key at the playhead; null deletes all property keys at that time. */
    /** True when the active property has a keyframe exactly under the playhead. */
    fun hasKeyframeAtPlayhead(property: AnimatedProperty): Boolean {
        val state = _state.value
        val clip = state.selectedClip() ?: return false
        val local = state.positionMs - clip.timelineStartMs + clip.startMs
        return clip.effectiveAnimation().keyframes(property).any { it.timeMs == local }
    }

    /** Alight Motion-style diamond: add at playhead, or remove the exact active-property key. */
    fun toggleKeyframeAtPlayhead(property: AnimatedProperty) {
        if (hasKeyframeAtPlayhead(property)) {
            removeKeyframeAtPlayhead(property)
            return
        }
        val clip = _state.value.selectedClip() ?: return
        val current = clip.keyframesAt(_state.value.positionMs)
        when (property) {
            AnimatedProperty.X -> setKeyframeProperty(x = current.x)
            AnimatedProperty.Y -> setKeyframeProperty(y = current.y)
            AnimatedProperty.SCALE -> setKeyframeProperty(scale = current.scale)
            AnimatedProperty.ROTATION -> setKeyframeProperty(rotation = current.rotation)
            AnimatedProperty.OPACITY -> setKeyframeProperty(opacity = current.opacity)
        }
    }

    /** Removes every keyframe on one property only, leaving all other tracks untouched. */
    fun clearKeyframes(property: AnimatedProperty) {
        val clip = _state.value.selectedClip() ?: return
        if (clip.effectiveAnimation().keyframes(property).isEmpty()) return
        record()
        _state.update { root ->
            root.copy(clips = root.clips.map { item ->
                if (item.id == clip.id) {
                    item.copy(
                        animation = item.effectiveAnimation().withKeyframes(property, emptyList()),
                        keyframes = emptyList(),
                    )
                } else item
            })
        }
    }

    fun removeKeyframeAtPlayhead(property: AnimatedProperty? = null) {
        val state = _state.value
        val clip = state.selectedClip() ?: return
        val local = state.positionMs - clip.timelineStartMs + clip.startMs
        val animation = clip.effectiveAnimation()
        val tracks = property?.let { listOf(animation.keyframes(it)) }
            ?: listOf(animation.x, animation.y, animation.scale, animation.rotation, animation.opacity)
        if (tracks.none { values -> values.any { it.timeMs == local } }) return
        record()
        _state.update { root ->
            root.copy(clips = root.clips.map { item ->
                if (item.id != clip.id) item else {
                    val effective = item.effectiveAnimation()
                    val updated = if (property == null) {
                        effective.copy(
                            x = effective.x.filterNot { it.timeMs == local },
                            y = effective.y.filterNot { it.timeMs == local },
                            scale = effective.scale.filterNot { it.timeMs == local },
                            rotation = effective.rotation.filterNot { it.timeMs == local },
                            opacity = effective.opacity.filterNot { it.timeMs == local },
                        )
                    } else {
                        effective.withKeyframes(
                            property,
                            effective.keyframes(property).filterNot { it.timeMs == local },
                        )
                    }
                    item.copy(animation = updated, keyframes = emptyList())
                }
            })
        }
    }

    fun clearKeyframes() {
        val clip = _state.value.selectedClip() ?: return
        if (clip.animation == TransformAnimation() && clip.keyframes.isEmpty()) return
        record()
        _state.update {
            it.copy(clips = it.clips.map { item ->
                if (item.id == clip.id) item.copy(animation = TransformAnimation(), keyframes = emptyList()) else item
            })
        }
    }

    /** Creates a Null controller and attaches the selected video clip to it. */
    fun createNullParentForSelectedClip(): Long? {
        val state = _state.value
        val clipId = state.selectedClipId ?: return null
        if (state.clips.none { it.id == clipId } || SceneGraph.validationErrors(state.clips, state.nullObjects).isNotEmpty()) return null
        val usedIds = state.nullObjects.mapTo(HashSet<Long>()) { it.id }
        var id = System.nanoTime().coerceAtLeast(1L)
        while (id in usedIds) id = if (id == Long.MAX_VALUE) 1L else id + 1L
        val node = NullObject(id, "Null ${state.nullObjects.size + 1}")
        record()
        _state.update { current ->
            current.copy(
                nullObjects = current.nullObjects + node,
                clips = current.clips.map { if (it.id == clipId) it.copy(parentId = id) else it },
            )
        }
        return id
    }

    /** Reparents the selected clip to a valid Null controller, or detaches it when [parentId] is null. */
    fun setSelectedClipParent(parentId: Long?): Boolean {
        val state = _state.value
        val clip = state.selectedClip() ?: return false
        if (!SceneGraph.canParentClip(parentId, state.nullObjects)) return false
        if (clip.parentId == parentId) return true
        record()
        _state.update { current ->
            current.copy(clips = current.clips.map { if (it.id == clip.id) it.copy(parentId = parentId) else it })
        }
        return true
    }

    fun setNullParent(id: Long, parentId: Long?): Boolean {
        val state = _state.value
        val node = state.nullObjects.firstOrNull { it.id == id } ?: return false
        if (!SceneGraph.canParentNull(id, parentId, state.nullObjects)) return false
        if (node.parentId == parentId) return true
        record()
        _state.update { current ->
            current.copy(nullObjects = current.nullObjects.map { if (it.id == id) it.copy(parentId = parentId) else it })
        }
        return true
    }

    /** Adds/updates a timeline-time transform keyframe on a Null controller. */
    fun setNullKeyframeProperty(
        id: Long,
        x: Float? = null,
        y: Float? = null,
        scale: Float? = null,
        rotation: Float? = null,
        opacity: Float? = null,
    ) {
        val state = _state.value
        val node = state.nullObjects.firstOrNull { it.id == id } ?: return
        val time = state.positionMs.coerceAtLeast(0L)
        val current = node.animation.at(time)
        record()

        fun put(list: List<AnimatedKeyframe>, value: Float, easing: Easing): List<AnimatedKeyframe> =
            (list.filterNot { it.timeMs == time } + AnimatedKeyframe(time, value, easing)).sortedBy { it.timeMs }

        val animation = node.animation.copy(
            x = put(node.animation.x, x ?: current.x, state.easing),
            y = put(node.animation.y, y ?: current.y, state.easing),
            scale = put(node.animation.scale, scale ?: current.scale, state.easing),
            rotation = put(node.animation.rotation, rotation ?: current.rotation, state.easing),
            opacity = put(node.animation.opacity, opacity ?: current.opacity, state.easing),
        )
        _state.update { currentState ->
            currentState.copy(nullObjects = currentState.nullObjects.map { if (it.id == id) it.copy(animation = animation) else it })
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

    private val _exportSettings = MutableStateFlow(ExportSettings())
    /** Last options chosen in the export drawer (kept while the editor lives). */
    val exportSettings: StateFlow<ExportSettings> = _exportSettings.asStateFlow()
    fun setExportSettings(settings: ExportSettings) { _exportSettings.value = settings }

    fun exportSelected() = exportWith(_exportSettings.value)

    fun exportWith(settings: ExportSettings) {
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
                        ExportKeys.RESOLUTION to settings.resolution.name,
                        ExportKeys.FPS to settings.fps,
                        ExportKeys.HIGH_QUALITY to settings.highQuality,
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

/** Default length of a photo / background placed on the timeline. */
private const val STILL_DEFAULT_MS = 5_000L
