# Android: DanaRS/Dana-i BLE Protocol implementation

This repository contains an Android/Kotlin application that communicates with a Dana-i insulin pump via the DanaRS-compatible BLE protocol.

## Architecture

The separation is intentionally strict:

- `:PumpCommon`: generic command abstractions, byte readers/writers,
  protocol codecs, Android GATT transport, and `PumpClient`.
- `:Dana`: Dana-i/DanaRS-specific BLE profiles, pairing/handshake helpers,
  packet codec, command registry, and command factory.
- `:app`: Compose test console for scanning, connecting, starting the Dana-i session,
  and executing selected read, write, and control commands.

## Commands

`DanaRsCommands` is the public facade used by app code. The concrete request and
response logic lives in individual command classes below
`com.example.pumpble.dana.commands.*`, grouped by packet domain such as `basal`,
`bolus`, `history`, `general`, `options`, and `aps`.

Standard one-byte ACK responses are decoded as `DanaRsAckResponse`. Read commands
currently return `DanaRsRawResponse` so their payload remains available while the
command-specific field decoders are implemented incrementally.
