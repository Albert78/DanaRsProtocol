package com.example.pumpble.dana.commands.history

import com.example.pumpble.dana.commands.DanaRsPacketDefinition
import com.example.pumpble.dana.commands.DanaRsPacketCommand
import com.example.pumpble.dana.commands.DanaRsPacketRegistry
import com.example.pumpble.dana.commands.discardRemaining
import com.example.pumpble.dana.commands.encodeDanaHistoryStart
import com.example.pumpble.dana.commands.requireRemainingAtLeast
import com.example.pumpble.dana.commands.readUInt16Be
import com.example.pumpble.commands.PumpStatus
import com.example.pumpble.protocol.ByteReader
import com.example.pumpble.protocol.ProtocolException
import com.example.pumpble.protocol.ByteWriter
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.ZoneId

abstract class DanaRsHistoryCommand(
    definition: DanaRsPacketDefinition,
    private val fromMillis: Long,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : DanaRsPacketCommand<DanaRsHistoryResponse>(definition) {
    override fun encodePayload(writer: ByteWriter) {
        writer.writeBytes(encodeDanaHistoryStart(fromMillis, zoneId))
    }

    override fun decodePayload(reader: ByteReader): DanaRsHistoryResponse {
        return when (reader.remaining) {
            1 -> decodeEnd(reader)
            3 -> decodeEndWithCount(reader)
            else -> decodeRecord(reader)
        }
    }

    private fun decodeEnd(reader: ByteReader): HistoryEndResponse {
        val errorCode = reader.readUInt8()
        reader.discardRemaining()
        return HistoryEndResponse(
            status = PumpStatus.fromCode(errorCode),
            errorCode = errorCode,
            totalCount = null,
        )
    }

    private fun decodeEndWithCount(reader: ByteReader): HistoryEndResponse {
        val errorCode = reader.readUInt8()
        val totalCount = reader.readUInt16Le()
        reader.discardRemaining()
        return HistoryEndResponse(
            status = PumpStatus.fromCode(errorCode),
            errorCode = errorCode,
            totalCount = totalCount,
        )
    }

    private fun decodeRecord(reader: ByteReader): HistoryRecordResponse {
        reader.requireRemainingAtLeast(10, name)
        val recordCode = reader.readUInt8()
        val year = reader.readUInt8()
        val month = reader.readUInt8()
        val day = reader.readUInt8()
        val hourOrDailyBasalHi = reader.readUInt8()
        val minuteOrDailyBasalLo = reader.readUInt8()
        val secondOrDailyBolusHi = reader.readUInt8()
        val historyCodeOrDailyBolusLo = reader.readUInt8()
        val rawValue = reader.readUInt16Be()
        reader.discardRemaining()

        val kind = DanaRsHistoryRecordKind.fromWireValue(recordCode)
        val dailyBasal = ((hourOrDailyBasalHi shl 8) or minuteOrDailyBasalLo) * 0.01
        val dailyBolus = ((secondOrDailyBolusHi shl 8) or historyCodeOrDailyBolusLo) * 0.01
        val timestamp = historyTimestamp(
            kind = kind,
            year = year,
            month = month,
            day = day,
            hour = hourOrDailyBasalHi,
            minute = minuteOrDailyBasalLo,
            second = secondOrDailyBolusHi,
        )
        val durationMinutes = if (kind == DanaRsHistoryRecordKind.BOLUS) {
            (historyCodeOrDailyBolusLo and 0x0f) * 60 + secondOrDailyBolusHi
        } else {
            null
        }
        val bolusType = if (kind == DanaRsHistoryRecordKind.BOLUS) {
            when (historyCodeOrDailyBolusLo and 0xf0) {
                0xa0 -> "DS"
                0xc0 -> "E"
                0x80 -> "S"
                0x90 -> "DE"
                else -> "None"
            }
        } else {
            null
        }
        val alarm = if (kind == DanaRsHistoryRecordKind.ALARM) alarmName(historyCodeOrDailyBolusLo) else null
        val stringValue = if (kind == DanaRsHistoryRecordKind.SUSPEND) {
            if (historyCodeOrDailyBolusLo == 'O'.code) "On" else "Off"
        } else {
            null
        }
        val value = when (kind) {
            DanaRsHistoryRecordKind.DAILY -> null
            DanaRsHistoryRecordKind.GLUCOSE,
            DanaRsHistoryRecordKind.CARBOHYDRATE -> rawValue.toDouble()
            DanaRsHistoryRecordKind.SUSPEND -> null
            else -> rawValue * 0.01
        }
        return HistoryRecordResponse(
            status = PumpStatus.OK,
            record = DanaRsHistoryRecord(
                recordCode = recordCode,
                kind = kind,
                timestamp = timestamp,
                messageType = messageType(kind),
                value = value,
                durationMinutes = durationMinutes,
                bolusType = bolusType,
                alarm = alarm,
                stringValue = stringValue,
                dailyBasalUnits = if (kind == DanaRsHistoryRecordKind.DAILY) dailyBasal else null,
                dailyBolusUnits = if (kind == DanaRsHistoryRecordKind.DAILY) dailyBolus else null,
                rawHistoryCode = historyCodeOrDailyBolusLo,
                rawValue = rawValue,
            ),
        )
    }

    private fun historyTimestamp(
        kind: DanaRsHistoryRecordKind,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
    ): LocalDateTime {
        return try {
            when (kind) {
                DanaRsHistoryRecordKind.DAILY -> LocalDateTime.of(2000 + year, month, day, 0, 0)
                DanaRsHistoryRecordKind.BOLUS -> LocalDateTime.of(2000 + year, month, day, hour, minute)
                else -> LocalDateTime.of(2000 + year, month, day, hour, minute, second)
            }
        } catch (error: DateTimeException) {
            throw ProtocolException("$name contains an invalid history timestamp: ${error.message}")
        }
    }

    private fun messageType(kind: DanaRsHistoryRecordKind): String? =
        when (kind) {
            DanaRsHistoryRecordKind.BOLUS -> "bolus"
            DanaRsHistoryRecordKind.DAILY -> "dailyinsulin"
            DanaRsHistoryRecordKind.PRIME -> "prime"
            DanaRsHistoryRecordKind.REFILL -> "refill"
            DanaRsHistoryRecordKind.BASAL_HOUR -> "basal hour"
            DanaRsHistoryRecordKind.TEMP_BASAL -> "tb"
            DanaRsHistoryRecordKind.GLUCOSE -> "glucose"
            DanaRsHistoryRecordKind.CARBOHYDRATE -> "carbo"
            DanaRsHistoryRecordKind.ALARM -> "alarm"
            DanaRsHistoryRecordKind.SUSPEND -> "suspend"
            DanaRsHistoryRecordKind.UNKNOWN -> null
        }

    private fun alarmName(code: Int): String =
        when (code) {
            'P'.code -> "Basal Compare"
            'R'.code -> "Empty Reservoir"
            'C'.code -> "Check"
            'O'.code -> "Occlusion"
            'M'.code -> "Basal max"
            'D'.code -> "Daily max"
            'B'.code -> "Low Battery"
            'S'.code -> "Shutdown"
            else -> "None"
        }
}

class HistoryAlarmCommand(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) :
    DanaRsHistoryCommand(DanaRsPacketRegistry.HISTORY_ALARM, fromMillis, zoneId)

class HistoryBasalCommand(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) :
    DanaRsHistoryCommand(DanaRsPacketRegistry.HISTORY_BASAL, fromMillis, zoneId)

class HistoryBloodGlucoseCommand(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) :
    DanaRsHistoryCommand(DanaRsPacketRegistry.HISTORY_BLOOD_GLUCOSE, fromMillis, zoneId)

class HistoryBolusCommand(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) :
    DanaRsHistoryCommand(DanaRsPacketRegistry.HISTORY_BOLUS, fromMillis, zoneId)

class HistoryCarbohydrateCommand(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) :
    DanaRsHistoryCommand(DanaRsPacketRegistry.HISTORY_CARBOHYDRATE, fromMillis, zoneId)

class HistoryDailyCommand(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) :
    DanaRsHistoryCommand(DanaRsPacketRegistry.HISTORY_DAILY, fromMillis, zoneId)

class HistoryPrimeCommand(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) :
    DanaRsHistoryCommand(DanaRsPacketRegistry.HISTORY_PRIME, fromMillis, zoneId)

class HistoryRefillCommand(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) :
    DanaRsHistoryCommand(DanaRsPacketRegistry.HISTORY_REFILL, fromMillis, zoneId)

class HistorySuspendCommand(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) :
    DanaRsHistoryCommand(DanaRsPacketRegistry.HISTORY_SUSPEND, fromMillis, zoneId)
