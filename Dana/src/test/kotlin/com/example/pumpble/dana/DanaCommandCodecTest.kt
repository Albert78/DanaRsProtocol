package com.example.pumpble.dana

import com.example.pumpble.dana.commands.DanaRsBolusSpeed
import com.example.pumpble.dana.commands.DanaRsCommands
import com.example.pumpble.dana.commands.general.DanaRsPumpErrorState
import com.example.pumpble.dana.commands.history.DanaRsHistoryRecordKind
import com.example.pumpble.dana.commands.history.HistoryEndResponse
import com.example.pumpble.dana.commands.history.HistoryRecordResponse
import com.example.pumpble.commands.PumpStatus
import com.example.pumpble.protocol.ByteReader
import com.example.pumpble.protocol.ByteWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime

class DanaCommandCodecTest {
    private val commands = DanaRsCommands()

    @Test
    fun apsTemporaryBasalAboveOneHundredUsesAapsFifteenMinutePayloadLayout() {
        val command = commands.apsBasalSetTemporaryBasal(percent = 125)

        val payload = ByteWriter().also(command::encodePayload).toByteArray()

        assertArrayEquals(byteArrayOf(0x7d, 0x00, 150.toByte()), payload)
    }

    @Test
    fun apsTemporaryBasalBelowOneHundredUsesAapsThirtyMinutePayloadLayout() {
        val command = commands.apsBasalSetTemporaryBasal(percent = 80)

        val payload = ByteWriter().also(command::encodePayload).toByteArray()

        assertArrayEquals(byteArrayOf(0x50, 0x00, 160.toByte()), payload)
    }

    @Test
    fun bolusStartUsesCentiUnitPayloadLayout() {
        val command = commands.bolusSetStepBolusStart(
            amountUnits = 1.25,
            speed = DanaRsBolusSpeed.U12_SECONDS,
        )

        val payload = ByteWriter().also(command::encodePayload).toByteArray()

        assertArrayEquals(byteArrayOf(0x7d, 0x00, 0x00), payload)
    }

    @Test
    fun historyCommandsUseReferenceStartDateWhenNoCursorIsKnown() {
        val command = commands.historyBolus(fromMillis = 0L)

        val payload = ByteWriter().also(command::encodePayload).toByteArray()

        assertArrayEquals(byteArrayOf(0, 1, 1, 0, 0, 0), payload)
    }

    @Test
    fun ackCommandsDecodeOneByteResultCode() {
        val command = commands.basalSetCancelTemporaryBasal()

        val response = command.decodePayload(ByteReader(byteArrayOf(0x00)))

        assertEquals(PumpStatus.OK, response.status)
        assertEquals(0x00, response.resultCode)
    }

    @Test
    fun rawCommandsKeepResponsePayloadAvailableForSpecificParsers() {
        val command = commands.apsHistoryEvents(fromMillis = 0L)
        val responsePayload = byteArrayOf(0x01, 0x02, 0x03)

        val response = command.decodePayload(ByteReader(responsePayload))

        assertEquals(PumpStatus.OK, response.status)
        assertArrayEquals(responsePayload, response.payload)
    }

    @Test
    fun initialScreenInformationDecodesReferenceLayout() {
        val command = commands.generalInitialScreenInformation()
        val response = command.decodePayload(
            ByteReader(
                byteArrayOf(
                    0x1d,
                    0x7b, 0x00,
                    0xc4.toByte(), 0x09,
                    0x10, 0x27,
                    0x50, 0x00,
                    150.toByte(),
                    88,
                    0x2c, 0x01,
                    0x19, 0x00,
                    0x02,
                ),
            ),
        )

        assertEquals(PumpStatus.OK, response.status)
        assertEquals(true, response.pumpSuspended)
        assertEquals(true, response.tempBasalInProgress)
        assertEquals(true, response.extendedBolusInProgress)
        assertEquals(true, response.dualBolusInProgress)
        assertEquals(1.23, response.dailyTotalUnits, 0.0001)
        assertEquals(25, response.maxDailyTotalUnits)
        assertEquals(100.0, response.reservoirRemainingUnits, 0.0001)
        assertEquals(0.8, response.currentBasalUnitsPerHour, 0.0001)
        assertEquals(150, response.tempBasalPercent)
        assertEquals(88, response.batteryRemainingPercent)
        assertEquals(3.0, response.extendedBolusAbsoluteRate, 0.0001)
        assertEquals(0.25, response.insulinOnBoardUnits, 0.0001)
        assertEquals(DanaRsPumpErrorState.DAILY_MAX, response.errorState)
    }

