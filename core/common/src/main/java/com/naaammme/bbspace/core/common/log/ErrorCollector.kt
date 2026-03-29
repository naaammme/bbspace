package com.naaammme.bbspace.core.common.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ErrorLog(
    val time: Long,
    val tag: String,
    val message: String,
    val stackTrace: String?
)

object ErrorCollector {
    private const val MAX = 200

    private val buffer = ArrayDeque<ErrorLog>(MAX)
    private val _flow = MutableStateFlow<List<ErrorLog>>(emptyList())
    val flow: StateFlow<List<ErrorLog>> = _flow.asStateFlow()

    fun record(tag: String, message: String, tr: Throwable?) {
        val entry = ErrorLog(
            time = System.currentTimeMillis(),
            tag = tag,
            message = message,
            stackTrace = tr?.stackTraceToString()
        )
        synchronized(buffer) {
            if (buffer.size >= MAX) buffer.removeFirst()
            buffer.addLast(entry)
            _flow.value = buffer.toList().asReversed()
        }
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            _flow.value = emptyList()
        }
    }
}
