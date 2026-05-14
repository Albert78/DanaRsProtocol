package com.example.pumpble.dana.commands

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/**
 * Factory for sendable DanaRS/Dana-i commands.
 *
 * The registry contains the packet identity; this class owns request parameter encoding for the
 * packets that require a non-empty request payload. Parameterless packets are exposed as convenience
 * methods and can also be created through [command].
 */
class DanaRsCommands {
    val definitions: List<DanaRsPacketDefinition> = DanaRsPacketRegistry.all

    fun command(definition: DanaRsPacketDefinition): DanaRsCommand = DanaRsCommand(definition)
    fun raw(definition: DanaRsPacketDefinition, payload: ByteArray): DanaRsCommand = DanaRsCommand(definition, payload)

    fun basalGetBasalRate(): DanaRsCommand = command(DanaRsPacketRegistry.BASAL_GET_BASAL_RATE)
    fun basalGetProfileNumber(): DanaRsCommand = command(DanaRsPacketRegistry.BASAL_GET_PROFILE_NUMBER)
    fun basalSetCancelTemporaryBasal(): DanaRsCommand = command(DanaRsPacketRegistry.BASAL_SET_CANCEL_TEMPORARY_BASAL)

    fun apsBasalSetTemporaryBasal(percent: Int): DanaRsCommand {
        val ratio = percent.coerceIn(0, 500)
        val durationParam = if (percent < 100) APS_TEMP_BASAL_30_MIN else APS_TEMP_BASAL_15_MIN
        return raw(
            DanaRsPacketRegistry.APS_BASAL_SET_TEMPORARY_BASAL,
            byteArrayOf((ratio and 0xff).toByte(), (ratio ushr 8 and 0xff).toByte(), durationParam.toByte()),
        )
    }

    fun basalSetTemporaryBasal(ratioPercent: Int, durationHours: Int): DanaRsCommand {
        return raw(
            DanaRsPacketRegistry.BASAL_SET_TEMPORARY_BASAL,
            byteArrayOf((ratioPercent and 0xff).toByte(), (durationHours and 0xff).toByte()),
        )
    }

    fun bolusGet24CIRCFArray(): DanaRsCommand = command(DanaRsPacketRegistry.BOLUS_GET_24_CIR_CF_ARRAY)
    fun bolusGetBolusOption(): DanaRsCommand = command(DanaRsPacketRegistry.BOLUS_GET_BOLUS_OPTION)
    fun bolusGetCalculationInformation(): DanaRsCommand = command(DanaRsPacketRegistry.BOLUS_GET_CALCULATION_INFORMATION)
    fun bolusGetCIRCFArray(): DanaRsCommand = command(DanaRsPacketRegistry.BOLUS_GET_CIR_CF_ARRAY)
    fun bolusGetStepBolusInformation(): DanaRsCommand = command(DanaRsPacketRegistry.BOLUS_GET_STEP_BOLUS_INFORMATION)

    fun basalSetProfileNumber(profileNumber: Int): DanaRsCommand {
        return raw(DanaRsPacketRegistry.BASAL_SET_PROFILE_NUMBER, byteArrayOf((profileNumber and 0xff).toByte()))
    }

    fun basalSetProfileBasalRate(profileNumber: Int, hourlyRatesUnits: List<Double>): DanaRsCommand {
        require(hourlyRatesUnits.size == 24) { "Dana basal profile requires 24 hourly rates" }
        val request = ByteArray(49)
        request[0] = (profileNumber and 0xff).toByte()
        hourlyRatesUnits.forEachIndexed { index, rate ->
            val centiUnits = (rate * 100.0).roundToInt()
            request[1 + index * 2] = (centiUnits and 0xff).toByte()
            request[2 + index * 2] = (centiUnits ushr 8 and 0xff).toByte()
        }
        return raw(DanaRsPacketRegistry.BASAL_SET_PROFILE_BASAL_RATE, request)
    }

    fun bolusSetStepBolusStart(amountUnits: Double, speed: DanaRsBolusSpeed): DanaRsCommand {
        val centiUnits = (amountUnits * 100.0).roundToInt()
        return raw(
            DanaRsPacketRegistry.BOLUS_SET_STEP_BOLUS_START,
            byteArrayOf((centiUnits and 0xff).toByte(), (centiUnits ushr 8 and 0xff).toByte(), speed.wireValue.toByte()),
        )
    }

    fun bolusSetExtendedBolus(amountUnits: Double, durationHalfHours: Int): DanaRsCommand {
        val centiUnits = (amountUnits * 100.0).roundToInt()
        return raw(
            DanaRsPacketRegistry.BOLUS_SET_EXTENDED_BOLUS,
            byteArrayOf((centiUnits and 0xff).toByte(), (centiUnits ushr 8 and 0xff).toByte(), (durationHalfHours and 0xff).toByte()),
        )
    }

