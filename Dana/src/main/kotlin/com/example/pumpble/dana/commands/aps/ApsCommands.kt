package com.example.pumpble.dana.commands.aps

import com.example.pumpble.commands.PumpStatus
import com.example.pumpble.commands.PumpStreamCommand
import com.example.pumpble.dana.commands.DanaRsAckPacketCommand
import com.example.pumpble.dana.commands.DanaRsPacketCommand
import com.example.pumpble.dana.commands.DanaRsPacketRegistry
import com.example.pumpble.dana.commands.encodeDanaDateTime
import com.example.pumpble.dana.commands.encodeDanaHistoryStart
import com.example.pumpble.dana.commands.le16
import com.example.pumpble.protocol.ByteReader
import com.example.pumpble.protocol.ByteWriter
import com.example.pumpble.protocol.ProtocolException
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

enum class ApsBasalDuration(val wireValue: Int) {
    DURATION_15_MIN(150),
    DURATION_30_MIN(160)
}

class ApsBasalSetTemporaryBasalCommand(
    val ratio: Int,
    val duration: ApsBasalDuration) :
    DanaRsAckPacketCommand(DanaRsPacketRegistry.APS_BASAL_SET_TEMPORARY_BASAL) {

    override fun encodePayload(writer: ByteWriter) {
        writer.writeBytes(le16(ratio))
        writer.writeUInt8(duration.wireValue)
    }

    companion object {
        fun create(percent: Int) = ApsBasalSetTemporaryBasalCommand(
            ratio = percent.coerceIn(0, 500),
            duration = if (percent < 100) ApsBasalDuration.DURATION_30_MIN else ApsBasalDuration.DURATION_15_MIN
        )
    }
}

