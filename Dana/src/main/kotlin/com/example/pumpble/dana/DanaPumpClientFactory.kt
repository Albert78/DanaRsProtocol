package com.example.pumpble.dana

import com.example.pumpble.PumpClient
import com.example.pumpble.dana.commands.DanaRsCommands
import com.example.pumpble.dana.protocol.DanaRsPacketCodec
import com.example.pumpble.transport.BleTransport

/**
 * Bundles the generic client with the Dana command factory configured for one pump model.
 */
data class DanaPumpClient(
    val client: PumpClient,
    val commands: DanaRsCommands,
)

object DanaPumpClientFactory {
    fun createDanaRsCompatible(
        transport: BleTransport,
        model: DanaPumpModel = DanaPumpModel.DANA_I,
        codec: DanaRsPacketCodec = DanaRsPacketCodec(),
    ): DanaPumpClient {
        return create(
            transport = transport,
            codec = codec,
        )
    }

    /**
     * Creates a Dana client from an already connected transport.
     *
     * Connection, Android bonding, and pump-side pairing should be completed before this factory is
     * called. This keeps session setup separate from command execution.
     */
    fun create(
        transport: BleTransport,
        codec: DanaPacketCodec,
    ): DanaPumpClient {
        return DanaPumpClient(
            client = PumpClient(
                transport = transport,
                codec = codec,
            ),
            commands = DanaRsCommands(),
        )
    }
}
