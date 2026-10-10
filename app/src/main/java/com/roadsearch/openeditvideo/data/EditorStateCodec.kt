package com.roadsearch.openeditvideo.data

import android.net.Uri
import com.roadsearch.openeditvideo.model.*
import org.json.JSONArray
import org.json.JSONObject

/** Compact JSON persistence format kept inside one Room row.
 * Keeping the schema in a codec makes future migrations independent from the UI state class.
 */
object EditorStateCodec {
    /** 9: adds editable Bézier keyframe curves while preserving groups, parenting and track metadata from V8. */
    private const val VERSION = 9

    fun encode(state: EditorUiState): String = JSONObject().apply {
        put("version", VERSION)
        put("clips", JSONArray().apply { state.clips.forEach { put(clip(it)) } })
        put("audio", JSONArray().apply { state.audioClips.forEach { put(audio(it)) } })
        put("text", JSONArray().apply { state.textOverlays.forEach { put(text(it)) } })
        put("effects", effects(state.effects))
        put("aspect", state.aspect.name)
        put("coverMs", state.coverMs)
        put("nullObjects", JSONArray().apply { state.nullObjects.forEach { put(nullObject(it)) } })
        put("transitions", JSONArray().apply { state.transitions.forEach { put(transition(it)) } })
        put("easing", state.easing.name)
        put("zoom", state.zoom.toDouble())
        put("muted", state.muted)
        put("masks", JSONObject().apply {
            state.masks.forEach { (id, value) -> put(id.toString(), mask(value)) }
        })
        put("blendModes", JSONObject().apply {
            state.blendModes.forEach { (id, value) -> put(id.toString(), value.name) }
        })
        put("chromaKeys", JSONObject().apply {
            state.chromaKeys.forEach { (id, value) -> put(id.toString(), chroma(value)) }
        })
        put("markers", JSONArray().apply { state.markers.forEach { put(marker(it)) } })
        put("snappingEnabled", state.snappingEnabled)
        put("trackStates", JSONObject().apply {
            state.trackStates.forEach { (track, value) -> put(track.toString(), trackState(value)) }
        })
    }.toString()

    fun decode(json: String): EditorUiState {
        val root = JSONObject(json)
        val version = root.optInt("version", 1)
        require(version <= VERSION) { "Version de projet $version non prise en charge" }
        val clips = mutableListOf<VideoClip>()
        root.optJSONArray("clips")?.let { array ->
            for (i in 0 until array.length()) clips += readClip(array.getJSONObject(i), version)
        }
        val audio = mutableListOf<AudioClip>()
        root.optJSONArray("audio")?.let { array ->
            for (i in 0 until array.length()) audio += readAudio(array.getJSONObject(i))
        }
        val text = mutableListOf<TextOverlay>()
        root.optJSONArray("text")?.let { array ->
            for (i in 0 until array.length()) text += readText(array.getJSONObject(i), version)
        }
        val nullObjects = mutableListOf<NullObject>()
        root.optJSONArray("nullObjects")?.let { array ->
            for (i in 0 until array.length()) nullObjects += readNullObject(array.getJSONObject(i), version)
        }
        val transitions = mutableListOf<Transition>()
        root.optJSONArray("transitions")?.let { array ->
            for (i in 0 until array.length()) transitions += readTransition(array.getJSONObject(i))
        }

        val masks = buildMap<Long, MaskSettings> {
            root.optJSONObject("masks")?.let { obj ->
                obj.keys().forEach { key -> runCatching { put(key.toLong(), readMask(obj.getJSONObject(key))) } }
            }
        }
        val blendModes = buildMap<Long, BlendMode> {
            root.optJSONObject("blendModes")?.let { obj ->
                obj.keys().forEach { key -> runCatching {
                    put(key.toLong(), runCatching { BlendMode.valueOf(obj.getString(key)) }.getOrDefault(BlendMode.NORMAL))
                } }
            }
        }
        val chromaKeys = buildMap<Long, ChromaKeySettings> {
            root.optJSONObject("chromaKeys")?.let { obj ->
                obj.keys().forEach { key -> runCatching { put(key.toLong(), readChroma(obj.getJSONObject(key))) } }
            }
        }
        val markers = buildList<Marker> {
            root.optJSONArray("markers")?.let { array ->
                for (i in 0 until array.length()) {
                    runCatching {
                        val o = array.getJSONObject(i)
                        add(Marker(o.getLong("id"), o.optLong("position", 0L), o.optString("label", ""), o.optInt("color", 0xFFFFB74D.toInt())))
                    }
                }
            }
        }
        val trackStates = buildMap<Int, TrackState> {
            root.optJSONObject("trackStates")?.let { obj ->
                obj.keys().forEach { key -> runCatching { put(key.toInt(), readTrackState(obj.getJSONObject(key))) } }
            }
        }

        return EditorUiState(
            clips = clips,
            audioClips = audio,
            textOverlays = text,
            selectedClipId = clips.firstOrNull()?.id,
            selectedClipIds = clips.firstOrNull()?.id?.let(::setOf) ?: emptySet(),
            playing = false,
            positionMs = 0L,
            durationMs = 0L,
            zoom = root.optDouble("zoom", 1.0).toFloat().coerceIn(.65f, 4f),
            muted = root.optBoolean("muted", false),
            activeTool = Tool.MEDIA,
            effects = readEffects(root.optJSONObject("effects") ?: JSONObject()),
            transitions = transitions,
            nullObjects = nullObjects,
            coverMs = root.optLong("coverMs", 0L),
            aspect = runCatching { AspectRatio.valueOf(root.optString("aspect")) }.getOrDefault(AspectRatio.PORTRAIT),
            easing = runCatching { Easing.valueOf(root.optString("easing", Easing.LINEAR.name)) }.getOrDefault(Easing.LINEAR),
            masks = masks,
            blendModes = blendModes,
            chromaKeys = chromaKeys,
            markers = markers,
            snappingEnabled = root.optBoolean("snappingEnabled", true),
            trackStates = trackStates,
        )
    }

