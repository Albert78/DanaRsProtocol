package com.example.pumpble.dana.commands

import com.example.pumpble.dana.commands.aps.ApsBasalSetTemporaryBasalCommand
import com.example.pumpble.dana.commands.aps.ApsHistoryEventsCommand
import com.example.pumpble.dana.commands.aps.ApsSetEventHistoryCommand
import com.example.pumpble.dana.commands.basal.BasalGetBasalRateCommand
import com.example.pumpble.dana.commands.basal.BasalGetProfileNumberCommand
import com.example.pumpble.dana.commands.basal.BasalSetCancelTemporaryBasalCommand
import com.example.pumpble.dana.commands.basal.BasalSetProfileBasalRateCommand
import com.example.pumpble.dana.commands.basal.BasalSetProfileNumberCommand
import com.example.pumpble.dana.commands.basal.BasalSetSuspendOffCommand
import com.example.pumpble.dana.commands.basal.BasalSetSuspendOnCommand
import com.example.pumpble.dana.commands.basal.BasalSetTemporaryBasalCommand
import com.example.pumpble.dana.commands.bolus.BolusGet24CIRCFArrayCommand
import com.example.pumpble.dana.commands.bolus.BolusGetBolusOptionCommand
import com.example.pumpble.dana.commands.bolus.BolusGetBolusRateCommand
import com.example.pumpble.dana.commands.bolus.BolusGetCIRCFArrayCommand
import com.example.pumpble.dana.commands.bolus.BolusGetCalculationInformationCommand
import com.example.pumpble.dana.commands.bolus.BolusGetStepBolusInformationCommand
import com.example.pumpble.dana.commands.bolus.BolusSet24CIRCFArrayCommand
import com.example.pumpble.dana.commands.bolus.BolusSetBolusOptionCommand
import com.example.pumpble.dana.commands.bolus.BolusSetBolusRateCommand
import com.example.pumpble.dana.commands.bolus.BolusSetExtendedBolusCancelCommand
import com.example.pumpble.dana.commands.bolus.BolusSetExtendedBolusCommand
import com.example.pumpble.dana.commands.bolus.BolusSetStepBolusStartCommand
import com.example.pumpble.dana.commands.bolus.BolusSetStepBolusStopCommand
import com.example.pumpble.dana.commands.bolus.MissedBolusWindow
import com.example.pumpble.dana.commands.etc.EtcKeepConnectionCommand
import com.example.pumpble.dana.commands.etc.EtcSetHistorySaveCommand
import com.example.pumpble.dana.commands.general.GeneralGetPumpCheckCommand
import com.example.pumpble.dana.commands.general.GeneralGetShippingInformationCommand
import com.example.pumpble.dana.commands.general.GeneralGetShippingVersionCommand
import com.example.pumpble.dana.commands.general.GeneralGetUserTimeChangeFlagCommand
import com.example.pumpble.dana.commands.general.GeneralInitialScreenInformationCommand
import com.example.pumpble.dana.commands.general.GeneralSetHistoryUploadModeCommand
import com.example.pumpble.dana.commands.general.GeneralSetUserTimeChangeFlagClearCommand
import com.example.pumpble.dana.commands.general.ReviewBolusAverageCommand
import com.example.pumpble.dana.commands.general.ReviewGetPumpDecRatioCommand
import com.example.pumpble.dana.commands.history.HistoryAlarmCommand
import com.example.pumpble.dana.commands.history.HistoryAllHistoryCommand
import com.example.pumpble.dana.commands.history.HistoryBasalCommand
import com.example.pumpble.dana.commands.history.HistoryBloodGlucoseCommand
import com.example.pumpble.dana.commands.history.HistoryBolusCommand
import com.example.pumpble.dana.commands.history.HistoryCarbohydrateCommand
import com.example.pumpble.dana.commands.history.HistoryDailyCommand
import com.example.pumpble.dana.commands.history.HistoryPrimeCommand
import com.example.pumpble.dana.commands.history.HistoryRefillCommand
import com.example.pumpble.dana.commands.history.HistorySuspendCommand
import com.example.pumpble.dana.commands.history.HistoryTemporaryCommand
import com.example.pumpble.dana.commands.options.OptionGetPumpTimeCommand
import com.example.pumpble.dana.commands.options.OptionGetPumpUtcAndTimeZoneCommand
import com.example.pumpble.dana.commands.options.OptionGetUserOptionCommand
import com.example.pumpble.dana.commands.options.OptionSetPumpTimeCommand
import com.example.pumpble.dana.commands.options.OptionSetPumpUtcAndTimeZoneCommand
import com.example.pumpble.dana.commands.options.OptionSetUserOptionCommand
import java.time.ZoneId

