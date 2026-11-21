package com.ranhroi.touchremote.ui.pager.files

class SimpleUpdateLimiter(
    private val minInterval: Long = 300,
    private val minProgressDelta: Int = 2
) {
    private var lastUpdateTime = 0L
    private var lastProgress = -1

    fun shouldUpdate(progress: Int): Boolean {
        if (progress >= 100) return true // Luôn update khi hoàn thành

        val now = System.currentTimeMillis()
        val timePassed = now - lastUpdateTime >= minInterval
        val progressChanged = progress - lastProgress >= minProgressDelta

        if (timePassed && progressChanged) {
            lastUpdateTime = now
            lastProgress = progress
            return true
        }
        return false
    }

    fun reset() {
        lastUpdateTime = 0L
        lastProgress = -1
    }
}