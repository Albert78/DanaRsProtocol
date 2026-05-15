package com.example.pumpble.dana.commands.general

import com.example.pumpble.dana.commands.DanaRsAckPacketCommand
import com.example.pumpble.dana.commands.DanaRsPacketRegistry
import com.example.pumpble.dana.commands.DanaRsPacketCommand
import com.example.pumpble.dana.commands.discardRemaining
import com.example.pumpble.dana.commands.readAscii
import com.example.pumpble.dana.commands.readDanaLocalDate
import com.example.pumpble.dana.commands.requireRemainingAtLeast
import com.example.pumpble.commands.PumpStatus
import com.example.pumpble.protocol.ByteReader
import com.example.pumpble.protocol.ByteWriter

class GeneralGetPumpCheckCommand :
    DanaRsPacketCommand<GeneralPumpCheckResponse>(DanaRsPacketRegistry.GENERAL_GET_PUMP_CHECK) {
    override fun decodePayload(reader: ByteReader): GeneralPumpCheckResponse {
        reader.requireRemainingAtLeast(3, name)
        val hardwareModel = reader.readUInt8()
        val protocol = reader.readUInt8()
        val productCode = reader.readUInt8()
        reader.discardRemaining()
        return GeneralPumpCheckResponse(
            status = PumpStatus.OK,
            hardwareModel = hardwareModel,
            protocol = protocol,
            productCode = productCode,
            supportedFirmware = productCode >= 2,
        )
    }
}

class GeneralGetShippingInformationCommand :
    DanaRsPacketCommand<GeneralShippingInformationResponse>(DanaRsPacketRegistry.GENERAL_GET_SHIPPING_INFORMATION) {
    override fun decodePayload(reader: ByteReader): GeneralShippingInformationResponse {
        reader.requireRemainingAtLeast(16, name)
        val serialNumber = reader.readAscii(10)
        val shippingCountry = reader.readAscii(3)
        val shippingDate = reader.readDanaLocalDate()
        reader.discardRemaining()
        return GeneralShippingInformationResponse(
            status = PumpStatus.OK,
            serialNumber = serialNumber,
            shippingCountry = shippingCountry,
            shippingDate = shippingDate,
        )
    }
}

class GeneralInitialScreenInformationCommand :
    DanaRsPacketCommand<GeneralInitialScreenInformationResponse>(DanaRsPacketRegistry.GENERAL_INITIAL_SCREEN_INFORMATION) {
    override fun decodePayload(reader: ByteReader): GeneralInitialScreenInformationResponse {
        reader.requireRemainingAtLeast(15, name)
        val statusByte = reader.readUInt8()
        val dailyTotalUnits = reader.readUInt16Le() / 100.0
        val maxDailyTotalUnits = reader.readUInt16Le() / 100
        val reservoirRemainingUnits = reader.readUInt16Le() / 100.0
        val currentBasal = reader.readUInt16Le() / 100.0
        val tempBasalPercent = reader.readUInt8()
        val batteryRemaining = reader.readUInt8()
        val extendedBolusAbsoluteRate = reader.readUInt16Le() / 100.0
        val insulinOnBoard = reader.readUInt16Le() / 100.0
        val errorState = if (reader.remaining >= 1) {
            DanaRsPumpErrorState.fromCode(reader.readUInt8())
        } else {
            DanaRsPumpErrorState.NONE
        }
        reader.discardRemaining()
        return GeneralInitialScreenInformationResponse(
            status = PumpStatus.OK,
            pumpSuspended = statusByte and 0x01 == 0x01,
            tempBasalInProgress = statusByte and 0x10 == 0x10,
            extendedBolusInProgress = statusByte and 0x04 == 0x04,
            dualBolusInProgress = statusByte and 0x08 == 0x08,
            dailyTotalUnits = dailyTotalUnits,
            maxDailyTotalUnits = maxDailyTotalUnits,
            reservoirRemainingUnits = reservoirRemainingUnits,
            currentBasalUnitsPerHour = currentBasal,
            tempBasalPercent = tempBasalPercent,
            batteryRemainingPercent = batteryRemaining,
            extendedBolusAbsoluteRate = extendedBolusAbsoluteRate,
            insulinOnBoardUnits = insulinOnBoard,
            errorState = errorState,
        )
    }
}

class GeneralSetHistoryUploadModeCommand(
    private val mode: Int,
) : DanaRsAckPacketCommand(DanaRsPacketRegistry.GENERAL_SET_HISTORY_UPLOAD_MODE) {
    override fun encodePayload(writer: ByteWriter) {
        writer.writeUInt8(mode)
    }
}
