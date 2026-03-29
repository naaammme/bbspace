package com.naaammme.bbspace.core.common.log

import android.util.Log

object Logger {
    var isDebug = false
        private set

    fun init(debug: Boolean) {
        isDebug = debug
    }

    inline fun d(tag: String, msg: () -> String) {
        if (isDebug) {
            Log.d(tag, msg())
        }
    }

    inline fun i(tag: String, msg: () -> String) {
        if (isDebug) {
            Log.i(tag, msg())
        }
    }

    inline fun w(tag: String, msg: () -> String) {
        if (isDebug) {
            Log.w(tag, msg())
        }
    }

    inline fun e(tag: String, tr: Throwable? = null, msg: () -> String) {
        val message = msg()
        ErrorCollector.record(tag, message, tr)
        if (isDebug) {
            if (tr != null) {
                Log.e(tag, message, tr)
            } else {
                Log.e(tag, message)
            }
        }
    }
}