    private fun clip(c: VideoClip) = JSONObject().apply {
        put("id", c.id); put("uri", c.uri.toString()); put("name", c.name)
        put("start", c.startMs); put("end", c.endMs); put("sourceDuration", c.sourceDurationMs); put("volume", c.volume.toDouble())
        put("track", c.track); put("timelineStart", c.timelineStartMs)
        put("keyframes", JSONArray().apply { c.keyframes.forEach { put(keyframe(it)) } })
        put("animation", animation(c.animation))
        put("effects", effects(c.effects))
        c.parentId?.let { put("parent", it) }
        c.groupId?.let { put("group", it) }
    }

    private fun readClip(o: JSONObject, version: Int) = VideoClip(
        id = o.getLong("id"), uri = Uri.parse(o.getString("uri")), name = o.optString("name", "Clip"),
        startMs = o.optLong("start", 0L), endMs = o.optLong("end", 0L),
        sourceDurationMs = o.optLong("sourceDuration", 0L).takeIf { it > 0L } ?: o.optLong("end", 0L).coerceAtLeast(o.optLong("start", 0L)),
        volume = o.optDouble("volume", 1.0).toFloat(),
        track = o.optInt("track", 0), timelineStartMs = o.optLong("timelineStart", 0L),
        keyframes = readKeyframes(o.optJSONArray("keyframes")), animation = readAnimation(o.optJSONObject("animation"), version),
        effects = readEffects(o.optJSONObject("effects") ?: JSONObject()),
        parentId = if (o.has("parent")) o.getLong("parent") else null,
        groupId = if (o.has("group")) o.getLong("group") else null,
    )

    private fun audio(a: AudioClip) = JSONObject().apply {
        put("id", a.id); put("uri", a.uri.toString()); put("name", a.name)
        put("start", a.startMs); put("end", a.endMs); put("sourceDuration", a.sourceDurationMs)
        put("volume", a.volume.toDouble()); put("timelineStart", a.timelineStartMs)
        a.groupId?.let { put("group", it) }
    }

    private fun readAudio(o: JSONObject) = AudioClip(
        id = o.getLong("id"), uri = Uri.parse(o.getString("uri")), name = o.optString("name", "Audio"),
        startMs = o.optLong("start", 0L), endMs = o.optLong("end", 0L),
        sourceDurationMs = o.optLong("sourceDuration", 0L).takeIf { it > 0L } ?: o.optLong("end", 0L).coerceAtLeast(o.optLong("start", 0L)),
        volume = o.optDouble("volume", 1.0).toFloat(),
        timelineStartMs = o.optLong("timelineStart", 0L),
        groupId = if (o.has("group")) o.getLong("group") else null,
    )

