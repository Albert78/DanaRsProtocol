package com.example.pumpble.dana.commands.bolus

import com.example.pumpble.commands.PumpStatus
import com.example.pumpble.dana.commands.DanaRsBolusSpeed
import com.example.pumpble.dana.commands.DanaRsResponse

/**
 * Global bolus rate settings (limits and speed).
 */
data class BolusRateResponse(
    override val status: PumpStatus,
    val maxBolusUnits: Double,
    val bolusStepUnits: Double,
    val bolusSpeed: DanaRsBolusSpeed,
) : DanaRsResponse