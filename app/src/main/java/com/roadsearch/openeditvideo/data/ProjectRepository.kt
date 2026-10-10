package com.roadsearch.openeditvideo.data

import android.content.Context
import com.roadsearch.openeditvideo.model.EditorUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import java.util.UUID

/**
 * Multi-project store. The Room schema is unchanged (id/name/updatedAt/documentJson), so no migration is needed:
 * the former single "default" row simply becomes one project among others.
 */
class ProjectRepository(context: Context) {
    companion object { const val DEFAULT_PROJECT_ID = "default" }

    private val dao = AppDatabase.get(context).projectDao()

    private val _currentId = MutableStateFlow(DEFAULT_PROJECT_ID)
    /** Project currently targeted by the editor. */
    val currentId: StateFlow<String> = _currentId

    fun open(id: String) { _currentId.value = id }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(): Flow<ProjectEntity?> = _currentId.flatMapLatest { dao.observe(it) }
    fun observeAll(): Flow<List<ProjectEntity>> = dao.observeAll()

    suspend fun get(id: String = _currentId.value): ProjectEntity? = dao.get(id)

    suspend fun create(name: String = "Nouveau projet"): String {
        val id = UUID.randomUUID().toString()
        dao.upsert(ProjectEntity(id, name, System.currentTimeMillis(), EditorStateCodec.encode(EditorUiState())))
        return id
    }

    suspend fun rename(id: String, name: String) {
        val p = dao.get(id) ?: return
        dao.upsert(p.copy(name = name.ifBlank { p.name }, updatedAt = System.currentTimeMillis()))
    }

    suspend fun duplicate(id: String): String? {
        val p = dao.get(id) ?: return null
        val copy = p.copy(id = UUID.randomUUID().toString(), name = "${p.name} (copie)", updatedAt = System.currentTimeMillis())
        dao.upsert(copy)
        return copy.id
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun save(state: EditorUiState, id: String = _currentId.value) = saveJson(EditorStateCodec.encode(state), id)

    /** Persists [json] for [id], keeping the existing name and skipping no-op writes (so merely opening a project doesn't reorder the list). */
    suspend fun saveJson(json: String, id: String = _currentId.value, defaultName: String = "Nouveau projet") {
        val existing = dao.get(id)
        if (existing?.documentJson == json) return
        dao.upsert(ProjectEntity(id, existing?.name ?: defaultName, System.currentTimeMillis(), json))
    }
}
