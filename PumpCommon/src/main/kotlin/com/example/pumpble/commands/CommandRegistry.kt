package com.example.pumpble.commands

import com.example.pumpble.protocol.CommandId

class CommandRegistry(commands: Iterable<PumpCommand<*>>) {
    private val byId: Map<CommandId, PumpCommand<*>> = commands.associateBy { it.commandId }

    fun find(commandId: CommandId): PumpCommand<*>? = byId[commandId]

    fun requireKnown(commandId: CommandId): PumpCommand<*> {
        return find(commandId) ?: error("Unknown command id: 0x${commandId.value.toString(16)}")
    }
}