    private fun text(t: TextOverlay) = JSONObject().apply {
        put("id", t.id); put("text", t.text); put("start", t.startMs); put("end", t.endMs)
        t.parentId?.let { put("parent", it) }
        t.groupId?.let { put("group", it) }
        put("animation", animation(t.animation))
        put("style", JSONObject().apply {
            put("preset", t.style.preset.name); put("font", t.style.font); put("color", t.style.colorArgb)
            put("size", t.style.size.toDouble()); put("posY", t.style.posY.toDouble())
        })
    }
    private fun readText(o: JSONObject, version: Int) = TextOverlay(
        o.getLong("id"), o.optString("text"), o.optLong("start"), o.optLong("end"),
        o.optJSONObject("style")?.let { s ->
            TextStyleSpec(
                preset = runCatching { TextPreset.valueOf(s.optString("preset")) }.getOrDefault(TextPreset.CLASSIC),
                font = s.optString("font", "sans"),
                colorArgb = s.optInt("color", 0xFFFFFFFF.toInt()),
                size = s.optDouble("size", 64.0).toFloat(),
                posY = s.optDouble("posY", 0.0).toFloat(),
            )
        } ?: TextStyleSpec(),
        parentId = if (o.has("parent")) o.getLong("parent") else null,
        animation = readAnimation(o.optJSONObject("animation"), version),
        groupId = if (o.has("group")) o.getLong("group") else null,
    )

    private fun effects(e: EffectSettings) = JSONObject().apply {
        put("rotation", e.rotation.toDouble()); put("contrast", e.contrast.toDouble()); put("saturation", e.saturation.toDouble())
        put("brightness", e.brightness.toDouble()); put("hue", e.hue.toDouble()); put("blur", e.blur.toDouble()); put("filter", e.filter.name)
    }
    private fun readEffects(o: JSONObject) = EffectSettings(
        rotation = o.optDouble("rotation", 0.0).toFloat(), contrast = o.optDouble("contrast", 0.0).toFloat(), saturation = o.optDouble("saturation", 1.0).toFloat(),
        brightness = o.optDouble("brightness", 0.0).toFloat(), hue = o.optDouble("hue", 0.0).toFloat(), blur = o.optDouble("blur", 0.0).toFloat(),
        filter = runCatching { VideoFilter.valueOf(o.optString("filter", VideoFilter.NONE.name)) }.getOrDefault(VideoFilter.NONE)
    )

    private fun transition(t: Transition) = JSONObject().apply { put("id", t.id); put("from", t.fromClipId); put("to", t.toClipId); put("duration", t.durationMs); put("type", t.type.name) }
    private fun readTransition(o: JSONObject) = Transition(o.getLong("id"), o.getLong("from"), o.getLong("to"), o.optLong("duration", 500), runCatching { TransitionType.valueOf(o.optString("type", TransitionType.CROSS_FADE.name)) }.getOrDefault(TransitionType.CROSS_FADE))

