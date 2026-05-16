package com.example.pumpble.dana.commands.bolus

import com.example.pumpble.commands.PumpStatus
import com.example.pumpble.dana.commands.DANA_UNITS_MGDL
import com.example.pumpble.dana.commands.DANA_UNITS_MMOL
import com.example.pumpble.dana.commands.DanaRsAckPacketCommand
import com.example.pumpble.dana.commands.DanaRsBolusSpeed
import com.example.pumpble.dana.commands.DanaRsPacketCommand
import com.example.pumpble.dana.commands.DanaRsPacketRegistry
import com.example.pumpble.dana.commands.discardRemaining
import com.example.pumpble.dana.commands.le16
import com.example.pumpble.dana.commands.requireRemainingAtLeast
import com.example.pumpble.protocol.ByteReader
import com.example.pumpble.protocol.ByteWriter
import java.time.LocalTime
import kotlin.math.roundToInt

class BolusGet24CIRCFArrayCommand :
    DanaRsPacketCommand<Bolus24CirCfArrayResponse>(DanaRsPacketRegistry.BOLUS_GET_24_CIR_CF_ARRAY) {
    override fun decodePayload(reader: ByteReader): Bolus24CirCfArrayResponse {
        reader.requireRemainingAtLeast(97, name)
        val unitsRaw = reader.readUInt8()
        val units = DanaGlucoseUnits.fromWireValue(unitsRaw)
        val cirValues = List(24) { reader.readUInt16Le().toDouble() }
        val cfValues = List(24) {
            val raw = reader.readUInt16Le()
            if (unitsRaw == DANA_UNITS_MGDL) raw.toDouble() else raw / 100.0
        }
        reader.discardRemaining()
        return Bolus24CirCfArrayResponse(
            status = if (units == DanaGlucoseUnits.UNKNOWN) PumpStatus.INVALID_PARAMETER else PumpStatus.OK,
            units = units,
            valuesByHour = cirValues.zip(cfValues) { cir, cf -> CirCfValue(cir, cf) },
        )
    }
}

class BolusGetBolusOptionCommand :
    DanaRsPacketCommand<BolusOptionResponse>(DanaRsPacketRegistry.BOLUS_GET_BOLUS_OPTION) {
    override fun decodePayload(reader: ByteReader): BolusOptionResponse {
        reader.requireRemainingAtLeast(19, name)
        val extendedBolusEnabled = reader.readUInt8() == 1
        val bolusCalculationOption = reader.readUInt8()
        val missedBolusConfig = reader.readUInt8()
        val windows = List(4) {
            MissedBolusWindow(
                startHour = reader.readUInt8(),
                startMinute = reader.readUInt8(),
                endHour = reader.readUInt8(),
                endMinute = reader.readUInt8(),
            )
        }
        reader.discardRemaining()
        return BolusOptionResponse(
            status = if (extendedBolusEnabled) PumpStatus.OK else PumpStatus.REJECTED,
            extendedBolusEnabled = extendedBolusEnabled,
            bolusCalculationOption = bolusCalculationOption,
            missedBolusConfig = missedBolusConfig,
            missedBolusWindows = windows,
        )
    }
}

class BolusGetCalculationInformationCommand :
    DanaRsPacketCommand<BolusCalculationInformationResponse>(DanaRsPacketRegistry.BOLUS_GET_CALCULATION_INFORMATION) {
    override fun decodePayload(reader: ByteReader): BolusCalculationInformationResponse {
        reader.requireRemainingAtLeast(14, name)
        val errorCode = reader.readUInt8()
        var currentBloodGlucose = reader.readUInt16Le().toDouble()
        val carbohydrate = reader.readUInt16Le()
        var currentTarget = reader.readUInt16Le().toDouble()
        val currentCarbRatio = reader.readUInt16Le()
        var currentCorrectionFactor = reader.readUInt16Le().toDouble()
        val insulinOnBoard = reader.readUInt16Le() / 100.0
        val unitsRaw = reader.readUInt8()
        if (unitsRaw == DANA_UNITS_MMOL) {
            currentBloodGlucose /= 100.0
            currentTarget /= 100.0
            currentCorrectionFactor /= 100.0
        }
        reader.discardRemaining()
        return BolusCalculationInformationResponse(
            status = PumpStatus.fromCode(errorCode),
            errorCode = errorCode,
            units = DanaGlucoseUnits.fromWireValue(unitsRaw),
            currentBloodGlucose = currentBloodGlucose,
            carbohydrateGrams = carbohydrate,
            currentTarget = currentTarget,
            currentCarbRatio = currentCarbRatio,
            currentCorrectionFactor = currentCorrectionFactor,
            insulinOnBoardUnits = insulinOnBoard,
        )
    }
}