class ApsHistoryEventsCommand(
    private val fromMillis: Long,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val useUtcLayout: Boolean = false,
) : DanaRsPacketCommand<ApsHistoryEventsChunk>(DanaRsPacketRegistry.APS_HISTORY_EVENTS),
    PumpStreamCommand<ApsHistoryEventsChunk, ApsHistoryEventsResult> {
    private val events = mutableListOf<ApsHistoryEvent>()

    override fun encodePayload(writer: ByteWriter) {
        writer.writeBytes(encodeDanaHistoryStart(fromMillis, zoneId))
    }

    override fun decodePayload(reader: ByteReader): ApsHistoryEventsChunk = decodeChunk(reader)

    override fun decodeChunk(reader: ByteReader): ApsHistoryEventsChunk {
        val payload = reader.readBytes(reader.remaining)
        if (payload.isEmpty()) {
            throw ProtocolException("$name returned an empty APS history chunk")
        }
        if (isEndMarker(payload)) {
            return ApsHistoryEndChunk()
        }
        val event = if (useUtcLayout) decodeUtcEvent(payload) else decodeLocalEvent(payload)
        return ApsHistoryEventChunk(status = PumpStatus.OK, event = event)
    }

    override fun onChunk(chunk: ApsHistoryEventsChunk) {
        if (chunk is ApsHistoryEventChunk) {
            events += chunk.event
        }
    }

    override fun isComplete(chunk: ApsHistoryEventsChunk): Boolean = chunk is ApsHistoryEndChunk

    override fun result(): ApsHistoryEventsResult {
        return ApsHistoryEventsResult(events = normalizeEvents(events))
    }

    private fun isEndMarker(payload: ByteArray): Boolean {
        return u8(payload, 0) == END_RECORD_CODE || (useUtcLayout && payload.size > 2 && u8(payload, 2) == END_RECORD_CODE)
    }

    private fun decodeLocalEvent(payload: ByteArray): ApsHistoryEvent {
        requirePayloadSize(payload, 11)
        val recordCode = u8(payload, 0)
        val timestamp = localTimestampMillis(payload, 1)
        val param1 = u16Be(payload, 7)
        val param2 = u16Be(payload, 9)
        return buildEvent(
            recordCode = recordCode,
            timestampMillis = timestamp,
            pumpId = timestamp,
            param1 = param1,
            param2 = param2,
            payload = payload,
        )
    }

    private fun decodeUtcEvent(payload: ByteArray): ApsHistoryEvent {
        requirePayloadSize(payload, 11)
        val pumpSequence = u16Be(payload, 0)
        val recordCode = u8(payload, 2)
        val timestamp = u32Be(payload, 3) * 1000L
        val param1 = u16Be(payload, 7)
        val param2 = u16Be(payload, 9)
        return buildEvent(
            recordCode = recordCode,
            timestampMillis = timestamp,
            pumpId = timestamp * 2 + pumpSequence,
            param1 = param1,
            param2 = param2,
            payload = payload,
        )
    }

    private fun buildEvent(
        recordCode: Int,
        timestampMillis: Long,
        pumpId: Long,
        param1: Int,
        param2: Int,
        payload: ByteArray,
    ): ApsHistoryEvent {
        val kind = ApsHistoryEventKind.fromWireValue(recordCode)
        return ApsHistoryEvent(
            kind = kind,
            recordCode = recordCode,
            timestampMillis = timestampMillis,
            pumpId = pumpId,
            param1 = param1,
            param2 = param2,
            insulinUnits = insulinUnits(kind, param1),
            durationMinutes = durationMinutes(kind, param2),
            ratioPercent = if (kind == ApsHistoryEventKind.TEMP_START) param1 else null,
            carbohydrateGrams = if (kind == ApsHistoryEventKind.CARBS) param1 else null,
            currentRateUnitsPerHour = if (kind == ApsHistoryEventKind.PROFILE_CHANGE) param2 / 100.0 else null,
            previousTimestampMillis = if (kind == ApsHistoryEventKind.TIME_CHANGE) u32Be(payload, 7) * 1000L else null,
        )
    }

    private fun insulinUnits(kind: ApsHistoryEventKind, param1: Int): Double? =
        when (kind) {
            ApsHistoryEventKind.EXTENDED_START,
            ApsHistoryEventKind.EXTENDED_STOP,
            ApsHistoryEventKind.BOLUS,
            ApsHistoryEventKind.DUAL_BOLUS,
            ApsHistoryEventKind.DUAL_EXTENDED_START,
            ApsHistoryEventKind.DUAL_EXTENDED_STOP,
            ApsHistoryEventKind.REFILL,
            ApsHistoryEventKind.PRIME,
            ApsHistoryEventKind.PRIME_CANNULA -> param1 / 100.0
            else -> null
        }

    private fun durationMinutes(kind: ApsHistoryEventKind, param2: Int): Int? =
        when (kind) {
            ApsHistoryEventKind.TEMP_START,
            ApsHistoryEventKind.EXTENDED_START,
            ApsHistoryEventKind.EXTENDED_STOP,
            ApsHistoryEventKind.DUAL_BOLUS,
            ApsHistoryEventKind.DUAL_EXTENDED_START,
            ApsHistoryEventKind.DUAL_EXTENDED_STOP -> param2
            else -> null
        }

    private fun normalizeEvents(source: List<ApsHistoryEvent>): List<ApsHistoryEvent> {
        val sorted = source.sortedBy { it.timestampMillis }
        val normalized = mutableListOf<ApsHistoryEvent>()
        for (event in sorted) {
            val previous = normalized.lastOrNull()
            if (
                event.kind == ApsHistoryEventKind.TEMP_STOP &&
                previous?.kind == ApsHistoryEventKind.TEMP_START &&
                previous.timestampMillis == event.timestampMillis
            ) {
                continue
            }
            normalized += event
        }
        return normalized
    }

    private fun localTimestampMillis(payload: ByteArray, offset: Int): Long {
        return try {
            LocalDateTime.of(
                2000 + u8(payload, offset),
                u8(payload, offset + 1),
                u8(payload, offset + 2),
                u8(payload, offset + 3),
                u8(payload, offset + 4),
                u8(payload, offset + 5),
            ).atZone(zoneId).toInstant().toEpochMilli()
        } catch (error: DateTimeException) {
            throw ProtocolException("$name contains an invalid APS history timestamp: ${error.message}")
        }
    }

    private fun requirePayloadSize(payload: ByteArray, minimumSize: Int) {
        if (payload.size < minimumSize) {
            throw ProtocolException("$name requires at least $minimumSize byte(s), only ${payload.size} available")
        }
    }

    private fun u8(payload: ByteArray, offset: Int): Int = payload[offset].toInt() and 0xff

    private fun u16Be(payload: ByteArray, offset: Int): Int {
        return (u8(payload, offset) shl 8) or u8(payload, offset + 1)
    }

    private fun u32Be(payload: ByteArray, offset: Int): Long {
        return (u8(payload, offset).toLong() shl 24) or
            (u8(payload, offset + 1).toLong() shl 16) or
            (u8(payload, offset + 2).toLong() shl 8) or
            u8(payload, offset + 3).toLong()
    }

    private companion object {
        const val END_RECORD_CODE = 0xff
    }
}

class ApsSetEventHistoryCommand(
    private val packetType: Int,
    private val timeMillis: Long,
    private val param1: Int,
    private val param2: Int,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : DanaRsAckPacketCommand(DanaRsPacketRegistry.APS_SET_EVENT_HISTORY) {
    override fun encodePayload(writer: ByteWriter) {
        val date = Instant.ofEpochMilli(timeMillis).atZone(zoneId)
        writer.writeUInt8(packetType)
        writer.writeBytes(encodeDanaDateTime(date))
        writer.writeUInt8((param1 ushr 8) and 0xff)
        writer.writeUInt8(param1 and 0xff)
        writer.writeUInt8((param2 ushr 8) and 0xff)
        writer.writeUInt8(param2 and 0xff)
    }
}