/**
 * Public command factory for DanaRS/Dana-i packets.
 *
 * The individual command classes own request encoding and response decoding. This facade keeps the
 * call site compact and gives UI/application code one stable entry point.
 */
class DanaRsCommands {
    val definitions: List<DanaRsPacketDefinition> = DanaRsPacketRegistry.all

    fun basalGetBasalRate() = BasalGetBasalRateCommand()
    fun basalGetProfileNumber() = BasalGetProfileNumberCommand()
    fun basalSetCancelTemporaryBasal() = BasalSetCancelTemporaryBasalCommand()
    fun basalSetTemporaryBasal(ratioPercent: Int, durationHours: Int) =
        BasalSetTemporaryBasalCommand(ratioPercent, durationHours)

    fun basalSetProfileNumber(profileNumber: Int) = BasalSetProfileNumberCommand(profileNumber)
    fun basalSetSuspendOff() = BasalSetSuspendOffCommand()
    fun basalSetSuspendOn() = BasalSetSuspendOnCommand()
    fun basalSetProfileBasalRate(profileNumber: Int, hourlyRatesUnits: List<Double>) =
        BasalSetProfileBasalRateCommand(profileNumber, hourlyRatesUnits)

    fun apsBasalSetTemporaryBasal(percent: Int) = ApsBasalSetTemporaryBasalCommand(percent)
    fun apsHistoryEvents(
        fromMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        useUtcLayout: Boolean = false,
    ) = ApsHistoryEventsCommand(fromMillis, zoneId, useUtcLayout)

    fun apsSetEventHistory(
        packetType: Int,
        timeMillis: Long,
        param1: Int,
        param2: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) = ApsSetEventHistoryCommand(packetType, timeMillis, param1, param2, zoneId)

    fun bolusGet24CIRCFArray() = BolusGet24CIRCFArrayCommand()
    fun bolusGetBolusOption() = BolusGetBolusOptionCommand()

    /**
     * Doesn't work on Dana-i.
     */
    fun bolusGetBolusRate() = BolusGetBolusRateCommand()
    fun bolusGetCalculationInformation() = BolusGetCalculationInformationCommand()
    fun bolusGetCIRCFArray() = BolusGetCIRCFArrayCommand()
    fun bolusGetStepBolusInformation() = BolusGetStepBolusInformationCommand()
    fun bolusSetStepBolusStart(amountUnits: Double, speed: DanaRsBolusSpeed) =
        BolusSetStepBolusStartCommand(amountUnits, speed)

    fun bolusSetStepBolusStop() = BolusSetStepBolusStopCommand()
    fun bolusSetExtendedBolus(amountUnits: Double, durationHalfHours: Int) =
        BolusSetExtendedBolusCommand(amountUnits, durationHalfHours)

    fun bolusSetExtendedBolusCancel() = BolusSetExtendedBolusCancelCommand()
    fun bolusSet24CIRCFArray(ic: IntArray, cf: IntArray) = BolusSet24CIRCFArrayCommand(ic, cf)
    fun bolusSetBolusOption(
        extendedBolusEnabled: Boolean,
        bolusCalculationOption: Int,
        missedBolusConfig: Int,
        missedBolusWindows: List<MissedBolusWindow>,
    ) = BolusSetBolusOptionCommand(
        extendedBolusEnabled = extendedBolusEnabled,
        bolusCalculationOption = bolusCalculationOption,
        missedBolusConfig = missedBolusConfig,
        missedBolusWindows = missedBolusWindows,
    )

