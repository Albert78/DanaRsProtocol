package com.example.pumpble.app

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogManager {
    val logLines = mutableStateListOf<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    private const val MAX_LOG_LINES = 200

    fun log(message: String) {
        val time = timeFormat.format(Date())
        logLines.add(0, "$time  $message")
        if (logLines.size > MAX_LOG_LINES) {
            logLines.removeAt(logLines.lastIndex)
        }
    }
}