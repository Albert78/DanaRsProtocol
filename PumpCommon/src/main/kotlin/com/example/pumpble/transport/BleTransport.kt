package com.example.pumpble.transport

import kotlinx.coroutines.flow.Flow

/**
 * Minimal transport contract required by [com.example.pumpble.PumpClient].
 *
 * Implementations expose raw notification payloads and provide serialized writes to the device. The
 * transport does not parse pump packets; that responsibility belongs to a [com.example.pumpble.protocol.PumpProtocolCodec].
 */
interface BleTransport {
    val notifications: Flow<ByteArray>

    suspend fun write(bytes: ByteArray)
    suspend fun close()
}
