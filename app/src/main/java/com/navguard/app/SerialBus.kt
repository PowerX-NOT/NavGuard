package com.navguard.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Simple app-wide event bus for broadcasting incoming serial (Bluetooth) messages
 * without altering existing listener flows.
 */
object SerialBus {
    // No replay to avoid stale messages; small buffer to handle bursts
    private val _events = MutableSharedFlow<String>(replay = 0, extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events

    fun tryEmit(message: String) {
        _events.tryEmit(message)
    }
}
