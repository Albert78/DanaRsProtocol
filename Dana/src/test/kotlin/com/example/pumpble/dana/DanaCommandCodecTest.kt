package com.example.pumpble.dana

import com.example.pumpble.dana.commands.DanaRsBolusSpeed
import com.example.pumpble.dana.commands.DanaRsCommands
import com.example.pumpble.protocol.ByteWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Test

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
}
