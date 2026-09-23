package com.midhun.a3dmodelviewer.filament

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock


object GlbLoadGate {

    private val lock = ReentrantLock()
    private val notBusy = lock.newCondition()
    private var activeLoads = 0
    private const val maxConcurrent = 1

    val liveWindowCount = AtomicInteger(0)

    fun <T> withPermit(block: () -> T): T {
        lock.withLock {
            while (activeLoads >= maxConcurrent) {
                notBusy.await()
            }
            activeLoads++
        }
        try {
            return block()
        } finally {
            lock.withLock {
                activeLoads--
                notBusy.signalAll()
            }
        }
    }


    fun frameIntervalNs(
        windowCount: Int,
        frontmost: Boolean,
        interacting: Boolean
    ): Long {
        if (interacting) {
            return when {
                windowCount >= 4 -> 48_000_000L // ~20 FPS
                windowCount >= 2 -> 40_000_000L // ~25 FPS
                else -> 33_000_000L // ~30 FPS
            }
        }
        if (frontmost) {
            return when {
                windowCount >= 4 -> 66_000_000L // ~15 FPS
                windowCount >= 2 -> 50_000_000L // ~20 FPS
                else -> 40_000_000L // ~25 FPS
            }
        }
        // Background windows: very low tick while settling / loading.
        return 100_000_000L // ~10 FPS
    }
}
