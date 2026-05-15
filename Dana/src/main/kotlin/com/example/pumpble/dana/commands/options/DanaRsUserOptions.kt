package com.example.pumpble.dana.commands.options

data class DanaRsUserOptions(
    val hwModel: Int,
    val timeDisplayType24: Boolean,
    val buttonScrollOnOff: Boolean,
    val beepAndAlarm: Int,
    val lcdOnTimeSec: Int,
    val backlightOnTimeSec: Int,
    val selectedLanguage: Int,
    val units: Int,
    val shutdownHour: Int,
    val lowReservoirRate: Int,
    val cannulaVolume: Int,
    val refillAmount: Int,
    val target: Int,
)
