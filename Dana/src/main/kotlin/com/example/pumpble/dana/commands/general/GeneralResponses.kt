package com.example.pumpble.dana.commands.general

import com.example.pumpble.commands.PumpStatus
import com.example.pumpble.dana.commands.DanaRsResponse
import java.time.LocalDate

enum class DanaRsPumpErrorState(val code: Int) {
    NONE(0x00),
    SUSPENDED(0x01),
    DAILY_MAX(0x02),
    BOLUS_BLOCK(0x04),
    ORDER_DELIVERING(0x08),
    NO_PRIME(0x10),
    UNKNOWN(-1);

    companion object {
        fun fromCode(code: Int): DanaRsPumpErrorState = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/**
 * Snapshot shown by the pump's initial screen command.
 *
 * Most numeric insulin values are transmitted as centi-units. The parser exposes them as pump units
 * to keep application code independent from the wire scaling.
 */
data class GeneralInitialScreenInformationResponse(
    override val status: PumpStatus,
    val pumpSuspended: Boolean,
    val tempBasalInProgress: Boolean,
    val extendedBolusInProgress: Boolean,
    val dualBolusInProgress: Boolean,
    val dailyTotalUnits: Double,
    val maxDailyTotalUnits: Int,
    val reservoirRemainingUnits: Double,
    val currentBasalUnitsPerHour: Double,
    val tempBasalPercent: Int,
    val batteryRemainingPercent: Int,
    val extendedBolusAbsoluteRate: Double,
    val insulinOnBoardUnits: Double,
    val errorState: DanaRsPumpErrorState,
) : DanaRsResponse

/**
 * Firmware capability information returned during the normal DanaRS/Dana-i pump check.
 */
data class GeneralPumpCheckResponse(
    override val status: PumpStatus,
    val hardwareModel: Int,
    val protocol: Int,
    val productCode: Int,
    val supportedFirmware: Boolean,
) : DanaRsResponse

/**
 * Manufacturing metadata reported by the pump.
 */
data class GeneralShippingInformationResponse(
    override val status: PumpStatus,
    val serialNumber: String,
    val shippingCountry: String,
    val shippingDate: LocalDate,
) : DanaRsResponse
