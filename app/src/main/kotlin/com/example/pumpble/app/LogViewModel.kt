package com.example.pumpble.app

import androidx.lifecycle.ViewModel

class LogViewModel : ViewModel() {
    val logLines get() = LogManager.logLines
}