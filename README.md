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