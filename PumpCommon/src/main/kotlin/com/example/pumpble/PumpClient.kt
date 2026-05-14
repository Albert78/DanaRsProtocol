package com.example.pumpble

import com.example.pumpble.commands.PumpCommand
import com.example.pumpble.commands.PumpResponse
import com.example.pumpble.protocol.ByteReader
import com.example.pumpble.protocol.ByteWriter
import com.example.pumpble.protocol.FrameCodec
import com.example.pumpble.protocol.PumpProtocolCodec
import com.example.pumpble.protocol.ProtocolFrame
import com.example.pumpble.transport.BleTransport
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * Executes typed pump commands over a byte-oriented BLE transport.
 *
 * The client intentionally knows nothing about a concrete pump model. Command classes encode and
 * decode their own payloads, while the injected [PumpProtocolCodec] handles the outer packet format
 * used by a specific pump family. Requests are serialized because many pump BLE protocols only allow
 * one in-flight command and because command ordering is safety-relevant for write/control actions.
 */
class PumpClient(
    private val transport: BleTransport,
    private val codec: PumpProtocolCodec = FrameCodec,
    private val defaultTimeout: Duration = 5.seconds,
) {
    private val transactionMutex = Mutex()
    private var nextSequence = 1

    suspend fun <R : PumpResponse> execute(
        command: PumpCommand<R>,
        timeout: Duration = defaultTimeout,
    ): R = transactionMutex.withLock {
        coroutineScope {
            val sequence = consumeSequence()
            val requestPayload = ByteWriter().also(command::encodePayload).toByteArray()
            val requestFrame = ProtocolFrame(
                sequence = sequence,
                commandId = command.commandId,
                flags = REQUEST_FLAGS,
                payload = requestPayload,
            )

            // Subscribe before writing so a fast notification cannot be missed between write
            // completion and response collection.
            val response = async {
                withTimeout(timeout) {
                    transport.notifications
                        .transform { bytes ->
                            codec.decodeFrames(bytes).forEach { emit(it) }
                        }
                        .first { frame ->
                            (frame.sequence == sequence || frame.sequence == NO_SEQUENCE) &&
                                frame.commandId == command.commandId
                        }
                }
            }

            transport.write(codec.encode(requestFrame))
            val responseFrame = response.await()
            val reader = ByteReader(responseFrame.payload)
            command.decodePayload(reader).also {
                reader.requireFullyConsumed(command.name)
            }
        }
    }

    private companion object {
        const val REQUEST_FLAGS = 0x00
        const val NO_SEQUENCE = 0
    }

    private fun consumeSequence(): Int {
        val current = nextSequence
        nextSequence = if (current == 0xffff) 1 else current + 1
        return current
    }
}
