package com.rosk.remoteosukey.input

import android.view.MotionEvent

/**
 * High-performance touch processor for osu! input relay.
 *
 * Design principles:
 * 1. NEVER block valid inputs when extra fingers touch the screen
 * 2. No object allocation in the hot path
 * 3. Simple left/right split mode by default
 * 4. First-touch-wins per side: only the first finger touching each half is tracked
 *
 * The key insight: In split mode, each half of the screen is a separate "key zone".
 * The first touch in each zone becomes the active pointer for that key.
 * Additional touches in the same zone are IGNORED (not rejected).
 * This means accidental touches from a middle finger will never cancel
 * the index or ring finger input.
 */
class TouchProcessor {

    data class KeyEvent(
        val keyIndex: Int,    // 0 = Key1 (left), 1 = Key2 (right)
        val isDown: Boolean
    )

    // Active pointer ID for each key (-1 = no active pointer)
    private var key1PointerId: Int = INVALID_POINTER
    private var key2PointerId: Int = INVALID_POINTER

    fun onFingerDown(pointerId: Int, x: Float, y: Float, isLeftSide: Boolean): KeyEvent? {
        return if (isLeftSide) {
            // Left side = Key 1
            if (key1PointerId == INVALID_POINTER) {
                key1PointerId = pointerId
                KeyEvent(keyIndex = 0, isDown = true)
            } else null
        } else {
            // Right side = Key 2
            if (key2PointerId == INVALID_POINTER) {
                key2PointerId = pointerId
                KeyEvent(keyIndex = 1, isDown = true)
            } else null
        }
    }

    fun onFingerUp(pointerId: Int): KeyEvent? {
        return when (pointerId) {
            key1PointerId -> {
                key1PointerId = INVALID_POINTER
                KeyEvent(keyIndex = 0, isDown = false)
            }
            key2PointerId -> {
                key2PointerId = INVALID_POINTER
                KeyEvent(keyIndex = 1, isDown = false)
            }
            else -> null
        }
    }

    fun reset() {
        key1PointerId = INVALID_POINTER
        key2PointerId = INVALID_POINTER
    }

    fun isKeyPressed(keyIndex: Int): Boolean {
        return when (keyIndex) {
            0 -> key1PointerId != INVALID_POINTER
            1 -> key2PointerId != INVALID_POINTER
            else -> false
        }
    }

    fun processFullScreenTouches(
        event: MotionEvent,
        screenWidth: Int,
        key1FingerIndex: Int,
        key2FingerIndex: Int
    ): List<KeyEvent> {
        val events = mutableListOf<KeyEvent>()
        
        // In full screen mode, we divide the screen into 5 equal vertical zones.
        val zoneWidth = screenWidth / 5f
        
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            val x = event.getX(i)
            
            // Determine zone index 0..4 (left to right)
            val zone = (x / zoneWidth).toInt().coerceIn(0, 4)
            
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                if (event.actionIndex == i) {
                    // Finger down
                    if (zone == key1FingerIndex && key1PointerId == INVALID_POINTER) {
                        key1PointerId = pointerId
                        events.add(KeyEvent(0, true))
                    } else if (zone == key2FingerIndex && key2PointerId == INVALID_POINTER) {
                        key2PointerId = pointerId
                        events.add(KeyEvent(1, true))
                    }
                }
            } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
                if (event.actionIndex == i) {
                    // Finger up
                    if (pointerId == key1PointerId) {
                        key1PointerId = INVALID_POINTER
                        events.add(KeyEvent(0, false))
                    } else if (pointerId == key2PointerId) {
                        key2PointerId = INVALID_POINTER
                        events.add(KeyEvent(1, false))
                    }
                }
            }
        }
        return events
    }

    companion object {
        private const val INVALID_POINTER = -1
    }
}
