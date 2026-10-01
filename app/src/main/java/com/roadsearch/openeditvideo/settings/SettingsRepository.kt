package com.roadsearch.openeditvideo.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "openeditvideo_settings")

class SettingsRepository(private val context: Context) {
    private val darkThemeKey = booleanPreferencesKey("dark_theme")
    private val timelineZoomKey = floatPreferencesKey("timeline_zoom")

    val darkTheme: Flow<Boolean> = context.settingsDataStore.data.map { it[darkThemeKey] ?: true }
    val defaultTimelineZoom: Flow<Float> = context.settingsDataStore.data.map { (it[timelineZoomKey] ?: 1f).coerceIn(.65f, 4f) }

    suspend fun setDarkTheme(enabled: Boolean) { context.settingsDataStore.edit { it[darkThemeKey] = enabled } }
    suspend fun setDefaultTimelineZoom(value: Float) { context.settingsDataStore.edit { it[timelineZoomKey] = value.coerceIn(.65f, 4f) } }
}
