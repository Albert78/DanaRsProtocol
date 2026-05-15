package com.example.pumpble.dana.commands.aps

import com.example.pumpble.dana.commands.DanaRsAckPacketCommand
import com.example.pumpble.dana.commands.DanaRsPacketRegistry
import com.example.pumpble.dana.commands.DanaRsRawPacketCommand
import com.example.pumpble.dana.commands.encodeDanaDateTime
import com.example.pumpble.dana.commands.encodeDanaHistoryStart
import com.example.pumpble.dana.commands.le16
import com.example.pumpble.protocol.ByteWriter
import java.time.Instant
import java.time.ZoneId

class ApsBasalSetTemporaryBasalCommand(percent: Int) :
    DanaRsAckPacketCommand(DanaRsPacketRegistry.APS_BASAL_SET_TEMPORARY_BASAL) {
    private val ratio = percent.coerceIn(0, 500)
    private val durationParam = if (percent < 100) PARAM_30_MIN else PARAM_15_MIN

    override fun encodePayload(writer: ByteWriter) {
        writer.writeBytes(le16(ratio))
        writer.writeUInt8(durationParam)
    }

    private companion object {
        const val PARAM_15_MIN = 150
        const val PARAM_30_MIN = 160
    }
}

class ApsHistoryEventsCommand(
    private val fromMillis: Long,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : DanaRsRawPacketCommand(DanaRsPacketRegistry.APS_HISTORY_EVENTS) {
    override fun encodePayload(writer: ByteWriter) {
        writer.writeBytes(encodeDanaHistoryStart(fromMillis, zoneId))
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
