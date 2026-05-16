package com.example.pumpble.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pumpble.dana.commands.DanaRsBolusSpeed
import com.example.pumpble.dana.commands.bolus.BolusOptionResponse
import com.example.pumpble.dana.commands.options.DanaRsUserOptions
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

    // Dialog States
    var showBolusDialog by mutableStateOf(false)
    var bolusAmountInput by mutableStateOf("0.00")
    var bolusSpeed by mutableStateOf(DanaRsBolusSpeed.U12_SECONDS)
    var isProcessingBolus by mutableStateOf(false)

    // User Options Dialog State
    var showUserOptionsDialog by mutableStateOf(false)
    var editingUserOptions by mutableStateOf<DanaRsUserOptions?>(null)
    var isSavingUserOptions by mutableStateOf(false)

    // Bolus Options Dialog State
    var showBolusOptionsDialog by mutableStateOf(false)
    var editingBolusOptions by mutableStateOf<BolusOptionResponse?>(null)
    var isSavingBolusOptions by mutableStateOf(false)

    // Basal Profile Dialog State
    var showBasalProfileDialog by mutableStateOf(false)
    var editingBasalRates by mutableStateOf<List<Double>?>(null)
    var isSavingBasalProfile by mutableStateOf(false)

    fun connectAndHandshake(context: Context) {
        val pump = selectedDevice ?: return
        viewModelScope.launch {
            try {
                activeCommand = "Connecting"
                PumpManager.connect(context, pump, PumpManager.DEFAULT_TX_UUID, PumpManager.DEFAULT_RX_UUID)
                
                activeCommand = "Handshake"
                val deviceName = pump.name.ifBlank { "" }
                if (deviceName.length == 10) {
                    val state = PumpManager.runHandshake(deviceName, null)
                    val modelInfo = if (state.hardwareModel != null) " (Model ${state.hardwareModel}, Prot. v${state.protocol})" else ""
                    PumpManager.setSessionReady(true, modelInfo)
                    LogManager.log("Handshake successful")
                    refreshAllStatus()
                } else {
                    LogManager.log("Handshake failed: Device name not 10 chars")
                }
            } catch (error: Throwable) {
                LogManager.log("Connection failed: ${error.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    fun syncPumpTime() {
        if (!sessionReady) return
        viewModelScope.launch {
            try {
                activeCommand = "Syncing Time"
                val timeZone = java.util.TimeZone.getDefault()
                val currentTime = System.currentTimeMillis()
                val currentOffsetHours = timeZone.getOffset(currentTime) / 3_600_000
                
                LogManager.log("Syncing pump time (UTC with offset $currentOffsetHours)...")
                val response = PumpManager.execute(
                    PumpManager.commands.optionSetPumpUtcAndTimeZone(currentTime, currentOffsetHours)
                )
                LogManager.log("Time sync result: ${response.status}")
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Time sync failed: ${error.message}")
            } finally {
                activeCommand = null
            }
        }
    }

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

    fun openUserOptionsDialog() {
        if (!sessionReady) {
            LogManager.log("Action failed: Handshake required")
            return
        }

        viewModelScope.launch {
            try {
                activeCommand = "Loading Options"
                val response = PumpManager.execute(PumpManager.commands.optionGetUserOption())
                PumpManager.userOptions = response
                
                // Convert response to editable object
                editingUserOptions = DanaRsUserOptions(
                    hwModel = response.selectableLanguages.size.let { if (it > 0) 7 else 0 }, // Simplified HW model detection
                    timeDisplayType24 = response.timeDisplayType24,
                    buttonScrollOnOff = response.buttonScrollOnOff,
                    beepAndAlarm = response.beepAndAlarm,
                    lcdOnTimeSec = response.lcdOnTimeSec,
                    backlightOnTimeSec = response.backlightOnTimeSec,
                    selectedLanguage = response.selectedLanguage,
                    units = response.units,
                    shutdownHour = response.shutdownHour,
                    lowReservoirRate = response.lowReservoirRate,
                    cannulaVolume = response.cannulaVolume,
                    refillAmount = response.refillAmount,
                    target = response.target ?: 0
                )
                showUserOptionsDialog = true
            } catch (e: Exception) {
                LogManager.log("Failed to fetch user options: ${e.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    fun saveUserOptions() {
        val options = editingUserOptions ?: return
        viewModelScope.launch {
            try {
                isSavingUserOptions = true
                activeCommand = "Saving Options"
                LogManager.log("Saving User Options...")
                val response = PumpManager.execute(PumpManager.commands.optionSetUserOption(options))
                LogManager.log("Save Options result: ${response.status}")
                showUserOptionsDialog = false
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Save failed: ${error.message}")
            } finally {
                isSavingUserOptions = false
                activeCommand = null
            }
        }
    }

    fun openBolusOptionsDialog() {
        if (!sessionReady) {
            LogManager.log("Action failed: Handshake required")
            return
        }
        viewModelScope.launch {
            try {
                activeCommand = "Loading Bolus Options"
                val response = PumpManager.execute(PumpManager.commands.bolusGetBolusOption())
                PumpManager.bolusOptions = response
                editingBolusOptions = response
                showBolusOptionsDialog = true
            } catch (e: Exception) {
                LogManager.log("Failed to fetch bolus options: ${e.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    fun saveBolusOptions() {
        val options = editingBolusOptions ?: return
        viewModelScope.launch {
            try {
                isSavingBolusOptions = true
                activeCommand = "Saving Bolus Options"
                LogManager.log("Saving Bolus Options...")
                val response = PumpManager.execute(
                    PumpManager.commands.bolusSetBolusOption(
                        extendedBolusEnabled = options.extendedBolusEnabled,
                        bolusCalculationOption = options.bolusCalculationOption,
                        missedBolusConfig = options.missedBolusConfig,
                        missedBolusWindows = options.missedBolusWindows
                    )
                )
                LogManager.log("Save Bolus Options result: ${response.status}")
                showBolusOptionsDialog = false
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Save failed: ${error.message}")
            } finally {
                isSavingBolusOptions = false
                activeCommand = null
            }
        }
    }

    fun openBasalProfileDialog() {
        if (!sessionReady) {
            LogManager.log("Action failed: Handshake required")
            return
        }
        viewModelScope.launch {
            try {
                activeCommand = "Loading Basal Profile"
                val response = PumpManager.execute(PumpManager.commands.basalGetBasalRate())
                PumpManager.basalRateInfo = response
                editingBasalRates = response.hourlyRatesUnits
                showBasalProfileDialog = true
            } catch (e: Exception) {
                LogManager.log("Failed to fetch basal profile: ${e.message}")
            } finally {
                activeCommand = null
            }
        }
    }

    fun saveBasalProfile() {
        val rates = editingBasalRates ?: return
        viewModelScope.launch {
            try {
                isSavingBasalProfile = true
                activeCommand = "Saving Basal Profile"
                LogManager.log("Saving Basal Profile...")
                
                // Fetch current profile number first
                val profileInfo = PumpManager.execute(PumpManager.commands.basalGetProfileNumber())
                
                val response = PumpManager.execute(
                    PumpManager.commands.basalSetProfileBasalRate(
                        profileNumber = profileInfo.activeProfile,
                        hourlyRatesUnits = rates
                    )
                )
                LogManager.log("Save Basal Profile result: ${response.status}")
                showBasalProfileDialog = false
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Save failed: ${error.message}")
            } finally {
                isSavingBasalProfile = false
                activeCommand = null
            }
        }
    }

    fun openBolusDialog() {
        if (!sessionReady) {
            LogManager.log("Action failed: Handshake required")
            return
        }
        
        // Use last known step bolus amount or a safe default
        bolusAmountInput = "0.00"
        showBolusDialog = true
        
        // Auto-refresh bolus info if it's missing or old
        if (stepBolusInfo == null) {
            viewModelScope.launch {
                try {
                    activeCommand = "Fetching Limits"
                    PumpManager.stepBolusInfo = PumpManager.execute(PumpManager.commands.bolusGetStepBolusInformation())
                } catch (e: Exception) {
                    LogManager.log("Failed to fetch bolus limits: ${e.message}")
                } finally {
                    activeCommand = null
                }
            }
        }
    }

    fun startStepBolus() {
        val amount = bolusAmountInput.toDoubleOrNull() ?: return
        if (amount <= 0) return
        
        viewModelScope.launch {
            try {
                isProcessingBolus = true
                activeCommand = "Sending Bolus"
                LogManager.log("Sending Bolus: $amount U...")
                
                val response = PumpManager.execute(
                    PumpManager.commands.bolusSetStepBolusStart(
                        amountUnits = amount,
                        speed = bolusSpeed
                    )
                )
                
                LogManager.log("Bolus result: ${response.status}")
                showBolusDialog = false
                
                // Refresh status after a short delay to see the updated reservoir/IOB
                kotlinx.coroutines.delay(2000)
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Bolus failed: ${error.message}")
            } finally {
                isProcessingBolus = false
                activeCommand = null
            }
        }
    }
    
    fun stopBolus() {
        viewModelScope.launch {
            try {
                activeCommand = "Stopping Bolus"
                LogManager.log("Stopping Bolus...")
                val response = PumpManager.execute(PumpManager.commands.bolusSetStepBolusStop())
                LogManager.log("Stop Bolus result: ${response.status}")
                refreshAllStatus()
            } catch (error: Throwable) {
                LogManager.log("Stop Bolus failed: ${error.message}")
            } finally {
                activeCommand = null
            }
        }
    }
}