    @Test
    fun basalRatesDecodeTwentyFourHourlyValues() {
        val command = commands.basalGetBasalRate()
        val payload = ByteWriter()
            .writeUInt16Le(300)
            .writeUInt8(1)
            .also { writer ->
                repeat(24) { hour -> writer.writeUInt16Le(100 + hour) }
            }
            .toByteArray()

        val response = command.decodePayload(ByteReader(payload))

        assertEquals(3.0, response.maxBasalUnitsPerHour, 0.0001)
        assertEquals(0.01, response.basalStepUnits, 0.0001)
        assertEquals(true, response.basalStepSupported)
        assertEquals(24, response.hourlyRatesUnits.size)
        assertEquals(1.0, response.hourlyRatesUnits.first(), 0.0001)
        assertEquals(1.23, response.hourlyRatesUnits.last(), 0.0001)
    }

    @Test
    fun stepBolusInformationDecodesAmountsAndClockTime() {
        val command = commands.bolusGetStepBolusInformation()
        val response = command.decodePayload(
            ByteReader(
                byteArrayOf(
                    0x00,
                    0x02,
                    0x7d, 0x00,
                    14,
                    30,
                    0x32, 0x00,
                    0xf4.toByte(), 0x01,
                    5,
                ),
            ),
        )

        assertEquals(PumpStatus.OK, response.status)
        assertEquals(2, response.bolusType)
        assertEquals(1.25, response.initialBolusAmountUnits, 0.0001)
        assertEquals(LocalTime.of(14, 30), response.lastBolusTimeOfDay)
        assertEquals(0.5, response.lastBolusAmountUnits, 0.0001)
        assertEquals(5.0, response.maxBolusUnits, 0.0001)
        assertEquals(0.05, response.bolusStepUnits, 0.0001)
    }

    @Test
    fun optionPumpTimeDecodesDanaDateTime() {
        val command = commands.optionGetPumpTime()

        val response = command.decodePayload(ByteReader(byteArrayOf(26, 5, 15, 9, 45, 12)))

        assertEquals(PumpStatus.OK, response.status)
        assertEquals(LocalDateTime.of(2026, 5, 15, 9, 45, 12), response.pumpTime)
    }

    @Test
    fun userOptionsDecodeSelectableLanguagesAndTarget() {
        val command = commands.optionGetUserOption()
        val response = command.decodePayload(
            ByteReader(
                byteArrayOf(
                    0,
                    1,
                    2,
                    5,
                    15,
                    3,
                    0,
                    12,
                    20,
                    0x2c, 0x01,
                    0xf4.toByte(), 0x01,
                    1,
                    2,
                    3,
                    4,
                    5,
                    100,
                    0,
                ),
            ),
        )

        assertEquals(PumpStatus.OK, response.status)
        assertEquals(true, response.timeDisplayType24)
        assertEquals(true, response.buttonScrollOnOff)
        assertEquals(300, response.cannulaVolume)
        assertEquals(500, response.refillAmount)
        assertEquals(listOf(1, 2, 3, 4, 5), response.selectableLanguages)
        assertEquals(100, response.target)
    }

    @Test
    fun historyEndPacketDecodesResultAndTotalCount() {
        val command = commands.historyBolus(fromMillis = 0L)

        val response = command.decodePayload(ByteReader(byteArrayOf(0x00, 0x02, 0x00)))

        val end = response as HistoryEndResponse
        assertEquals(PumpStatus.OK, end.status)
        assertEquals(0, end.errorCode)
        assertEquals(2, end.totalCount)
    }

    @Test
    fun historyRecordPacketDecodesBolusRecord() {
        val command = commands.historyBolus(fromMillis = 0L)

        val response = command.decodePayload(
            ByteReader(byteArrayOf(0x02, 26, 5, 15, 14, 30, 10, 0x82.toByte(), 0x00, 0x7d)),
        )

        val record = (response as HistoryRecordResponse).record
        assertEquals(DanaRsHistoryRecordKind.BOLUS, record.kind)
        assertEquals(LocalDateTime.of(2026, 5, 15, 14, 30), record.timestamp)
        assertEquals("S", record.bolusType)
        assertEquals(130, record.durationMinutes)
        assertEquals(1.25, record.value ?: 0.0, 0.0001)
    }
}
