package com.naaammme.bbspace.feature.settings.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class HasUpdate(val version: String, val url: String) : UpdateState
    data class Error(val msg: String) : UpdateState
}

@HiltViewModel
class AboutViewModel @Inject constructor() : ViewModel() {

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    companion object {
        private const val RELEASES_API = "https://api.github.com/repos/naaammme/bbspace/releases/latest"
    }

    fun checkUpdate(currentVersion: String) {
        if (_updateState.value == UpdateState.Checking) return
        _updateState.value = UpdateState.Checking
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL(RELEASES_API).openConnection() as HttpURLConnection
                    conn.setRequestProperty("Accept", "application/vnd.github+json")
                    conn.connectTimeout = 10_000
                    conn.readTimeout = 10_000
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val json = JSONObject(body)
                    val tag = json.getString("tag_name").trimStart('v', 'V')
                    val htmlUrl = json.getString("html_url")
                    tag to htmlUrl
                }
            }
            result.fold(
                onSuccess = { (tag, url) ->
                    val current = currentVersion.trimStart('v', 'V')
                    _updateState.value = if (tag == current) UpdateState.UpToDate
                    else UpdateState.HasUpdate(tag, url)
                },
                onFailure = { _updateState.value = UpdateState.Error(it.message ?: "未知错误") }
            )
        }
    }
}
