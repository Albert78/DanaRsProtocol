package com.example.pumpble.dana.commands

import com.example.pumpble.commands.PumpCommand
import com.example.pumpble.commands.PumpStatus
import com.example.pumpble.protocol.ByteReader
import com.example.pumpble.protocol.ByteWriter
import com.example.pumpble.protocol.CommandId

/**
 * Generic command wrapper for the real DanaRS packet definitions.
 *
 * The packet identity lives in [DanaRsPacketDefinition] and the request payload is supplied
 * explicitly. This keeps command creation deterministic while still allowing richer, command-specific
 * response models to be added later.
 */
class DanaRsCommand(
    val definition: DanaRsPacketDefinition,
    private val requestPayload: ByteArray = ByteArray(0),
) : PumpCommand<DanaRsRawResponse> {
    override val commandId: CommandId = CommandId(definition.opcode)
    override val name: String = definition.friendlyName
    override val kind = definition.kind

    override fun encodePayload(writer: ByteWriter) {
        writer.writeBytes(requestPayload)
    }

    override fun decodePayload(reader: ByteReader): DanaRsRawResponse {
        val payload = reader.readBytes(reader.remaining)
        val resultCode = if (definition.responseShape == DanaRsResponseShape.ACK_RESULT && payload.isNotEmpty()) {
            payload[0].toInt() and 0xff
        } else {
            null
        }
        return DanaRsRawResponse(
            status = resultCode?.let(PumpStatus::fromCode) ?: PumpStatus.OK,
            definition = definition,
            resultCode = resultCode,
            payload = payload,
        )
    }
}
