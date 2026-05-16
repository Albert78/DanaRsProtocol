package com.example.pumpble.dana.commands.history

import com.example.pumpble.commands.PumpStatus
import com.example.pumpble.dana.commands.DanaRsResponse
import java.time.LocalDateTime

/**
 * Base type for one packet returned by a classic Dana history command.
 *
 * History transfers are multi-packet streams: each notification is either one decoded record or an
 * end marker with the pump's result code.
 */
sealed interface DanaRsHistoryResponse : DanaRsResponse

/**
 * Terminal packet for a history transfer.
 */
data class HistoryEndResponse(
    override val status: PumpStatus,
    val errorCode: Int,
    val totalCount: Int?,
) : DanaRsHistoryResponse

/**
 * Wire-level DanaRS history record types used by the classic history commands.
 */
enum class DanaRsHistoryRecordKind(val wireValue: Int) {
    BOLUS(0x02),
    DAILY(0x03),
    PRIME(0x04),
    REFILL(0x05),
    GLUCOSE(0x06),
    CARBOHYDRATE(0x07),
    SUSPEND(0x09),
    ALARM(0x0a),
    BASAL_HOUR(0x0b),
    TEMP_BASAL(0x99),
    UNKNOWN(-1);

    companion object {
        fun fromWireValue(value: Int): DanaRsHistoryRecordKind =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

/**
 * Decoded classic history record.
 *
 * The original format reuses several bytes for different record types. Fields that are not meaningful
 * for the current [kind] are left null instead of inventing placeholder values.
 */
data class DanaRsHistoryRecord(
    /**
     * Raw record code from the pump.
     */
    val recordCode: Int,
    /**
     * Type of the history record.
     */
    val kind: DanaRsHistoryRecordKind,
    /**
     * Local timestamp of the record.
     */
    val timestamp: LocalDateTime,
    /**
     * Readable message type.
     */
    val messageType: String?,
    /**
     * Primary numeric value (e.g., Units for Bolus, mg/dL for Glucose).
     */
    val value: Double?,
    /**
     * Duration in minutes (for extended delivery).
     */
    val durationMinutes: Int?,
    /**
     * Type of bolus (S: Step, E: Extended, DS: Dual-Step, DE: Dual-Extended).
     */
    val bolusType: String?,
    /**
     * Alarm description if kind is ALARM.
     */
    val alarm: String?,
    /**
     * String value for state-based records (e.g., Suspend On/Off).
     */
    val stringValue: String?,
    /**
     * Daily basal total (for DAILY records).
     */
    val dailyBasalUnits: Double?,
    /**
     * Daily bolus total (for DAILY records).
     */
    val dailyBolusUnits: Double?,
    /**
     * Raw 8-bit history sub-code.
     */
    val rawHistoryCode: Int,
    /**
     * Raw 16-bit primary value.
     */
    val rawValue: Int,
)

/**
 * One non-terminal history packet.
 */
data class HistoryRecordResponse(
    override val status: PumpStatus,
    val record: DanaRsHistoryRecord,
) : DanaRsHistoryResponse

/**
 * Complete result of a classic history transfer after the terminal packet has been received.
 */
data class DanaRsHistoryResult(
    val records: List<DanaRsHistoryRecord>,
    val end: HistoryEndResponse,
)