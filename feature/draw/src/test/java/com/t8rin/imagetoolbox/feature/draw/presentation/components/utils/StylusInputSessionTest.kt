package com.t8rin.imagetoolbox.feature.draw.presentation.components.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StylusInputSessionTest {
    private fun pen(
        id: Long = 1,
        pressed: Boolean = true,
        previouslyPressed: Boolean = true,
        consumed: Boolean = false
    ) = StylusPointer(id, pressed, previouslyPressed, isStylus = true, consumed = consumed)

    private fun finger(
        id: Long = 2,
        pressed: Boolean = true,
        previouslyPressed: Boolean = true
    ) = StylusPointer(id, pressed, previouslyPressed, isStylus = false)

    @Test
    fun fingersStayAvailableForNavigation() {
        val session = StylusInputSession()
        assertEquals(StylusUpdate(), session.update(listOf(finger(previouslyPressed = false))))
        assertEquals(StylusUpdate(), session.update(listOf(finger(), finger(id = 3))))
        assertFalse(session.hasActiveStroke)
    }

    @Test
    fun hoverDoesNotStartStroke() {
        val session = StylusInputSession()
        assertEquals(StylusUpdate(), session.update(listOf(pen(pressed = false, previouslyPressed = false))))
        assertFalse(session.hasActiveStroke)
    }

    @Test
    fun freshPenContactStartsStroke() {
        val session = StylusInputSession()
        assertEquals(
            StylusUpdate(StylusAction.Down, 1, consumeEvent = true),
            session.update(listOf(pen(previouslyPressed = false)))
        )
        assertTrue(session.hasActiveStroke)
    }

    @Test
    fun restingPalmCannotTakeOverOrMoveTheStroke() {
        val session = StylusInputSession()
        session.update(listOf(pen(previouslyPressed = false), finger()))
        assertEquals(
            StylusUpdate(StylusAction.Move, 1, consumeEvent = true),
            session.update(listOf(finger(), pen()))
        )
        assertEquals(
            StylusUpdate(StylusAction.Up, 1, consumeEvent = true),
            session.update(listOf(finger(), pen(pressed = false)))
        )
        assertFalse(session.hasActiveStroke)
    }

    @Test
    fun remainingPalmIsConsumedButAnotherPenStrokeCanStartWithoutLiftingPalm() {
        val session = StylusInputSession()
        session.update(listOf(pen(previouslyPressed = false), finger()))
        session.update(listOf(pen(pressed = false), finger()))
        assertEquals(StylusUpdate(consumeEvent = true), session.update(listOf(finger())))
        assertEquals(
            StylusUpdate(StylusAction.Down, 3, consumeEvent = true),
            session.update(listOf(pen(id = 3, previouslyPressed = false), finger()))
        )
        assertEquals(StylusAction.Move, session.update(listOf(finger(), pen(id = 3))).action)
        assertEquals(StylusAction.Up, session.update(listOf(pen(id = 3, pressed = false), finger())).action)
        assertEquals(StylusUpdate(consumeEvent = true), session.update(listOf(finger())))
        assertEquals(
            StylusUpdate(consumeEvent = true),
            session.update(listOf(finger(pressed = false)))
        )
        assertEquals(
            StylusAction.Down,
            session.update(listOf(pen(previouslyPressed = false))).action
        )
    }

    @Test
    fun missingPenCancelsInsteadOfSwitchingToFinger() {
        val session = StylusInputSession()
        session.update(listOf(pen(previouslyPressed = false)))
        assertEquals(
            StylusUpdate(StylusAction.Cancel, 1, consumeEvent = true),
            session.update(listOf(finger()))
        )
        assertFalse(session.hasActiveStroke)
    }

    @Test
    fun consumedPenCancelsStroke() {
        val session = StylusInputSession()
        session.update(listOf(pen(previouslyPressed = false)))
        assertEquals(StylusAction.Cancel, session.update(listOf(pen(consumed = true))).action)
        assertFalse(session.hasActiveStroke)
    }

    @Test
    fun pointerChangingToolCannotContinuePenStroke() {
        val session = StylusInputSession()
        session.update(listOf(pen(previouslyPressed = false)))
        assertEquals(StylusAction.Cancel, session.update(listOf(finger(id = 1))).action)
    }

    @Test
    fun penCannotStartDuringConsumedFingerNavigation() {
        val session = StylusInputSession()
        session.update(listOf(finger(previouslyPressed = false)))
        session.navigationStarted()
        assertEquals(
            StylusUpdate(),
            session.update(listOf(finger(), pen(previouslyPressed = false)))
        )
        assertEquals(StylusUpdate(), session.update(emptyList()))
        assertEquals(
            StylusAction.Down,
            session.update(listOf(pen(previouslyPressed = false))).action
        )
    }

    @Test
    fun consumedDownDoesNotStartStroke() {
        val session = StylusInputSession()
        assertEquals(
            StylusUpdate(),
            session.update(listOf(pen(previouslyPressed = false, consumed = true)))
        )
    }

    @Test
    fun secondPenDoesNotReplaceActivePen() {
        val session = StylusInputSession()
        session.update(listOf(pen(previouslyPressed = false)))
        assertEquals(
            StylusUpdate(StylusAction.Move, 1, consumeEvent = true),
            session.update(listOf(pen(id = 3, previouslyPressed = false), pen()))
        )
        assertEquals(
            StylusAction.Up,
            session.update(listOf(pen(id = 3), pen(pressed = false))).action
        )
        assertEquals(StylusUpdate(consumeEvent = true), session.update(listOf(pen(id = 3))))
    }

    @Test
    fun completeReleaseAllowsAnotherIndependentStroke() {
        val session = StylusInputSession()
        session.update(listOf(pen(previouslyPressed = false)))
        assertEquals(StylusAction.Up, session.update(listOf(pen(pressed = false))).action)
        assertEquals(
            StylusUpdate(StylusAction.Down, 4, consumeEvent = true),
            session.update(listOf(pen(id = 4, previouslyPressed = false)))
        )
    }

    @Test
    fun emptyCancelEventDoesNotLeaveSessionStuck() {
        val session = StylusInputSession()
        session.update(listOf(pen(previouslyPressed = false)))
        assertEquals(StylusAction.Cancel, session.update(emptyList()).action)
        assertEquals(
            StylusAction.Down,
            session.update(listOf(pen(previouslyPressed = false))).action
        )
    }
}
