package com.example.pumpble.commands

import com.example.pumpble.protocol.ByteReader
import com.example.pumpble.protocol.ByteWriter
import com.example.pumpble.protocol.CommandId

/**
 * Describes the operational risk class of a command.
 *
 * UI code and policy layers can use this value to distinguish passive reads from writes and active
 * control commands. The protocol layer still treats all commands uniformly.
 */
enum class CommandKind {
    READ,
    WRITE,
    CONTROL,
    ABORT_CONTROL,
}

enum class PumpStatus(val code: Int) {
    OK(0x00),
    REJECTED(0x01),
    BUSY(0x02),
    INVALID_PARAMETER(0x03),
    NOT_AUTHORIZED(0x04),
    DEVICE_ERROR(0x05),
    UNKNOWN(0xff);

    companion object {
        fun fromCode(code: Int): PumpStatus = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

interface PumpResponse {
    val status: PumpStatus
}

data class AckResponse(
    override val status: PumpStatus,
) : PumpResponse

/**
 * A typed command owns both directions of its wire payload.
 *
 * Keeping encode and decode logic together avoids a central parser that must know every pump-specific
 * message layout. It also makes each command easy to verify with golden request/response byte streams.
 */
interface PumpCommand<R : PumpResponse> {
    val commandId: CommandId
    val name: String
    val kind: CommandKind

    fun encodePayload(writer: ByteWriter)
    fun decodePayload(reader: ByteReader): R
}

fun ByteReader.readStatus(): PumpStatus = PumpStatus.fromCode(readUInt8())
