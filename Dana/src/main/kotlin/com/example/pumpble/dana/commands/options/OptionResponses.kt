package com.example.pumpble.dana.commands.options

import com.example.pumpble.commands.PumpStatus
import com.example.pumpble.dana.commands.DanaRsResponse
import java.time.LocalDateTime

/**
 * Pump-local clock value, decoded without applying a phone timezone conversion.
 */
data class OptionPumpTimeResponse(
    override val status: PumpStatus,
    val pumpTime: LocalDateTime,
) : DanaRsResponse

/**
 * UTC clock value plus the signed hour offset stored by newer Dana firmware.
 */
data class OptionPumpUtcAndTimeZoneResponse(
    override val status: PumpStatus,
    val pumpUtcTime: LocalDateTime,
    val zoneOffsetHours: Int,
) : DanaRsResponse

/**
 * User-visible pump settings returned by OPTION_GET_USER_OPTION.
 *
 * Older firmware may omit the target value; in that case [target] is null and all known bytes are
 * still decoded.
 */
data class OptionUserOptionsResponse(
    override val status: PumpStatus,
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
    val selectableLanguages: List<Int>,
    val target: Int?,
) : DanaRsResponse