    /**
     * Doesn't work on Dana-i.
     */
    fun bolusSetBolusRate(
        maxBolusUnits: Double,
        bolusStepUnits: Double,
        speed: DanaRsBolusSpeed,
    ) = BolusSetBolusRateCommand(
        maxBolusUnits = maxBolusUnits,
        bolusStepUnits = bolusStepUnits,
        speed = speed,
    )

    fun etcKeepConnection() = EtcKeepConnectionCommand()
    fun etcSetHistorySave(
        historyType: Int,
        historyYear: Int,
        historyMonth: Int,
        historyDate: Int,
        historyHour: Int,
        historyMinute: Int,
        historySecond: Int,
        historyCode: Int,
        historyValue: Int,
    ) = EtcSetHistorySaveCommand(
        historyType = historyType,
        historyYear = historyYear,
        historyMonth = historyMonth,
        historyDate = historyDate,
        historyHour = historyHour,
        historyMinute = historyMinute,
        historySecond = historySecond,
        historyCode = historyCode,
        historyValue = historyValue,
    )

    fun generalGetShippingVersion() = GeneralGetShippingVersionCommand()
    fun generalGetPumpCheck() = GeneralGetPumpCheckCommand()
    fun generalGetShippingInformation() = GeneralGetShippingInformationCommand()
    fun generalGetUserTimeChangeFlag() = GeneralGetUserTimeChangeFlagCommand()
    fun generalInitialScreenInformation() = GeneralInitialScreenInformationCommand()
    fun generalSetHistoryUploadMode(mode: Int) = GeneralSetHistoryUploadModeCommand(mode)
    fun generalSetUserTimeChangeFlagClear() = GeneralSetUserTimeChangeFlagClearCommand()
    fun reviewBolusAverage() = ReviewBolusAverageCommand()
    fun reviewGetPumpDecRatio() = ReviewGetPumpDecRatioCommand()

    fun historyAlarm(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) =
        HistoryAlarmCommand(fromMillis, zoneId)

    fun historyAllHistory(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) =
        HistoryAllHistoryCommand(fromMillis, zoneId)

    fun historyBasal(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) =
        HistoryBasalCommand(fromMillis, zoneId)

    fun historyBloodGlucose(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) =
        HistoryBloodGlucoseCommand(fromMillis, zoneId)

    fun historyBolus(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) =
        HistoryBolusCommand(fromMillis, zoneId)

    fun historyCarbohydrate(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) =
        HistoryCarbohydrateCommand(fromMillis, zoneId)

    fun historyDaily(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) =
        HistoryDailyCommand(fromMillis, zoneId)

    fun historyPrime(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) =
        HistoryPrimeCommand(fromMillis, zoneId)

    fun historyRefill(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) =
        HistoryRefillCommand(fromMillis, zoneId)

    fun historySuspend(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) =
        HistorySuspendCommand(fromMillis, zoneId)

    fun historyTemporary(fromMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) =
        HistoryTemporaryCommand(fromMillis, zoneId)

    /**
     * Doesn't work on Dana-i.
     */
    fun optionGetPumpTime() = OptionGetPumpTimeCommand()
    fun optionGetPumpUtcAndTimeZone() = OptionGetPumpUtcAndTimeZoneCommand()
    fun optionGetUserOption() = OptionGetUserOptionCommand()

    /**
     * Doesn't work on Dana-i.
     */
    fun optionSetPumpTime(timeMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()) =
        OptionSetPumpTimeCommand(timeMillis, zoneId)

    fun optionSetPumpUtcAndTimeZone(timeMillis: Long, zoneOffset: Int) =
        OptionSetPumpUtcAndTimeZoneCommand(timeMillis, zoneOffset)

    fun optionSetUserOption(options: DanaRsUserOptions) = OptionSetUserOptionCommand(options)
}