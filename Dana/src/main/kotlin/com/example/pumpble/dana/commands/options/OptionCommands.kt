package com.example.pumpble.dana.commands.options

import com.example.pumpble.dana.commands.DanaRsAckPacketCommand
import com.example.pumpble.dana.commands.DanaRsPacketCommand
import com.example.pumpble.dana.commands.DanaRsPacketRegistry
import com.example.pumpble.dana.commands.discardRemaining
import com.example.pumpble.dana.commands.encodeDanaDateTime
import com.example.pumpble.dana.commands.encodeDanaUtcDateTime
import com.example.pumpble.dana.commands.readDanaLocalDateTime
import com.example.pumpble.dana.commands.readSignedInt8
import com.example.pumpble.dana.commands.requireRemainingAtLeast
import com.example.pumpble.commands.PumpStatus
import com.example.pumpble.protocol.ByteReader
import com.example.pumpble.protocol.ByteWriter
import java.time.ZoneId

class OptionGetPumpTimeCommand :
    DanaRsPacketCommand<OptionPumpTimeResponse>(DanaRsPacketRegistry.OPTION_GET_PUMP_TIME) {
    override fun decodePayload(reader: ByteReader): OptionPumpTimeResponse {
        reader.requireRemainingAtLeast(6, name)
        val pumpTime = reader.readDanaLocalDateTime()
        reader.discardRemaining()
        return OptionPumpTimeResponse(
            status = PumpStatus.OK,
            pumpTime = pumpTime,
        )
    }
}

class OptionGetPumpUtcAndTimeZoneCommand :
    DanaRsPacketCommand<OptionPumpUtcAndTimeZoneResponse>(DanaRsPacketRegistry.OPTION_GET_PUMP_UTC_AND_TIME_ZONE) {
    override fun decodePayload(reader: ByteReader): OptionPumpUtcAndTimeZoneResponse {
        reader.requireRemainingAtLeast(7, name)
        val pumpUtcTime = reader.readDanaLocalDateTime()
        val zoneOffset = reader.readSignedInt8()
        reader.discardRemaining()
        return OptionPumpUtcAndTimeZoneResponse(
            status = PumpStatus.OK,
            pumpUtcTime = pumpUtcTime,
            zoneOffsetHours = zoneOffset,
        )
    }
}

class OptionGetUserOptionCommand :
    DanaRsPacketCommand<OptionUserOptionsResponse>(DanaRsPacketRegistry.OPTION_GET_USER_OPTION) {
    override fun decodePayload(reader: ByteReader): OptionUserOptionsResponse {
        reader.requireRemainingAtLeast(18, name)
        val timeDisplayType24 = reader.readUInt8() == 0
        val buttonScrollOnOff = reader.readUInt8() == 1
        val beepAndAlarm = reader.readUInt8()
        val lcdOnTimeSec = reader.readUInt8()
        val backlightOnTimeSec = reader.readUInt8()
        val selectedLanguage = reader.readUInt8()
        val units = reader.readUInt8()
        val shutdownHour = reader.readUInt8()
        val lowReservoirRate = reader.readUInt8()
        val cannulaVolume = reader.readUInt16Le()
        val refillAmount = reader.readUInt16Le()
        val selectableLanguages = List(5) { reader.readUInt8() }
        val target = if (reader.remaining >= 2) reader.readUInt16Le() else null
        reader.discardRemaining()
        return OptionUserOptionsResponse(
            status = if (lcdOnTimeSec >= 5) PumpStatus.OK else PumpStatus.INVALID_PARAMETER,
            timeDisplayType24 = timeDisplayType24,
            buttonScrollOnOff = buttonScrollOnOff,
            beepAndAlarm = beepAndAlarm,
            lcdOnTimeSec = lcdOnTimeSec,
            backlightOnTimeSec = backlightOnTimeSec,
            selectedLanguage = selectedLanguage,
            units = units,
            shutdownHour = shutdownHour,
            lowReservoirRate = lowReservoirRate,
            cannulaVolume = cannulaVolume,
            refillAmount = refillAmount,
            selectableLanguages = selectableLanguages,
            target = target,
        )
    }
}

class OptionSetPumpTimeCommand(
    private val timeMillis: Long,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : DanaRsAckPacketCommand(DanaRsPacketRegistry.OPTION_SET_PUMP_TIME) {
    override fun encodePayload(writer: ByteWriter) {
        writer.writeBytes(encodeDanaDateTime(timeMillis, zoneId))
    }
}

class OptionSetPumpUtcAndTimeZoneCommand(
    private val timeMillis: Long,
    private val zoneOffset: Int,
) : DanaRsAckPacketCommand(DanaRsPacketRegistry.OPTION_SET_PUMP_UTC_AND_TIME_ZONE) {
    override fun encodePayload(writer: ByteWriter) {
        writer.writeBytes(encodeDanaUtcDateTime(timeMillis))
        writer.writeBytes(byteArrayOf(zoneOffset.toByte()))
    }
}

class OptionSetUserOptionCommand(
    private val options: DanaRsUserOptions,
) : DanaRsAckPacketCommand(DanaRsPacketRegistry.OPTION_SET_USER_OPTION) {
    override fun encodePayload(writer: ByteWriter) {
        writer.writeUInt8(if (options.timeDisplayType24) 0 else 1)
        writer.writeUInt8(if (options.buttonScrollOnOff) 1 else 0)
        writer.writeUInt8(options.beepAndAlarm)
        writer.writeUInt8(options.lcdOnTimeSec)
        writer.writeUInt8(options.backlightOnTimeSec)
        writer.writeUInt8(options.selectedLanguage)
        writer.writeUInt8(options.units)
        writer.writeUInt8(options.shutdownHour)
        writer.writeUInt8(options.lowReservoirRate)
        writer.writeUInt8(options.cannulaVolume and 0xff)
        writer.writeUInt8((options.cannulaVolume ushr 8) and 0xff)
        writer.writeUInt8(options.refillAmount and 0xff)
        writer.writeUInt8((options.refillAmount ushr 8) and 0xff)
        if (options.hwModel >= 7) {
            writer.writeUInt8(options.target and 0xff)
            writer.writeUInt8((options.target ushr 8) and 0xff)
        }
    }
}
