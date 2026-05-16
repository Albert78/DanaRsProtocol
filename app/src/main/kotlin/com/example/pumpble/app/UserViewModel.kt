package com.example.pumpble.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class UserViewModel(application: Application) : AndroidViewModel(application) {
    
    // Bind to PumpManager
    val connectionState get() = PumpManager.connectionState
    val selectedDevice get() = PumpManager.selectedDevice
    val sessionReady get() = PumpManager.sessionReady
    val lastSyncTime get() = PumpManager.lastSyncTime
    val pumpStatus get() = PumpManager.pumpStatus
    val userOptions get() = PumpManager.userOptions
    val bolusOptions get() = PumpManager.bolusOptions
    val stepBolusInfo get() = PumpManager.stepBolusInfo
    val basalRateInfo get() = PumpManager.basalRateInfo
    val pumpTimeInfo get() = PumpManager.pumpTimeInfo
    
    var activeCommand by mutableStateOf<String?>(null)

    fun refreshAllStatus() {
        if (!sessionReady) {
            LogManager.log("Refresh failed: Session not ready")
            return
        }

        viewModelScope.launch {
            try {
                activeCommand = "Syncing Status"
                LogManager.log("Refreshing pump status...")

                // Execute all sync commands via PumpManager
                PumpManager.pumpStatus = PumpManager.execute(PumpManager.commands.generalInitialScreenInformation())
                PumpManager.basalRateInfo = PumpManager.execute(PumpManager.commands.basalGetBasalRate())
                PumpManager.userOptions = PumpManager.execute(PumpManager.commands.optionGetUserOption())
                PumpManager.bolusOptions = PumpManager.execute(PumpManager.commands.bolusGetBolusOption())
                PumpManager.stepBolusInfo = PumpManager.execute(PumpManager.commands.bolusGetStepBolusInformation())
                PumpManager.pumpTimeInfo = PumpManager.execute(PumpManager.commands.optionGetPumpUtcAndTimeZone())

                PumpManager.lastSyncTime = System.currentTimeMillis()
                LogManager.log("Sync successful")
            } catch (error: Throwable) {
                LogManager.log("Sync failed: ${error.message}")
            } finally {
                activeCommand = null
            }
        }
    }
}