class BolusGetCIRCFArrayCommand :
    DanaRsPacketCommand<BolusCirCfArrayResponse>(DanaRsPacketRegistry.BOLUS_GET_CIR_CF_ARRAY) {
    override fun decodePayload(reader: ByteReader): BolusCirCfArrayResponse {
        reader.requireRemainingAtLeast(30, name)
        val language = reader.readUInt8()
        val unitsRaw = reader.readUInt8()
        val cirValues = List(7) { reader.readUInt16Le() }
        val cfValues = List(7) {
            val raw = reader.readUInt16Le()
            if (unitsRaw == DANA_UNITS_MGDL) raw.toDouble() else raw / 100.0
        }
        val units = DanaGlucoseUnits.fromWireValue(unitsRaw)
        reader.discardRemaining()
        return BolusCirCfArrayResponse(
            status = if (units == DanaGlucoseUnits.UNKNOWN) PumpStatus.INVALID_PARAMETER else PumpStatus.OK,
            language = language,
            units = units,
            cirValues = cirValues,
            cfValues = cfValues,
        )
    }
}

class BolusGetStepBolusInformationCommand :
    DanaRsPacketCommand<BolusStepBolusInformationResponse>(DanaRsPacketRegistry.BOLUS_GET_STEP_BOLUS_INFORMATION) {
    override fun decodePayload(reader: ByteReader): BolusStepBolusInformationResponse {
        reader.requireRemainingAtLeast(11, name)
        val errorCode = reader.readUInt8()
        val bolusType = reader.readUInt8()
        val initialBolusAmount = reader.readUInt16Le() / 100.0
        val hour = reader.readUInt8()
        val minute = reader.readUInt8()
        val lastBolusAmount = reader.readUInt16Le() / 100.0
        val maxBolus = reader.readUInt16Le() / 100.0
        val bolusStep = reader.readUInt8() / 100.0
        reader.discardRemaining()
        return BolusStepBolusInformationResponse(
            status = PumpStatus.fromCode(errorCode),
            errorCode = errorCode,
            bolusType = bolusType,
            initialBolusAmountUnits = initialBolusAmount,
            lastBolusTimeOfDayUTC = LocalTime.of(hour, minute),
            lastBolusAmountUnits = lastBolusAmount,
            maxBolusUnits = maxBolus,
            bolusStepUnits = bolusStep,
        )
    }
}

class BolusSetStepBolusStartCommand(
    private val amountUnits: Double,
    private val speed: DanaRsBolusSpeed,
) : DanaRsAckPacketCommand(DanaRsPacketRegistry.BOLUS_SET_STEP_BOLUS_START) {
    override fun encodePayload(writer: ByteWriter) {
        writer.writeBytes(le16((amountUnits * 100.0).roundToInt()))
        writer.writeUInt8(speed.wireValue)
    }
}

class BolusSetStepBolusStopCommand : DanaRsAckPacketCommand(DanaRsPacketRegistry.BOLUS_SET_STEP_BOLUS_STOP)

class BolusSetExtendedBolusCommand(
    private val amountUnits: Double,
    private val durationHalfHours: Int,
) : DanaRsAckPacketCommand(DanaRsPacketRegistry.BOLUS_SET_EXTENDED_BOLUS) {
    override fun encodePayload(writer: ByteWriter) {
        writer.writeBytes(le16((amountUnits * 100.0).roundToInt()))
        writer.writeUInt8(durationHalfHours)
    }
}

class BolusSetExtendedBolusCancelCommand : DanaRsAckPacketCommand(DanaRsPacketRegistry.BOLUS_SET_EXTENDED_BOLUS_CANCEL)

class BolusSet24CIRCFArrayCommand(
    private val ic: IntArray,
    private val cf: IntArray,
) : DanaRsAckPacketCommand(DanaRsPacketRegistry.BOLUS_SET_24_CIR_CF_ARRAY) {
    init {
        require(ic.size == 24 && cf.size == 24) { "Dana CIR/CF array requires 24 IC and 24 CF values" }
    }

    override fun encodePayload(writer: ByteWriter) {
        ic.forEach { writer.writeBytes(le16(it)) }
        cf.forEach { writer.writeBytes(le16(it)) }
    }
}

class BolusSetBolusOptionCommand(
    private val extendedBolusEnabled: Boolean,
    private val bolusCalculationOption: Int,
    private val missedBolusConfig: Int,
    private val missedBolusWindows: List<MissedBolusWindow>,
) : DanaRsAckPacketCommand(DanaRsPacketRegistry.BOLUS_SET_BOLUS_OPTION) {
    init {
        require(missedBolusWindows.size == 4) { "Dana bolus options require four missed-bolus windows" }
    }

    override fun encodePayload(writer: ByteWriter) {
        writer.writeUInt8(if (extendedBolusEnabled) 1 else 0)
        writer.writeUInt8(bolusCalculationOption)
        writer.writeUInt8(missedBolusConfig)
        missedBolusWindows.forEach { window ->
            writer.writeUInt8(window.startHour)
            writer.writeUInt8(window.startMinute)
            writer.writeUInt8(window.endHour)
            writer.writeUInt8(window.endMinute)
        }
    }
}