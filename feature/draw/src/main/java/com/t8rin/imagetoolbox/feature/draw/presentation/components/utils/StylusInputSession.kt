/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * https://www.apache.org/licenses/LICENSE-2.0
 */

package com.t8rin.imagetoolbox.feature.draw.presentation.components.utils

internal data class StylusPointer(
    val id: Long,
    val pressed: Boolean,
    val previouslyPressed: Boolean,
    val isStylus: Boolean,
    val consumed: Boolean = false
)

internal enum class StylusAction { Idle, Down, Move, Up, Cancel }

internal data class StylusUpdate(
    val action: StylusAction = StylusAction.Idle,
    val pointerId: Long? = null,
    val consumeEvent: Boolean = false
)

/** Keeps a stroke attached to its pen, never to the next finger in an event. */
internal class StylusInputSession {
    private var activePointerId: Long? = null
    private var waitForRelease = false
    private var consumeUntilRelease = false

    val hasActiveStroke: Boolean get() = activePointerId != null

    fun navigationStarted() {
        if (!hasActiveStroke) waitForRelease = true
    }

    fun update(pointers: List<StylusPointer>): StylusUpdate {
        val activeId = activePointerId
        if (activeId == null) {
            val consume = consumeUntilRelease
            if (waitForRelease && pointers.none { it.pressed }) {
                waitForRelease = false
                consumeUntilRelease = false
                return StylusUpdate(consumeEvent = consume)
            }
            if (waitForRelease && !consumeUntilRelease) return StylusUpdate()
            if (pointers.any { it.consumed }) return StylusUpdate(consumeEvent = consume)
            val pen = pointers.firstOrNull {
                it.isStylus && it.pressed && !it.previouslyPressed
            } ?: return StylusUpdate(consumeEvent = consume)
            // A resting palm can span several separate pen strokes.
            waitForRelease = false
            consumeUntilRelease = false
            activePointerId = pen.id
            return StylusUpdate(StylusAction.Down, pen.id, consumeEvent = true)
        }

        val pen = pointers.firstOrNull { it.id == activeId }
        val action = when {
            pen == null || !pen.isStylus || pen.consumed -> StylusAction.Cancel
            !pen.pressed -> StylusAction.Up
            else -> StylusAction.Move
        }
        if (action == StylusAction.Up || action == StylusAction.Cancel) {
            activePointerId = null
            waitForRelease = pointers.any { it.pressed }
            consumeUntilRelease = waitForRelease
        }
        return StylusUpdate(action, activeId, consumeEvent = true)
    }
}
