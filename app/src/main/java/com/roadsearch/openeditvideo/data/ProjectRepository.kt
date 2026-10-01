package com.roadsearch.openeditvideo.data

import android.content.Context
import com.roadsearch.openeditvideo.model.EditorUiState
import kotlinx.coroutines.flow.Flow

class ProjectRepository(context: Context) {
    companion object { const val DEFAULT_PROJECT_ID = "default" }
    private val dao = AppDatabase.get(context).projectDao()

    fun observe(): Flow<ProjectEntity?> = dao.observe(DEFAULT_PROJECT_ID)
    suspend fun get(): ProjectEntity? = dao.get(DEFAULT_PROJECT_ID)

    suspend fun save(state: EditorUiState, name: String = "Nouveau projet") {
        saveJson(EditorStateCodec.encode(state), name)
    }

    suspend fun saveJson(json: String, name: String = "Nouveau projet") {
        dao.upsert(ProjectEntity(DEFAULT_PROJECT_ID, name, System.currentTimeMillis(), json))
    }
}
