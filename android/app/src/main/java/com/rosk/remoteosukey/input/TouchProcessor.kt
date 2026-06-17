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
        anchor1X = -1f
        anchor1Y = -1f
        anchor2X = -1f
        anchor2Y = -1f
    }

    fun isKeyPressed(keyIndex: Int): Boolean {
        return when (keyIndex) {
            0 -> key1PointerId != INVALID_POINTER
            1 -> key2PointerId != INVALID_POINTER
            else -> false
        }
    }

    // Anchors for Full Screen Floating Mode (Voronoi cell centers)
    private var anchor1X: Float = -1f
    private var anchor1Y: Float = -1f
    private var anchor2X: Float = -1f
    private var anchor2Y: Float = -1f

    fun getPointerIdForKey(keyIndex: Int): Int {
        return when (keyIndex) {
            0 -> key1PointerId
            1 -> key2PointerId
            else -> INVALID_POINTER
        }
    }

    fun processFullScreenTouches(event: MotionEvent, screenWidth: Float, screenHeight: Float): List<KeyEvent> {
        val events = mutableListOf<KeyEvent>()

        if (anchor1X == -1f) {
            anchor1X = screenWidth * 0.25f
            anchor1Y = screenHeight * 0.5f
            anchor2X = screenWidth * 0.75f
            anchor2Y = screenHeight * 0.5f
        }

        val action = event.actionMasked

        if (action == MotionEvent.ACTION_MOVE) {
            for (i in 0 until event.pointerCount) {
                val pid = event.getPointerId(i)
                val x = event.getX(i)
                val y = event.getY(i)
                if (pid == key1PointerId) {
                    anchor1X = x
                    anchor1Y = y
                } else if (pid == key2PointerId) {
                    anchor2X = x
                    anchor2Y = y
                }
            }
            return events
        }

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val actionIndex = event.actionIndex
            val pointerId = event.getPointerId(actionIndex)
            val x = event.getX(actionIndex)
            val y = event.getY(actionIndex)

            val dist1 = distanceSq(x, y, anchor1X, anchor1Y)
            val dist2 = distanceSq(x, y, anchor2X, anchor2Y)

            if (dist1 <= dist2) {
                if (key1PointerId == INVALID_POINTER) {
                    key1PointerId = pointerId
                    anchor1X = x
                    anchor1Y = y
                    events.add(KeyEvent(0, true))
                } else if (key2PointerId == INVALID_POINTER) {
                    key2PointerId = pointerId
                    anchor2X = x
                    anchor2Y = y
                    events.add(KeyEvent(1, true))
                }
            } else {
                if (key2PointerId == INVALID_POINTER) {
                    key2PointerId = pointerId
                    anchor2X = x
                    anchor2Y = y
                    events.add(KeyEvent(1, true))
                } else if (key1PointerId == INVALID_POINTER) {
                    key1PointerId = pointerId
                    anchor1X = x
                    anchor1Y = y
                    events.add(KeyEvent(0, true))
                }
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            val actionIndex = event.actionIndex
            val pointerId = event.getPointerId(actionIndex)
            if (pointerId == key1PointerId) {
                key1PointerId = INVALID_POINTER
                events.add(KeyEvent(0, false))
            } else if (pointerId == key2PointerId) {
                key2PointerId = INVALID_POINTER
                events.add(KeyEvent(1, false))
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            if (key1PointerId != INVALID_POINTER) {
                key1PointerId = INVALID_POINTER
                events.add(KeyEvent(0, false))
            }
            if (key2PointerId != INVALID_POINTER) {
                key2PointerId = INVALID_POINTER
                events.add(KeyEvent(1, false))
            }
        }

        return events
    }

    private fun distanceSq(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return dx * dx + dy * dy
    }

    companion object {
        private const val INVALID_POINTER = -1
    }
}
