package com.rosk.remoteosukey.input

import android.view.MotionEvent

/**
 * High-performance touch processor for osu! input relay.
 *
 * Supports two modes:
 * Mode 0: Split Screen (Left half = Key 1, Right half = Key 2)
 * Mode 1: Full Screen Floating Mode (Dynamic 2-finger tracking anywhere on screen)
 *
 * Safety Features:
 * 1. Active Pointer Verification: Auto-prunes ghost pointers if an active touch is lost.
 * 2. Absolute Clearance on ACTION_UP / ACTION_CANCEL: Guaranteed key release when 0 fingers remain.
 */
class TouchProcessor {

    data class KeyEvent(
        val keyIndex: Int,    // 0 = Key 1, 1 = Key 2
        val isDown: Boolean
    )

    data class CalibratedHandBounds(
        val key1XRatio: Float = -1f,
        val key2XRatio: Float = -1f,
        val deadZoneMinXRatio: Float = -1f,
        val deadZoneMaxXRatio: Float = -1f,
        val isCalibrated: Boolean = false
    )

    private var key1PointerId: Int = INVALID_POINTER
    private var key2PointerId: Int = INVALID_POINTER

    // Floating anchors for Full Screen Floating Mode
    private var anchor1X: Float = -1f
    private var anchor1Y: Float = -1f
    private var anchor2X: Float = -1f
    private var anchor2Y: Float = -1f

    private var calibrationBounds: CalibratedHandBounds? = null

    fun setCalibrationData(bounds: CalibratedHandBounds) {
        this.calibrationBounds = bounds
    }

    fun reset(): List<KeyEvent> {
        val events = mutableListOf<KeyEvent>()
        if (key1PointerId != INVALID_POINTER) {
            key1PointerId = INVALID_POINTER
            events.add(KeyEvent(0, false))
        }
        if (key2PointerId != INVALID_POINTER) {
            key2PointerId = INVALID_POINTER
            events.add(KeyEvent(1, false))
        }
        anchor1X = -1f
        anchor1Y = -1f
        anchor2X = -1f
        anchor2Y = -1f
        return events
    }

    fun isKeyPressed(keyIndex: Int): Boolean {
        return when (keyIndex) {
            0 -> key1PointerId != INVALID_POINTER
            1 -> key2PointerId != INVALID_POINTER
            else -> false
        }
    }

    fun getPointerIdForKey(keyIndex: Int): Int {
        return when (keyIndex) {
            0 -> key1PointerId
            1 -> key2PointerId
            else -> INVALID_POINTER
        }
    }

    /**
     * Checks if a touch position (x) falls within the calibrated middle finger rejection dead zone.
     */
    private fun isTouchInDeadZone(x: Float, screenWidth: Float, calibration: CalibratedHandBounds?): Boolean {
        if (screenWidth <= 0f) return false
        val cal = calibration ?: calibrationBounds
        if (cal != null && cal.isCalibrated && cal.deadZoneMinXRatio >= 0f && cal.deadZoneMaxXRatio >= 0f) {
            val xRatio = x / screenWidth
            return xRatio >= cal.deadZoneMinXRatio && xRatio <= cal.deadZoneMaxXRatio
        }
        return false
    }

    /**
     * Verifies that active pointers are still present in the MotionEvent.
     * Automatically releases any key whose pointer ID is missing or when ACTION_UP / ACTION_CANCEL occurs.
     */
    private fun verifyActivePointers(event: MotionEvent): List<KeyEvent> {
        val events = mutableListOf<KeyEvent>()
        val action = event.actionMasked

        // ACTION_UP or ACTION_CANCEL means ZERO fingers remain on screen.
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (key1PointerId != INVALID_POINTER) {
                key1PointerId = INVALID_POINTER
                events.add(KeyEvent(0, false))
            }
            if (key2PointerId != INVALID_POINTER) {
                key2PointerId = INVALID_POINTER
                events.add(KeyEvent(1, false))
            }
            return events
        }

        // Build set of currently active pointer IDs in MotionEvent
        val activeIds = HashSet<Int>(event.pointerCount)
        val actionIndex = if (action == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1

        for (i in 0 until event.pointerCount) {
            if (i == actionIndex) continue // Skip pointer currently being lifted
            activeIds.add(event.getPointerId(i))
        }

        // Prune key 1 if pointer lost
        if (key1PointerId != INVALID_POINTER && !activeIds.contains(key1PointerId)) {
            key1PointerId = INVALID_POINTER
            events.add(KeyEvent(0, false))
        }

        // Prune key 2 if pointer lost
        if (key2PointerId != INVALID_POINTER && !activeIds.contains(key2PointerId)) {
            key2PointerId = INVALID_POINTER
            events.add(KeyEvent(1, false))
        }

        return events
    }

