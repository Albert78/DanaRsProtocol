package com.example.pumpble.dana.commands

import com.example.pumpble.commands.PumpResponse
import com.example.pumpble.commands.PumpStatus

/**
 * Raw decoded DanaRS response.
 *
 * This keeps the transport/protocol layer useful before every command-specific parser exists. Callers
 * can still inspect the packet definition, result code, and payload bytes, while later iterations can
 * replace selected raw responses with richer typed models command by command.
 */
data class DanaRsRawResponse(
    override val status: PumpStatus,
    val definition: DanaRsPacketDefinition,
    val resultCode: Int?,
    val payload: ByteArray,
) : PumpResponse {
    override fun equals(other: Any?): Boolean {
        return other is DanaRsRawResponse &&
            status == other.status &&
            definition == other.definition &&
            resultCode == other.resultCode &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = status.hashCode()
        result = 31 * result + definition.hashCode()
        result = 31 * result + (resultCode ?: 0)
        result = 31 * result + payload.contentHashCode()
        return result
    }
}
