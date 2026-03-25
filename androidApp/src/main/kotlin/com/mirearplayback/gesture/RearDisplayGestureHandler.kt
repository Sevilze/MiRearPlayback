package com.mirearplayback.gesture

import android.view.GestureDetector
import android.view.MotionEvent
import kotlin.math.abs

class RearDisplayGestureHandler(
    private val onSwipeLeft: () -> Unit,
    private val onSwipeRight: () -> Unit,
    private val onDoubleTap: () -> Unit
) : GestureDetector.SimpleOnGestureListener() {
    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        if (e1 == null) return false

        val dx = e2.x - e1.x
        val dy = e2.y - e1.y

        if (abs(dx) < abs(dy)) return false
        if (abs(dx) < SWIPE_THRESHOLD || abs(velocityX) < VELOCITY_THRESHOLD) return false

        if (dx > 0) {
            onSwipeRight()
        } else {
            onSwipeLeft()
        }
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        onDoubleTap()
        return true
    }

    companion object {
        private const val SWIPE_THRESHOLD = 100f
        private const val VELOCITY_THRESHOLD = 100f
    }
}
