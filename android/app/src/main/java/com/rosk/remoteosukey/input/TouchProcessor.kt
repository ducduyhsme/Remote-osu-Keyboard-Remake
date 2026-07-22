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

    private var key1PointerId: Int = INVALID_POINTER
    private var key2PointerId: Int = INVALID_POINTER

    // Floating anchors for Full Screen Floating Mode
    private var anchor1X: Float = -1f
    private var anchor1Y: Float = -1f
    private var anchor2X: Float = -1f
    private var anchor2Y: Float = -1f

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
     * Filters pointer IDs to prevent accidental touches from a 3rd finger using a Hybrid algorithm:
     * Layer 1: Spatial Bounds Filtering (Option A) for count >= 3.
     * Layer 2: Dynamic Dual-Cluster Memory & Micro-Instant Dead Zone for count < 3 (Stream/Doubletap).
     */
    fun filterThirdFingerPointers(
        event: MotionEvent,
        preventThirdFinger: Boolean,
        fingerPair: Int
    ): Set<Int> {
        val count = event.pointerCount
        val allIds = HashSet<Int>(count)
        for (i in 0 until count) {
            allIds.add(event.getPointerId(i))
        }

        if (!preventThirdFinger) {
            return allIds
        }

        // Finger Pair Types:
        // 0: Index + Middle (Adjacent)
        // 1: Index + Ring (Non-adjacent: Middle in between)
        // 2: Index + Pinky (Non-adjacent: Middle + Ring in between)
        // 3: Middle + Ring (Adjacent)
        // 4: Thumb + Index (Adjacent)
        // 5: Thumb + Middle (Non-adjacent: Index in between)
        val isNonAdjacent = fingerPair == 1 || fingerPair == 2 || fingerPair == 5

        if (count >= 3) {
            val sortedPointers = (0 until count).map { i ->
                Triple(event.getPointerId(i), event.getX(i), event.getY(i))
            }.sortedBy { it.second }

            val validIds = HashSet<Int>()

            if (isNonAdjacent) {
                // Layer 1: Spatial Bounds Filtering (Option A)
                // Outermost X pointers are the 2 valid playing fingers
                val minXPointer = sortedPointers.first()
                val maxXPointer = sortedPointers.last()
                validIds.add(minXPointer.first)
                validIds.add(maxXPointer.first)
            } else {
                // Layer 2: Anchor Distance Masking for adjacent pairs when 3+ fingers touch
                if (anchor1X >= 0f && anchor2X >= 0f && (key1PointerId != INVALID_POINTER || key2PointerId != INVALID_POINTER)) {
                    val minAnchorX = minOf(anchor1X, anchor2X)
                    val maxAnchorX = maxOf(anchor1X, anchor2X)
                    for (p in sortedPointers) {
                        val id = p.first
                        val x = p.second
                        if (id == key1PointerId || id == key2PointerId) {
                            validIds.add(id)
                        } else {
                            if (x < minAnchorX || x > maxAnchorX) {
                                validIds.add(id)
                            }
                        }
                    }
                } else {
                    validIds.addAll(allIds)
                }
            }
            return validIds
        }

        // ===== Micro-Instant Dead Zone & Radius Masking for count < 3 (Fast Streaming/Doubletap) =====
        if (isNonAdjacent && anchor1X >= 0f && anchor2X >= 0f) {
            val minAnchorX = minOf(anchor1X, anchor2X)
            val maxAnchorX = maxOf(anchor1X, anchor2X)
            val dx = maxAnchorX - minAnchorX

            // If anchors are at least 40px apart (distinct finger clusters)
            if (dx > 40f) {
                val deadZoneMinX = minAnchorX + (dx * 0.20f)
                val deadZoneMaxX = maxAnchorX - (dx * 0.20f)

                val validIds = HashSet<Int>()
                for (i in 0 until count) {
                    val id = event.getPointerId(i)
                    val x = event.getX(i)

                    // Keep existing active keys valid
                    if (id == key1PointerId || id == key2PointerId) {
                        validIds.add(id)
                    } else {
                        // Check if new touch falls in the Middle Dead Zone between Key 1 & Key 2 clusters
                        val isInsideDeadZone = x >= deadZoneMinX && x <= deadZoneMaxX
                        if (!isInsideDeadZone) {
                            validIds.add(id)
                        }
                    }
                }
                return validIds
            }
        }

        return allIds
    }

    /**
     * Verifies that active pointers are still present in the MotionEvent and not rejected as 3rd finger.
     * Automatically releases any key whose pointer ID is missing or invalid when ACTION_UP / ACTION_CANCEL occurs.
     */
    private fun verifyActivePointers(
        event: MotionEvent,
        preventThirdFinger: Boolean = false,
        fingerPair: Int = 0
    ): List<KeyEvent> {
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
        val actionIndex = if (action == MotionEvent.ACTION_POINTER_UP) event.actionIndex else -1
        val validPointers = filterThirdFingerPointers(event, preventThirdFinger, fingerPair)

        val activeIds = HashSet<Int>(event.pointerCount)
        for (i in 0 until event.pointerCount) {
            if (i == actionIndex) continue // Skip pointer currently being lifted
            val pid = event.getPointerId(i)
            if (validPointers.contains(pid)) {
                activeIds.add(pid)
            }
        }

        // Prune key 1 if pointer lost or invalidated as 3rd finger
        if (key1PointerId != INVALID_POINTER && !activeIds.contains(key1PointerId)) {
            key1PointerId = INVALID_POINTER
            events.add(KeyEvent(0, false))
        }

        // Prune key 2 if pointer lost or invalidated as 3rd finger
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
        fingerPair: Int = 0
    ): List<KeyEvent> {
        val events = mutableListOf<KeyEvent>()
        val action = event.actionMasked

        // 1. Verify active pointers first
        events.addAll(verifyActivePointers(event, preventThirdFinger, fingerPair))

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            return events
        }

        val validPointers = filterThirdFingerPointers(event, preventThirdFinger, fingerPair)

        if (action == MotionEvent.ACTION_MOVE) {
            for (i in 0 until event.pointerCount) {
                val pid = event.getPointerId(i)
                if (validPointers.contains(pid)) {
                    if (pid == key1PointerId) {
                        anchor1X = event.getX(i)
                        anchor1Y = event.getY(i)
                    } else if (pid == key2PointerId) {
                        anchor2X = event.getX(i)
                        anchor2Y = event.getY(i)
                    }
                }
            }
        } else if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            val actionIndex = event.actionIndex
            val pointerId = event.getPointerId(actionIndex)

            if (validPointers.contains(pointerId)) {
                val x = event.getX(actionIndex)
                val y = event.getY(actionIndex)
                val isLeftSide = x < screenWidth / 2f

                if (isLeftSide) {
                    if (key1PointerId == INVALID_POINTER) {
                        key1PointerId = pointerId
                        anchor1X = x
                        anchor1Y = y
                        events.add(KeyEvent(0, true))
                    }
                } else {
                    if (key2PointerId == INVALID_POINTER) {
                        key2PointerId = pointerId
                        anchor2X = x
                        anchor2Y = y
                        events.add(KeyEvent(1, true))
                    }
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
        fingerPair: Int = 0
    ): List<KeyEvent> {
        val events = mutableListOf<KeyEvent>()
        val action = event.actionMasked

        // 1. Verify active pointers first
        events.addAll(verifyActivePointers(event, preventThirdFinger, fingerPair))

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            return events
        }

        val validPointers = filterThirdFingerPointers(event, preventThirdFinger, fingerPair)

        when (action) {
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val pid = event.getPointerId(i)
                    if (validPointers.contains(pid)) {
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
            }

            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val actionIndex = event.actionIndex
                val pointerId = event.getPointerId(actionIndex)

                if (validPointers.contains(pointerId)) {
                    val x = event.getX(actionIndex)
                    val y = event.getY(actionIndex)

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



