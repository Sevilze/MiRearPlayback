package com.mirearplayback.gesture

import android.view.MotionEvent
import kotlin.math.abs

class MultiTouchGestureHandler(private val onAction: (GestureType) -> Unit) {
    enum class GestureType {
        ONE_FINGER_SWIPE_LEFT,
        ONE_FINGER_SWIPE_RIGHT,
        ONE_FINGER_SWIPE_UP,
        ONE_FINGER_SWIPE_DOWN,
        TWO_FINGER_SWIPE_LEFT,
        TWO_FINGER_SWIPE_RIGHT,
        DOUBLE_TAP
    }

    private var initialX = 0f
    private var initialY = 0f
    private var initialPointerCount = 0
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var lastTapY = 0f
    private var isTracking = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y
                initialPointerCount = 1
                isTracking = true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    initialPointerCount = event.pointerCount
                    initialX = (event.getX(0) + event.getX(1)) / 2f
                    initialY = (event.getY(0) + event.getY(1)) / 2f
                }
            }

            MotionEvent.ACTION_UP -> {
                if (!isTracking) return false
                isTracking = false

                val dx = event.x - initialX
                val dy = event.y - initialY
                val absDx = abs(dx)
                val absDy = abs(dy)

                if (absDx < TAP_THRESHOLD && absDy < TAP_THRESHOLD) {
                    val now = System.currentTimeMillis()
                    val tapDx = abs(event.x - lastTapX)
                    val tapDy = abs(event.y - lastTapY)

                    if (now - lastTapTime < DOUBLE_TAP_TIMEOUT &&
                        tapDx < TAP_SLOP && tapDy < TAP_SLOP
                    ) {
                        onAction(GestureType.DOUBLE_TAP)
                        lastTapTime = 0L
                        return true
                    }

                    lastTapTime = now
                    lastTapX = event.x
                    lastTapY = event.y
                    return false
                }

                if (absDx < SWIPE_THRESHOLD && absDy < SWIPE_THRESHOLD) return false

                if (initialPointerCount >= 2) {
                    if (absDx > absDy && absDx >= SWIPE_THRESHOLD) {
                        if (dx > 0) {
                            onAction(GestureType.TWO_FINGER_SWIPE_RIGHT)
                        } else {
                            onAction(GestureType.TWO_FINGER_SWIPE_LEFT)
                        }
                        return true
                    }
                } else {
                    if (absDx > absDy && absDx >= SWIPE_THRESHOLD) {
                        if (dx > 0) {
                            onAction(GestureType.ONE_FINGER_SWIPE_RIGHT)
                        } else {
                            onAction(GestureType.ONE_FINGER_SWIPE_LEFT)
                        }
                        return true
                    }
                    if (absDy > absDx && absDy >= SWIPE_THRESHOLD) {
                        if (dy > 0) {
                            onAction(GestureType.ONE_FINGER_SWIPE_DOWN)
                        } else {
                            onAction(GestureType.ONE_FINGER_SWIPE_UP)
                        }
                        return true
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 1) {
                    val dx: Float
                    val dy: Float
                    if (event.pointerCount >= 2) {
                        dx = (event.getX(0) + event.getX(1)) / 2f - initialX
                        dy = (event.getY(0) + event.getY(1)) / 2f - initialY
                    } else {
                        dx = event.x - initialX
                        dy = event.y - initialY
                    }

                    val absDx = abs(dx)
                    val absDy = abs(dy)

                    if (initialPointerCount >= 2 && absDx > absDy && absDx >= SWIPE_THRESHOLD) {
                        isTracking = false
                        if (dx > 0) {
                            onAction(GestureType.TWO_FINGER_SWIPE_RIGHT)
                        } else {
                            onAction(GestureType.TWO_FINGER_SWIPE_LEFT)
                        }
                        return true
                    }
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                isTracking = false
            }
        }
        return false
    }

    companion object {
        private const val SWIPE_THRESHOLD = 100f
        private const val TAP_THRESHOLD = 30f
        private const val TAP_SLOP = 50f
        private const val DOUBLE_TAP_TIMEOUT = 300L
    }
}
