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

class GeneralGetShippingVersionCommand :
    DanaRsPacketCommand<GeneralShippingVersionResponse>(DanaRsPacketRegistry.GENERAL_GET_SHIPPING_VERSION) {
    override fun decodePayload(reader: ByteReader): GeneralShippingVersionResponse {
        val bleModel = reader.readAscii(reader.remaining)
        return GeneralShippingVersionResponse(
            status = PumpStatus.OK,
            bleModel = bleModel,
        )
    }
}

class GeneralGetUserTimeChangeFlagCommand :
    DanaRsPacketCommand<GeneralUserTimeChangeFlagResponse>(DanaRsPacketRegistry.GENERAL_GET_USER_TIME_CHANGE_FLAG) {
    override fun decodePayload(reader: ByteReader): GeneralUserTimeChangeFlagResponse {
        reader.requireRemainingAtLeast(1, name)
        val flag = reader.readUInt8()
        reader.discardRemaining()
        return GeneralUserTimeChangeFlagResponse(
            status = PumpStatus.OK,
            flag = flag,
            changedByUser = flag != 0,
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

class GeneralSetUserTimeChangeFlagClearCommand :
    DanaRsAckPacketCommand(DanaRsPacketRegistry.GENERAL_SET_USER_TIME_CHANGE_FLAG_CLEAR)

class ReviewBolusAverageCommand :
    DanaRsPacketCommand<ReviewBolusAverageResponse>(DanaRsPacketRegistry.REVIEW_BOLUS_AVG) {
    override fun decodePayload(reader: ByteReader): ReviewBolusAverageResponse {
        reader.requireRemainingAtLeast(10, name)
        val averages = List(5) { reader.readUInt16Le() / 100.0 }
        reader.discardRemaining()
        val looksLikeEmptyPlaceholder = averages.all { it == EMPTY_PLACEHOLDER_UNITS }
        return ReviewBolusAverageResponse(
            status = if (looksLikeEmptyPlaceholder) PumpStatus.INVALID_PARAMETER else PumpStatus.OK,
            threeDayAverageUnits = averages[0],
            sevenDayAverageUnits = averages[1],
            fourteenDayAverageUnits = averages[2],
            twentyOneDayAverageUnits = averages[3],
            twentyEightDayAverageUnits = averages[4],
        )
    }

    private companion object {
        const val EMPTY_PLACEHOLDER_UNITS = 2.57
    }
}

class ReviewGetPumpDecRatioCommand :
    DanaRsPacketCommand<ReviewPumpDecRatioResponse>(DanaRsPacketRegistry.REVIEW_GET_PUMP_DEC_RATIO) {
    override fun decodePayload(reader: ByteReader): ReviewPumpDecRatioResponse {
        reader.requireRemainingAtLeast(1, name)
        val ratioPercent = reader.readUInt8() * 5
        reader.discardRemaining()
        return ReviewPumpDecRatioResponse(
            status = PumpStatus.OK,
            ratioPercent = ratioPercent,
        )
    }
}
