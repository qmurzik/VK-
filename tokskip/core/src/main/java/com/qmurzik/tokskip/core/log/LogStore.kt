package com.qmurzik.tokskip.core.log

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A small on-device log (no network, nothing leaves the phone) shown on the "Логи" screen. */
class LogStore(context: Context) {
    private val file = File(context.filesDir, "tokskip.log")
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT)
    private val _lines = MutableStateFlow(read())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    private fun read(): List<String> = runCatching { if (file.exists()) file.readLines().takeLast(MAX_LINES) else emptyList() }
        .getOrDefault(emptyList())

    @Synchronized
    fun log(tag: String, message: String) {
        Log.i(TAG, "[$tag] $message")
        val line = "${fmt.format(Date())} [$tag] $message"
        val next = (_lines.value + line).takeLast(MAX_LINES)
        _lines.value = next
        runCatching {
            if (file.length() > MAX_BYTES) file.writeText(next.joinToString("\n", postfix = "\n"))
            else file.appendText(line + "\n")
        }
    }

    @Synchronized
    fun clear() {
        _lines.value = emptyList()
        runCatching { file.delete() }
    }

    companion object {
        const val TAG = "TokSkip"
        private const val MAX_LINES = 1500
        private const val MAX_BYTES = 256 * 1024L
    }
}