    // ===== Mode 0: Split Screen Mode =====
    fun processSplitScreenTouches(
        event: MotionEvent,
        screenWidth: Float,
        screenHeight: Float,
        preventThirdFinger: Boolean = false,
        calibration: CalibratedHandBounds? = null
    ): List<KeyEvent> {
        val events = mutableListOf<KeyEvent>()
        val action = event.actionMasked

        // 1. Verify active pointers first
        events.addAll(verifyActivePointers(event))

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            return events
        }

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val actionIndex = event.actionIndex
            val pointerId = event.getPointerId(actionIndex)
            val x = event.getX(actionIndex)

            // Option C filtering: Check calibrated middle finger dead zone
            if (preventThirdFinger && isTouchInDeadZone(x, screenWidth, calibration)) {
                // Reject touch from middle finger dead zone
                return events
            }

            val isLeftSide = x < screenWidth / 2f

            if (isLeftSide) {
                if (key1PointerId == INVALID_POINTER) {
                    key1PointerId = pointerId
                    events.add(KeyEvent(0, true))
                }
            } else {
                if (key2PointerId == INVALID_POINTER) {
                    key2PointerId = pointerId
                    events.add(KeyEvent(1, true))
                }
            }
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            val actionIndex = event.actionIndex
            val pointerId = event.getPointerId(actionIndex)
            if (pointerId == key1PointerId) {
                key1PointerId = INVALID_POINTER
                events.add(KeyEvent(0, false))
            } else if (pointerId == key2PointerId) {
                key2PointerId = INVALID_POINTER
                events.add(KeyEvent(1, false))
            }
        }

        return events
    }

    // ===== Mode 1: Full Screen Floating Mode =====
    fun processFullScreenTouches(
        event: MotionEvent,
        screenWidth: Float,
        screenHeight: Float,
        preventThirdFinger: Boolean = false,
        calibration: CalibratedHandBounds? = null
    ): List<KeyEvent> {
        val events = mutableListOf<KeyEvent>()
        val action = event.actionMasked

        // 1. Verify active pointers first
        events.addAll(verifyActivePointers(event))

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            return events
        }

        when (action) {
            MotionEvent.ACTION_MOVE -> {
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
            }

            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val actionIndex = event.actionIndex
                val pointerId = event.getPointerId(actionIndex)
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)

                // Option C filtering: Check calibrated middle finger dead zone
                if (preventThirdFinger && isTouchInDeadZone(x, screenWidth, calibration)) {
                    // Reject touch from middle finger dead zone
                    return events
                }

                if (key1PointerId == INVALID_POINTER && key2PointerId == INVALID_POINTER) {
                    // Neither key is currently down
                    if (anchor1X != -1f && anchor2X != -1f) {
                        val dist1 = distanceSq(x, y, anchor1X, anchor1Y)
                        val dist2 = distanceSq(x, y, anchor2X, anchor2Y)
                        if (dist1 <= dist2) {
                            key1PointerId = pointerId
                            anchor1X = x
                            anchor1Y = y
                            events.add(KeyEvent(0, true))
                        } else {
                            key2PointerId = pointerId
                            anchor2X = x
                            anchor2Y = y
                            events.add(KeyEvent(1, true))
                        }
                    } else {
                        // Initial first touch
                        val cal = calibration ?: calibrationBounds
                        if (cal != null && cal.isCalibrated && cal.deadZoneMinXRatio >= 0f) {
                            val xRatio = x / screenWidth
                            if (xRatio < cal.deadZoneMinXRatio) {
                                key1PointerId = pointerId
                                anchor1X = x
                                anchor1Y = y
                                events.add(KeyEvent(0, true))
                            } else {
                                key2PointerId = pointerId
                                anchor2X = x
                                anchor2Y = y
                                events.add(KeyEvent(1, true))
                            }
                        } else {
                            if (x < screenWidth / 2f) {
                                key1PointerId = pointerId
                                anchor1X = x
                                anchor1Y = y
                                events.add(KeyEvent(0, true))
                            } else {
                                key2PointerId = pointerId
                                anchor2X = x
                                anchor2Y = y
                                events.add(KeyEvent(1, true))
                            }
                        }
                    }
                } else if (key1PointerId != INVALID_POINTER && key2PointerId == INVALID_POINTER) {
                    // Key 1 is down -> Second touch MUST be Key 2
                    key2PointerId = pointerId
                    anchor2X = x
                    anchor2Y = y
                    events.add(KeyEvent(1, true))
                } else if (key2PointerId != INVALID_POINTER && key1PointerId == INVALID_POINTER) {
                    // Key 2 is down -> Second touch MUST be Key 1
                    key1PointerId = pointerId
                    anchor1X = x
                    anchor1Y = y
                    events.add(KeyEvent(0, true))
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val actionIndex = event.actionIndex
                val pointerId = event.getPointerId(actionIndex)
                if (pointerId == key1PointerId) {
                    key1PointerId = INVALID_POINTER
                    events.add(KeyEvent(0, false))
                } else if (pointerId == key2PointerId) {
                    key2PointerId = INVALID_POINTER
                    events.add(KeyEvent(1, false))
                }
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



