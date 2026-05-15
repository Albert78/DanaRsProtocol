package com.example.pumpble.dana.commands.bolus

import com.example.pumpble.commands.PumpStatus
import com.example.pumpble.dana.commands.DANA_UNITS_MGDL
import com.example.pumpble.dana.commands.DANA_UNITS_MMOL
import com.example.pumpble.dana.commands.DanaRsResponse
import java.time.LocalTime

enum class DanaGlucoseUnits(val wireValue: Int) {
    MGDL(DANA_UNITS_MGDL),
    MMOL(DANA_UNITS_MMOL),
    UNKNOWN(-1);

    companion object {
        fun fromWireValue(value: Int): DanaGlucoseUnits = entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

/**
 * One paired carbohydrate-ratio/correction-factor value for a specific hour.
 */
data class CirCfValue(
    val cir: Double,
    val cf: Double,
)

/**
 * Full 24-hour CIR/CF table. Correction factors are normalized according to the pump's glucose unit.
 */
data class Bolus24CirCfArrayResponse(
    override val status: PumpStatus,
    val units: DanaGlucoseUnits,
    val valuesByHour: List<CirCfValue>,
) : DanaRsResponse

data class MissedBolusWindow(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
)

/**
 * Bolus feature flags and missed-bolus reminder windows.
 */
data class BolusOptionResponse(
    override val status: PumpStatus,
    val extendedBolusEnabled: Boolean,
    val bolusCalculationOption: Int,
    val missedBolusConfig: Int,
    val missedBolusWindows: List<MissedBolusWindow>,
) : DanaRsResponse

/**
 * Inputs and pump-calculated values used by the bolus calculator screen.
 */
data class BolusCalculationInformationResponse(
    override val status: PumpStatus,
    val errorCode: Int,
    val units: DanaGlucoseUnits,
    val currentBloodGlucose: Double,
    val carbohydrateGrams: Int,
    val currentTarget: Double,
    val currentCarbRatio: Int,
    val currentCorrectionFactor: Double,
    val insulinOnBoardUnits: Double,
) : DanaRsResponse

/**
 * Compact CIR/CF profile used by older bolus calculator commands.
 */
data class BolusCirCfArrayResponse(
    override val status: PumpStatus,
    val language: Int,
    val units: DanaGlucoseUnits,
    val cirValues: List<Int>,
    val cfValues: List<Double>,
) : DanaRsResponse

/**
 * Current limits and last-delivery information for step bolus delivery.
 */
data class BolusStepBolusInformationResponse(
    override val status: PumpStatus,
    val errorCode: Int,
    val bolusType: Int,
    val initialBolusAmountUnits: Double,
    val lastBolusTimeOfDay: LocalTime,
    val lastBolusAmountUnits: Double,
    val maxBolusUnits: Double,
    val bolusStepUnits: Double,
) : DanaRsResponse
