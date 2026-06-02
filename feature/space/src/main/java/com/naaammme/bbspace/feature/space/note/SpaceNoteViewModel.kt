package com.naaammme.bbspace.feature.space.note

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Immutable
data class SpaceNoteUiState(
    val content: String = ""
)

@HiltViewModel
class SpaceNoteViewModel @Inject constructor(
    private val dao: SpaceNoteDao
) : ViewModel() {
    private val _uiState = MutableStateFlow(SpaceNoteUiState())
    val uiState: StateFlow<SpaceNoteUiState> = _uiState.asStateFlow()

    private var observedUid = 0L
    private var observeJob: Job? = null

    fun observeNote(uid: Long) {
        if (uid <= 0L || uid == observedUid) return
        observedUid = uid
        _uiState.update {
            it.copy(content = "")
        }
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            dao.observeContent(uid).collectLatest { content ->
                _uiState.update {
                    it.copy(content = content.orEmpty())
                }
            }
        }
    }

    fun saveNote(
        uid: Long,
        name: String,
        face: String?,
        content: String
    ) {
        if (uid <= 0L) return
        viewModelScope.launch {
            val trimmedContent = content.trim()
            if (trimmedContent.isEmpty()) {
                dao.deleteByUid(uid)
            } else {
                dao.upsert(
                    SpaceNoteEntity(
                        uid = uid,
                        name = name,
                        face = face,
                        content = trimmedContent
                    )
                )
            }
        }
    }

    suspend fun exportNoteJson(outputStream: OutputStream?): Boolean = withContext(Dispatchers.IO) {
        if (outputStream == null) return@withContext false
        runCatching {
            val arr = JSONArray()
            dao.getAllNonBlank().forEach { note ->
                arr.put(
                    JSONObject()
                        .put("uid", note.uid)
                        .put("name", note.name)
                        .put("face", note.face)
                        .put("content", note.content)
                )
            }
            outputStream.use { out ->
                out.write(arr.toString(2).replace("\\/", "/").toByteArray(Charsets.UTF_8))
            }
            true
        }.getOrDefault(false)
    }

    suspend fun importNoteJson(inputStream: InputStream?): Result<Int> = withContext(Dispatchers.IO) {
        if (inputStream == null) return@withContext Result.failure(Exception("InputStream is null"))
        runCatching {
            val json = inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val arr = JSONArray(json)
            val items = buildList {
                repeat(arr.length()) { index ->
                    val obj = arr.getJSONObject(index)
                    val uid = obj.getLong("uid")
                    val content = obj.getString("content").trim()
                    if (uid > 0L) {
                        add(
                            SpaceNoteEntity(
                                uid = uid,
                                name = obj.optString("name"),
                                face = obj.optString("face").takeIf(String::isNotBlank),
                                content = content
                            )
                        )
                    }
                }
            }
            dao.upsertAll(items)
            items.size
        }
    }
}