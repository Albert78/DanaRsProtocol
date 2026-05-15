package com.example.pumpble.dana.commands.aps

import com.example.pumpble.commands.PumpStatus
import com.example.pumpble.dana.commands.DanaRsResponse

sealed interface ApsHistoryEventsChunk : DanaRsResponse

/**
 * Wire-level APS history event types used by DanaRS-compatible pumps.
 */
enum class ApsHistoryEventKind(val wireValue: Int) {
    TEMP_START(1),
    TEMP_STOP(2),
    EXTENDED_START(3),
    EXTENDED_STOP(4),
    BOLUS(5),
    DUAL_BOLUS(6),
    DUAL_EXTENDED_START(7),
    DUAL_EXTENDED_STOP(8),
    SUSPEND_ON(9),
    SUSPEND_OFF(10),
    REFILL(11),
    PRIME(12),
    PROFILE_CHANGE(13),
    CARBS(14),
    PRIME_CANNULA(15),
    TIME_CHANGE(16),
    UNKNOWN(-1);

    companion object {
        fun fromWireValue(value: Int): ApsHistoryEventKind =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

/**
 * One decoded APS history event.
 *
 * The pump always provides two 16-bit parameters. Their semantic meaning depends on [kind], so the
 * raw values are retained and common derived values are exposed where the reference protocol defines
 * them.
 */
data class ApsHistoryEvent(
    val kind: ApsHistoryEventKind,
    val recordCode: Int,
    val timestampMillis: Long,
    val pumpId: Long,
    val param1: Int,
    val param2: Int,
    val insulinUnits: Double?,
    val durationMinutes: Int?,
    val ratioPercent: Int?,
    val carbohydrateGrams: Int?,
    val currentRateUnitsPerHour: Double?,
    val previousTimestampMillis: Long?,
)

data class ApsHistoryEventChunk(
    override val status: PumpStatus,
    val event: ApsHistoryEvent,
) : ApsHistoryEventsChunk

data class ApsHistoryEndChunk(
    override val status: PumpStatus = PumpStatus.OK,
) : ApsHistoryEventsChunk

/**
 * Final APS history result after all event packets have been received and normalized.
 */
data class ApsHistoryEventsResult(
    val events: List<ApsHistoryEvent>,
)
