package com.example.pumpble.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pumpble.dana.commands.aps.ApsHistoryEvent
import kotlinx.coroutines.launch

class HistoryViewModel : ViewModel() {
    val apsEvents = mutableStateListOf<ApsHistoryEvent>()
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    val sessionReady get() = PumpManager.sessionReady

    fun loadApsHistory() {
        if (!sessionReady) return
        
        viewModelScope.launch {
            try {
                isLoading = true
                error = null
                LogManager.log("Fetching APS history events...")
                
                // Fetch events from the last 24 hours
                val fromTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
                val result = PumpManager.executeStream(PumpManager.commands.apsHistoryEvents(fromTime))
                
                apsEvents.clear()
                apsEvents.addAll(result.events.sortedByDescending { it.timestampMillis })
                
                LogManager.log("Fetched ${result.events.size} history events")
            } catch (e: Exception) {
                error = e.message
                LogManager.log("History fetch failed: ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }
}