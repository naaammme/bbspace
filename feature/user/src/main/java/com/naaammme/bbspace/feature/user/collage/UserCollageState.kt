package com.naaammme.bbspace.feature.user.collage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

@Composable
internal fun rememberUserCollageState(): UserCollageState {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val prefs = remember(context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    val state = remember(prefs) {
        UserCollageState(
            prefs = prefs,
            initialOffsets = loadUserCollageOffsets(prefs)
        )
    }

    DisposableEffect(owner, state) {
        val lifecycle = owner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) state.flush()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    return state
}

@Stable
internal class UserCollageState(
    private val prefs: SharedPreferences,
    initialOffsets: Map<String, Offset>
) {
    val offsets = mutableStateMapOf<String, Offset>().apply {
        putAll(initialOffsets)
    }
    private var dirty = false

    fun updateOffset(key: String, offset: Offset) {
        if (abs(offset.x) < COLLAGE_OFFSET_ZERO_EPSILON && abs(offset.y) < COLLAGE_OFFSET_ZERO_EPSILON) {
            offsets.remove(key)
        } else {
            offsets[key] = offset
        }
        dirty = true
    }

    fun reset() {
        dirty = false
        offsets.clear()
        prefs.edit {
            remove(KEY_COLLAGE_LAYOUT)
        }
    }

    fun flush() {
        if (!dirty) return
        dirty = false
        val encoded = offsets.entries
            .sortedBy { it.key }
            .joinToString("|") { (key, offset) -> "$key:${offset.x},${offset.y}" }
        prefs.edit {
            if (encoded.isEmpty()) {
                remove(KEY_COLLAGE_LAYOUT)
            } else {
                putString(KEY_COLLAGE_LAYOUT, encoded)
            }
        }
    }
}

private fun loadUserCollageOffsets(prefs: SharedPreferences): Map<String, Offset> {
    val encoded = prefs.getString(KEY_COLLAGE_LAYOUT, null)
        ?.takeIf(String::isNotBlank)
        ?: return emptyMap()

    return buildMap {
        encoded.split('|').forEach { item ->
            val keyValue = item.split(':', limit = 2)
            if (keyValue.size != 2 || keyValue[0].isBlank()) return@forEach
            val axes = keyValue[1].split(',', limit = 2)
            if (axes.size != 2) return@forEach
            val x = axes[0].toFloatOrNull() ?: return@forEach
            val y = axes[1].toFloatOrNull() ?: return@forEach
            put(keyValue[0], Offset(x, y))
        }
    }
}

private const val PREFS_NAME = "user_collage"
private const val KEY_COLLAGE_LAYOUT = "layout"
private const val COLLAGE_OFFSET_ZERO_EPSILON = 0.5f