    fun bolusSetExtendedBolusCancel(): DanaRsCommand = command(DanaRsPacketRegistry.BOLUS_SET_EXTENDED_BOLUS_CANCEL)
    fun bolusSetStepBolusStop(): DanaRsCommand = command(DanaRsPacketRegistry.BOLUS_SET_STEP_BOLUS_STOP)

    fun bolusSet24CIRCFArray(ic: IntArray, cf: IntArray): DanaRsCommand {
        require(ic.size == 24 && cf.size == 24) { "Dana CIR/CF array requires 24 IC and 24 CF values" }
        val request = ByteArray(96)
        val cfStart = 24 * 2
        for (i in 0..23) {
            request[2 * i] = (ic[i] and 0xff).toByte()
            request[2 * i + 1] = (ic[i] ushr 8 and 0xff).toByte()
            request[cfStart + 2 * i] = (cf[i] and 0xff).toByte()
            request[cfStart + 2 * i + 1] = (cf[i] ushr 8 and 0xff).toByte()
        }
        return raw(DanaRsPacketRegistry.BOLUS_SET_24_CIR_CF_ARRAY, request)
    }

    fun etcKeepConnection(): DanaRsCommand = command(DanaRsPacketRegistry.ETC_KEEP_CONNECTION)
    fun generalGetPumpCheck(): DanaRsCommand = command(DanaRsPacketRegistry.GENERAL_GET_PUMP_CHECK)
    fun generalGetShippingInformation(): DanaRsCommand = command(DanaRsPacketRegistry.GENERAL_GET_SHIPPING_INFORMATION)
    fun generalInitialScreenInformation(): DanaRsCommand = command(DanaRsPacketRegistry.GENERAL_INITIAL_SCREEN_INFORMATION)

    fun generalSetHistoryUploadMode(mode: Int): DanaRsCommand {
        return raw(DanaRsPacketRegistry.GENERAL_SET_HISTORY_UPLOAD_MODE, byteArrayOf((mode and 0xff).toByte()))
    }

    fun apsHistoryEvents(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): DanaRsCommand {
        val request = if (fromMillis == 0L) {
            byteArrayOf(0, 1, 1, 0, 0, 0)
        } else {
            encodeDateTime(Instant.ofEpochMilli(fromMillis).atZone(zoneId))
        }
        return raw(DanaRsPacketRegistry.APS_HISTORY_EVENTS, request)
    }

    fun historyAlarm(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): DanaRsCommand {
        return history(DanaRsPacketRegistry.HISTORY_ALARM, fromMillis, zoneId)
    }

    fun historyBasal(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): DanaRsCommand {
        return history(DanaRsPacketRegistry.HISTORY_BASAL, fromMillis, zoneId)
    }

    fun historyBloodGlucose(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): DanaRsCommand {
        return history(DanaRsPacketRegistry.HISTORY_BLOOD_GLUCOSE, fromMillis, zoneId)
    }

    fun historyBolus(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): DanaRsCommand {
        return history(DanaRsPacketRegistry.HISTORY_BOLUS, fromMillis, zoneId)
    }

    fun historyCarbohydrate(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): DanaRsCommand {
        return history(DanaRsPacketRegistry.HISTORY_CARBOHYDRATE, fromMillis, zoneId)
    }

    fun historyDaily(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): DanaRsCommand {
        return history(DanaRsPacketRegistry.HISTORY_DAILY, fromMillis, zoneId)
    }

    fun historyPrime(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): DanaRsCommand {
        return history(DanaRsPacketRegistry.HISTORY_PRIME, fromMillis, zoneId)
    }

    fun historyRefill(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): DanaRsCommand {
        return history(DanaRsPacketRegistry.HISTORY_REFILL, fromMillis, zoneId)
    }

    fun historySuspend(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): DanaRsCommand {
        return history(DanaRsPacketRegistry.HISTORY_SUSPEND, fromMillis, zoneId)
    }

    fun apsSetEventHistory(
        packetType: Int,
        timeMillis: Long,
        param1: Int,
        param2: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): DanaRsCommand {
        val date = Instant.ofEpochMilli(timeMillis).atZone(zoneId)
        val request = ByteArray(11)
        request[0] = (packetType and 0xff).toByte()
        request[1] = (date.year - 2000 and 0xff).toByte()
        request[2] = (date.monthValue and 0xff).toByte()
        request[3] = (date.dayOfMonth and 0xff).toByte()
        request[4] = (date.hour and 0xff).toByte()
        request[5] = (date.minute and 0xff).toByte()
        request[6] = (date.second and 0xff).toByte()
        request[7] = (param1 ushr 8 and 0xff).toByte()
        request[8] = (param1 and 0xff).toByte()
        request[9] = (param2 ushr 8 and 0xff).toByte()
        request[10] = (param2 and 0xff).toByte()
        return raw(DanaRsPacketRegistry.APS_SET_EVENT_HISTORY, request)
    }

