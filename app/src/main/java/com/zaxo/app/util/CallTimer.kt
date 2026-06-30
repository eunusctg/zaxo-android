package com.zaxo.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Call timer with hold support.
 * Tracks elapsed call time, subtracting any time spent on hold.
 * Uses absolute timestamps to survive configuration changes (F88).
 */
class CallTimer {

    private var callStartTime: Long = 0L
    private var totalHoldTime: Long = 0L
    private var holdStartTime: Long = 0L
    private var isOnHold: Boolean = false

    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    fun start() {
        callStartTime = System.currentTimeMillis()
        isOnHold = false
        totalHoldTime = 0L
    }

    fun hold() {
        if (!isOnHold) {
            isOnHold = true
            holdStartTime = System.currentTimeMillis()
        }
    }

    fun resume() {
        if (isOnHold) {
            isOnHold = false
            totalHoldTime += System.currentTimeMillis() - holdStartTime
        }
    }

    fun getElapsedSeconds(): Int {
        if (callStartTime == 0L) return 0
        val now = System.currentTimeMillis()
        val raw = if (isOnHold) {
            now - callStartTime - totalHoldTime - (now - holdStartTime)
        } else {
            now - callStartTime - totalHoldTime
        }
        return (raw / 1000).toInt().coerceAtLeast(0)
    }

    fun format(): String {
        val seconds = getElapsedSeconds()
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    /** Call this from a coroutine loop to keep the flow updated */
    fun tick() {
        _elapsedSeconds.value = getElapsedSeconds()
    }

    fun reset() {
        callStartTime = 0L
        totalHoldTime = 0L
        holdStartTime = 0L
        isOnHold = false
        _elapsedSeconds.value = 0
    }

    fun isRunning(): Boolean = callStartTime > 0L
}