    private fun keyframe(k: Keyframe) = JSONObject().apply { put("time", k.timeMs); put("x", k.x.toDouble()); put("y", k.y.toDouble()); put("scale", k.scale.toDouble()); put("rotation", k.rotation.toDouble()); put("opacity", k.opacity.toDouble()) }
    private fun readKeyframes(a: JSONArray?): List<Keyframe> = buildList {
        if (a != null) for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            add(
                Keyframe(
                    timeMs = o.getLong("time"),
                    x = o.optDouble("x", 0.0).toFloat(),
                    y = o.optDouble("y", 0.0).toFloat(),
                    scale = o.optDouble("scale", 1.0).toFloat(),
                    rotation = o.optDouble("rotation", 0.0).toFloat(),
                    opacity = o.optDouble("opacity", 1.0).toFloat(),
                )
            )
        }
    }

    private fun animation(a: TransformAnimation) = JSONObject().apply {
        put("x", keyframes(a.x)); put("y", keyframes(a.y)); put("scale", keyframes(a.scale)); put("rotation", keyframes(a.rotation)); put("opacity", keyframes(a.opacity))
    }
    private fun keyframes(items: List<AnimatedKeyframe>) = JSONArray().apply {
        items.forEach { frame ->
            put(JSONObject().apply {
                put("time", frame.timeMs)
                put("value", frame.value.toDouble())
                put("easing", frame.easingToNext.name)
                put("curveX1", frame.curveX1.toDouble())
                put("curveY1", frame.curveY1.toDouble())
                put("curveX2", frame.curveX2.toDouble())
                put("curveY2", frame.curveY2.toDouble())
            })
        }
    }

    private fun readAnimation(o: JSONObject?, version: Int): TransformAnimation = TransformAnimation(
        readAnimated(o?.optJSONArray("x"), version),
        readAnimated(o?.optJSONArray("y"), version),
        readAnimated(o?.optJSONArray("scale"), version),
        readAnimated(o?.optJSONArray("rotation"), version),
        readAnimated(o?.optJSONArray("opacity"), version),
    )

    private fun readAnimated(a: JSONArray?, version: Int): List<AnimatedKeyframe> {
        val parsed = buildList {
            if (a != null) for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                add(
                    AnimatedKeyframe(
                        timeMs = o.getLong("time"),
                        value = o.optDouble("value", 0.0).toFloat(),
                        easingToNext = runCatching {
                            Easing.valueOf(o.optString("easing", Easing.LINEAR.name))
                        }.getOrDefault(Easing.LINEAR),
                        curveX1 = o.optDouble("curveX1", 0.25).toFloat(),
                        curveY1 = o.optDouble("curveY1", 0.1).toFloat(),
                        curveX2 = o.optDouble("curveX2", 0.25).toFloat(),
                        curveY2 = o.optDouble("curveY2", 1.0).toFloat(),
                    )
                )
            }
        }
        // Versions <= 4 stored interpolation on the destination keyframe. Shift it to
        // the source keyframe so existing projects keep their visual interpolation.
        return if (version <= 4) {
            parsed.mapIndexed { index, key ->
                key.copy(easingToNext = parsed.getOrNull(index + 1)?.easingToNext ?: Easing.LINEAR)
            }
        } else parsed
    }

    private fun nullObject(n: NullObject) = JSONObject().apply {
        put("id", n.id); put("name", n.name); n.parentId?.let { put("parent", it) }; put("animation", animation(n.animation))
    }

    private fun readNullObject(o: JSONObject, version: Int) = NullObject(
        id = o.getLong("id"), name = o.optString("name", "Null"),
        parentId = if (o.has("parent")) o.getLong("parent") else null,
        animation = readAnimation(o.optJSONObject("animation"), version),
    )

    private fun mask(m: MaskSettings) = JSONObject().apply { put("enabled",m.enabled); put("type",m.type.name); put("feather",m.feather.toDouble()); put("x",m.x.toDouble()); put("y",m.y.toDouble()); put("width",m.width.toDouble()); put("height",m.height.toDouble()); put("invert",m.invert) }
    private fun readMask(o: JSONObject) = MaskSettings(
        invert = o.optBoolean("invert"),
        enabled = o.optBoolean("enabled"),
        type = runCatching { MaskType.valueOf(o.optString("type", MaskType.RECTANGLE.name)) }.getOrDefault(MaskType.RECTANGLE),
        feather = o.optDouble("feather", 0.0).toFloat(),
        x = o.optDouble("x", 0.0).toFloat(),
        y = o.optDouble("y", 0.0).toFloat(),
        width = o.optDouble("width", 1.0).toFloat(),
        height = o.optDouble("height", 1.0).toFloat(),
    )

    private fun chroma(c: ChromaKeySettings) = JSONObject().apply { put("enabled",c.enabled); put("color",c.colorArgb); put("threshold",c.threshold.toDouble()); put("softness",c.softness.toDouble()) }
    private fun readChroma(o: JSONObject) = ChromaKeySettings(o.optBoolean("enabled"),o.optInt("color",0xFF00FF00.toInt()),o.optDouble("threshold",.18).toFloat(),o.optDouble("softness",.08).toFloat())

    private fun marker(m: Marker) = JSONObject().apply { put("id", m.id); put("position", m.positionMs); put("label", m.label); put("color", m.colorArgb) }
    private fun trackState(t: TrackState) = JSONObject().apply { put("locked", t.locked); put("muted", t.muted); put("hidden", t.hidden) }
    private fun readTrackState(o: JSONObject) = TrackState(o.optBoolean("locked"), o.optBoolean("muted"), o.optBoolean("hidden"))
}