    fun optionGetPumpTime(): DanaRsCommand = command(DanaRsPacketRegistry.OPTION_GET_PUMP_TIME)
    fun optionGetPumpUtcAndTimeZone(): DanaRsCommand = command(DanaRsPacketRegistry.OPTION_GET_PUMP_UTC_AND_TIME_ZONE)
    fun optionGetUserOption(): DanaRsCommand = command(DanaRsPacketRegistry.OPTION_GET_USER_OPTION)

    fun optionSetPumpTime(timeMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): DanaRsCommand {
        return raw(DanaRsPacketRegistry.OPTION_SET_PUMP_TIME, encodeDateTime(Instant.ofEpochMilli(timeMillis).atZone(zoneId)))
    }

    fun optionSetPumpUtcAndTimeZone(timeMillis: Long, zoneOffset: Int): DanaRsCommand {
        val request = encodeDateTime(Instant.ofEpochMilli(timeMillis).atZone(ZoneOffset.UTC)) + byteArrayOf(zoneOffset.toByte())
        return raw(DanaRsPacketRegistry.OPTION_SET_PUMP_UTC_AND_TIME_ZONE, request)
    }

    fun optionSetUserOption(options: DanaRsUserOptions): DanaRsCommand {
        val size = if (options.hwModel >= 7) 15 else 13
        val request = ByteArray(size)
        request[0] = (if (options.timeDisplayType24) 0 else 1).toByte()
        request[1] = (if (options.buttonScrollOnOff) 1 else 0).toByte()
        request[2] = (options.beepAndAlarm and 0xff).toByte()
        request[3] = (options.lcdOnTimeSec and 0xff).toByte()
        request[4] = (options.backlightOnTimeSec and 0xff).toByte()
        request[5] = (options.selectedLanguage and 0xff).toByte()
        request[6] = (options.units and 0xff).toByte()
        request[7] = (options.shutdownHour and 0xff).toByte()
        request[8] = (options.lowReservoirRate and 0xff).toByte()
        request[9] = (options.cannulaVolume and 0xff).toByte()
        request[10] = (options.cannulaVolume ushr 8 and 0xff).toByte()
        request[11] = (options.refillAmount and 0xff).toByte()
        request[12] = (options.refillAmount ushr 8 and 0xff).toByte()
        if (options.hwModel >= 7) {
            request[13] = (options.target and 0xff).toByte()
            request[14] = (options.target ushr 8 and 0xff).toByte()
        }
        return raw(DanaRsPacketRegistry.OPTION_SET_USER_OPTION, request)
    }

    private fun history(definition: DanaRsPacketDefinition, fromMillis: Long, zoneId: ZoneId): DanaRsCommand {
        val request = if (fromMillis == 0L) {
            byteArrayOf(0, 1, 1, 0, 0, 0)
        } else {
            encodeDateTime(Instant.ofEpochMilli(fromMillis).atZone(zoneId))
        }
        return raw(definition, request)
    }

    private fun encodeDateTime(date: ZonedDateTime): ByteArray {
        return byteArrayOf(
            (date.year - 2000 and 0xff).toByte(),
            (date.monthValue and 0xff).toByte(),
            (date.dayOfMonth and 0xff).toByte(),
            (date.hour and 0xff).toByte(),
            (date.minute and 0xff).toByte(),
            (date.second and 0xff).toByte(),
        )
    }

    private companion object {
        const val APS_TEMP_BASAL_15_MIN = 150
        const val APS_TEMP_BASAL_30_MIN = 160
    }
}

enum class DanaRsBolusSpeed(val wireValue: Int) {
    U12_SECONDS(0),
    U30_SECONDS(1),
    U60_SECONDS(2),
}

data class DanaRsUserOptions(
    val hwModel: Int,
    val timeDisplayType24: Boolean,
    val buttonScrollOnOff: Boolean,
    val beepAndAlarm: Int,
    val lcdOnTimeSec: Int,
    val backlightOnTimeSec: Int,
    val selectedLanguage: Int,
    val units: Int,
    val shutdownHour: Int,
    val lowReservoirRate: Int,
    val cannulaVolume: Int,
    val refillAmount: Int,
    val target: Int,
)
