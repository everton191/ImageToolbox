package com.t8rin.imagetoolbox.feature.draw.presentation.components

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StylusPointerIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private data class Contact(
        val id: Int = 0,
        val x: Float,
        val y: Float = .5f,
        val tool: Int = MotionEvent.TOOL_TYPE_STYLUS
    )

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(20)
        assertTrue(message, condition())
    }

    private fun withDrawer(stylusOnly: Boolean = true, test: (LiveDrawTestActivity) -> Unit) {
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, LiveDrawTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as LiveDrawTestActivity
        try {
            instrumentation.runOnMainSync { activity.stylusOnlyDraw = stylusOnly }
            await("Drawer did not initialize") { activity.ready && activity.bounds.width > 0 && activity.frame != null }
            instrumentation.waitForIdleSync()
            test(activity)
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }

    private fun event(bounds: Rect, down: Long, action: Int, vararg contacts: Contact) {
        val properties = contacts.map { contact ->
            MotionEvent.PointerProperties().apply { id = contact.id; toolType = contact.tool }
        }.toTypedArray()
        val coordinates = contacts.map { contact ->
            MotionEvent.PointerCoords().apply {
                x = bounds.left + bounds.width * contact.x
                y = bounds.top + bounds.height * contact.y
                pressure = 1f
                size = .01f
            }
        }.toTypedArray()
        val event = MotionEvent.obtain(
            down, SystemClock.uptimeMillis(), action, contacts.size, properties, coordinates,
            0, 0, 1f, 1f, 0, 0,
            if (contacts.any { it.tool == MotionEvent.TOOL_TYPE_STYLUS }) {
                InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN
            } else InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
        try {
            instrumentation.sendPointerSync(event)
        } finally {
            event.recycle()
        }
        SystemClock.sleep(40)
    }

    private fun stroke(activity: LiveDrawTestActivity, tool: Int) {
        val bounds = activity.bounds
        val down = SystemClock.uptimeMillis()
        event(bounds, down, MotionEvent.ACTION_DOWN, Contact(x = .2f, tool = tool))
        for (step in 1..8) {
            event(bounds, down, MotionEvent.ACTION_MOVE, Contact(x = .2f + step * .075f, tool = tool))
        }
        event(bounds, down, MotionEvent.ACTION_UP, Contact(x = .8f, tool = tool))
    }

    @Test
    fun onlyPenAddsInkWhileSingleFingerDragDoesNot() = withDrawer { activity ->
        stroke(activity, MotionEvent.TOOL_TYPE_FINGER)
        assertTrue("Finger drag added a drawing", activity.paths.isEmpty())
        stroke(activity, MotionEvent.TOOL_TYPE_STYLUS)
        await("Pen did not commit ink") { activity.ready && activity.paths.size == 1 }
        assertEquals(1f, activity.zoomState.scale, .01f)
        assertTrue(activity.frame!!.let { it.getPixel(it.width / 2, it.height / 2) != android.graphics.Color.WHITE })
    }

    @Test
    fun optionalModeOffKeepsFingerDrawing() = withDrawer(stylusOnly = false) { activity ->
        stroke(activity, MotionEvent.TOOL_TYPE_FINGER)
        await("Legacy finger drawing stopped working") { activity.ready && activity.paths.size == 1 }
    }

    @Test
    fun fingerPinchAndOneFingerPanDoNotAddInk() = withDrawer { activity ->
        val bounds = activity.bounds
        val down = SystemClock.uptimeMillis()
        fun finger(id: Int, x: Float) = Contact(id, x, tool = MotionEvent.TOOL_TYPE_FINGER)
        event(bounds, down, MotionEvent.ACTION_DOWN, finger(0, .4f))
        event(bounds, down, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), finger(0, .4f), finger(1, .6f))
        for (step in 1..6) {
            event(bounds, down, MotionEvent.ACTION_MOVE, finger(0, .4f - step * .025f), finger(1, .6f + step * .025f))
        }
        event(bounds, down, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), finger(0, .25f), finger(1, .75f))
        event(bounds, down, MotionEvent.ACTION_UP, finger(0, .25f))
        await("Finger pinch did not zoom") { activity.zoomState.scale > 1.2f }
        SystemClock.sleep(150)
        val beforePan = activity.zoomState.offsetX
        val panDown = SystemClock.uptimeMillis()
        event(bounds, panDown, MotionEvent.ACTION_DOWN, finger(0, .4f))
        for (step in 1..6) event(bounds, panDown, MotionEvent.ACTION_MOVE, finger(0, .4f + step * .03f))
        await("Single finger did not pan zoomed content") { activity.zoomState.offsetX > beforePan + 1f }
        event(bounds, panDown, MotionEvent.ACTION_UP, finger(0, .58f))
        assertTrue("Navigation added a drawing", activity.paths.isEmpty())
    }

    @Test
    fun palmCannotZoomOrContinueThePenStrokeAfterPenLifts() = withDrawer { activity ->
        val bounds = activity.bounds
        val down = SystemClock.uptimeMillis()
        event(bounds, down, MotionEvent.ACTION_DOWN, Contact(x = .2f))
        event(bounds, down, MotionEvent.ACTION_MOVE, Contact(x = .4f))
        val palm = Contact(1, .7f, .7f, MotionEvent.TOOL_TYPE_FINGER)
        event(bounds, down, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), Contact(x = .4f), palm)
        event(bounds, down, MotionEvent.ACTION_MOVE, Contact(x = .6f), palm.copy(x = .85f))
        event(bounds, down, MotionEvent.ACTION_POINTER_UP, Contact(x = .6f), palm.copy(x = .85f))
        await("Pen did not finish when it lifted") { activity.ready && activity.paths.size == 1 }
        event(bounds, down, MotionEvent.ACTION_MOVE, palm.copy(x = .3f))
        event(bounds, down, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), palm.copy(x = .3f), Contact(x = .2f, y = .3f))
        event(bounds, down, MotionEvent.ACTION_MOVE, palm.copy(x = .3f), Contact(x = .5f, y = .3f))
        event(bounds, down, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), palm.copy(x = .3f), Contact(x = .5f, y = .3f))
        await("Resting palm blocked the second pen stroke") { activity.ready && activity.paths.size == 2 }
        event(bounds, down, MotionEvent.ACTION_MOVE, palm.copy(x = .7f))
        event(bounds, down, MotionEvent.ACTION_UP, palm.copy(x = .3f))
        assertEquals("Palm added another stroke", 2, activity.paths.size)
        assertEquals("Palm changed zoom", 1f, activity.zoomState.scale, .01f)
        assertEquals("Palm panned the drawing", 0f, activity.zoomState.offsetX, .5f)
    }

    @Test
    fun cancelledPenStrokeLeavesNoInkAndNextStrokeWorks() = withDrawer { activity ->
        val bounds = activity.bounds
        val down = SystemClock.uptimeMillis()
        event(bounds, down, MotionEvent.ACTION_DOWN, Contact(x = .2f))
        event(bounds, down, MotionEvent.ACTION_MOVE, Contact(x = .6f))
        event(bounds, down, MotionEvent.ACTION_CANCEL, Contact(x = .6f))
        await("Cancelled live ink remained") {
            activity.frame!!.let { it.getPixel(it.width / 2, it.height / 2) == android.graphics.Color.WHITE }
        }
        assertTrue("Cancelled stroke was committed", activity.paths.isEmpty())
        stroke(activity, MotionEvent.TOOL_TYPE_STYLUS)
        await("Next pen stroke was lost after cancellation") { activity.ready && activity.paths.size == 1 }
    }

    @Test
    fun penThenFingerTapCannotBecomeFalseDoubleTapButFingerDoubleTapStillWorks() = withDrawer { activity ->
        val bounds = activity.bounds
        val down = SystemClock.uptimeMillis()
        event(bounds, down, MotionEvent.ACTION_DOWN, Contact(x = .4f))
        event(bounds, down, MotionEvent.ACTION_MOVE, Contact(x = .45f))
        event(bounds, down, MotionEvent.ACTION_UP, Contact(x = .45f))
        val finger = Contact(x = .5f, tool = MotionEvent.TOOL_TYPE_FINGER)
        val fingerDown = SystemClock.uptimeMillis()
        event(bounds, fingerDown, MotionEvent.ACTION_DOWN, finger)
        event(bounds, fingerDown, MotionEvent.ACTION_UP, finger)
        SystemClock.sleep(600)
        assertEquals("Pen was interpreted as the first finger tap", 1f, activity.zoomState.scale, .01f)
        await("Pen stroke did not finish") { activity.ready && activity.paths.size == 1 }
        repeat(2) {
            val tapDown = SystemClock.uptimeMillis()
            event(bounds, tapDown, MotionEvent.ACTION_DOWN, finger)
            event(bounds, tapDown, MotionEvent.ACTION_UP, finger)
        }
        await("Finger double tap stopped zooming") { activity.zoomState.scale > 1.2f }
        assertEquals("Finger taps added ink", 1, activity.paths.size)
    }

    @Test
    fun selectedEraserStillErasesPenInk() = withDrawer { activity ->
        stroke(activity, MotionEvent.TOOL_TYPE_STYLUS)
        await("Initial ink did not commit") { activity.ready && activity.paths.size == 1 }
        instrumentation.runOnMainSync { activity.erasing = true }
        instrumentation.waitForIdleSync()
        stroke(activity, MotionEvent.TOOL_TYPE_STYLUS)
        await("Pen eraser did not commit") { activity.ready && activity.paths.size == 2 }
        assertTrue(activity.paths.last().isErasing)
        assertEquals(android.graphics.Color.WHITE, activity.frame!!.let { it.getPixel(it.width / 2, it.height / 2) })
    }
}
