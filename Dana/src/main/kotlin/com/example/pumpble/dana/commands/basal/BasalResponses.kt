package com.example.pumpble.dana.commands.basal

import com.example.pumpble.commands.PumpStatus
import com.example.pumpble.dana.commands.DanaRsResponse

/**
 * Active basal profile returned as 24 hourly rates plus pump-side limits.
 */
data class BasalRateProfileResponse(
    override val status: PumpStatus,
    val maxBasalUnitsPerHour: Double,
    val basalStepUnits: Double,
    val hourlyRatesUnits: List<Double>,
    val basalStepSupported: Boolean,
) : DanaRsResponse

/**
 * The one-byte active profile index reported by the pump.
 */
data class BasalProfileNumberResponse(
    override val status: PumpStatus,
    val activeProfile: Int,
) : DanaRsResponse
