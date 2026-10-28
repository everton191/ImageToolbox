/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/LICENSE-2.0>.
 */

package com.t8rin.imagetoolbox.feature.draw.presentation.components.utils

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.magnifier
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.t8rin.gesture.pointerMotionEvents
import com.t8rin.imagetoolbox.core.ui.widget.modifier.observePointersCountWithOffset
import com.t8rin.imagetoolbox.core.ui.widget.modifier.smartDelayAfterDownInMillis
import net.engawapg.lib.zoomable.ZoomState
import net.engawapg.lib.zoomable.ZoomableDefaults.defaultZoomOnDoubleTap
import net.engawapg.lib.zoomable.zoomable

fun Modifier.pointerDrawObserver(
    magnifierEnabled: Boolean,
    currentDrawPosition: Offset,
    zoomState: ZoomState,
    globalTouchPointersCount: MutableIntState,
    panEnabled: Boolean,
    stylusOnlyDraw: Boolean = false
) = this.composed {
    var globalTouchPosition by remember { mutableStateOf(Offset.Unspecified) }

    Modifier
        .fillMaxSize()
        .observePointersCountWithOffset { size, offset ->
            globalTouchPointersCount.intValue = size
            globalTouchPosition = offset
        }
        .then(
            if (magnifierEnabled) {
                Modifier.magnifier(
                    sourceCenter = {
                        if (currentDrawPosition.isSpecified) {
                            globalTouchPosition
                        } else Offset.Unspecified
                    },
                    magnifierCenter = {
                        globalTouchPosition - Offset(0f, 100.dp.toPx())
                    },
                    size = DpSize(height = 100.dp, width = 100.dp),
                    cornerRadius = 50.dp,
                    elevation = 2.dp
                )
            } else Modifier
        )
        .clipToBounds()
        .zoomable(
            zoomState = zoomState,
            zoomEnabled = (stylusOnlyDraw || globalTouchPointersCount.intValue >= 2 || panEnabled),
            enableOneFingerZoom = stylusOnlyDraw || panEnabled,
            onDoubleTap = { pos ->
                if (stylusOnlyDraw || panEnabled) zoomState.defaultZoomOnDoubleTap(pos)
            }
        )
}

fun Modifier.pointerDrawHandler(
    globalTouchPointersCount: MutableIntState,
    onReceiveMotionEvent: (MotionEvent) -> Unit,
    onInvalidate: () -> Unit,
    onUpdateCurrentDrawPosition: (Offset) -> Unit,
    onUpdateDrawDownPosition: (Offset) -> Unit,
    enabled: Boolean,
    stylusOnlyDraw: Boolean = false,
    onCancelDraw: () -> Unit = {}
) = if (enabled) {
    this.composed {
        val receiveMotionEvent by rememberUpdatedState(onReceiveMotionEvent)
        val invalidate by rememberUpdatedState(onInvalidate)
        val updateCurrentDrawPosition by rememberUpdatedState(onUpdateCurrentDrawPosition)
        val updateDrawDownPosition by rememberUpdatedState(onUpdateDrawDownPosition)
        val cancelDraw by rememberUpdatedState(onCancelDraw)

        var drawStartedWithOnePointer by remember {
            mutableStateOf(false)
        }

        if (stylusOnlyDraw) Modifier.pointerInput(stylusOnlyDraw) {
            val session = StylusInputSession()
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val update = session.update(event.changes.map { pointer ->
                            StylusPointer(
                                id = pointer.id.value,
                                pressed = pointer.pressed,
                                previouslyPressed = pointer.previousPressed,
                                isStylus = pointer.type == PointerType.Stylus ||
                                        pointer.type == PointerType.Eraser,
                                consumed = pointer.isConsumed
                            )
                        })
                        val pointer = event.changes.firstOrNull { it.id.value == update.pointerId }

                        when (update.action) {
                            StylusAction.Down -> pointer?.let {
                                updateDrawDownPosition(it.position)
                                updateCurrentDrawPosition(it.position)
                                receiveMotionEvent(MotionEvent.Down)
                                invalidate()
                            }

                            StylusAction.Move -> pointer?.let {
                                if (it.position != it.previousPosition) {
                                    updateCurrentDrawPosition(it.position)
                                    receiveMotionEvent(MotionEvent.Move)
                                    invalidate()
                                }
                            }

                            StylusAction.Up -> pointer?.let {
                                updateCurrentDrawPosition(it.position)
                                receiveMotionEvent(MotionEvent.Up)
                                invalidate()
                            }

                            StylusAction.Cancel -> {
                                cancelDraw()
                                invalidate()
                            }

                            StylusAction.Idle -> Unit
                        }

                        if (update.consumeEvent) {
                            // Keep a resting palm out of the parent transform detector too.
                            event.changes.forEach { it.consume() }
                        } else {
                            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
                            if (finalEvent.changes.any { it.pressed } &&
                                finalEvent.changes.any { it.isConsumed }
                            ) {
                                session.navigationStarted()
                            }
                        }
                    }
                }
            } finally {
                if (session.hasActiveStroke) {
                    cancelDraw()
                    invalidate()
                }
            }
        } else Modifier.pointerMotionEvents(
            onDown = { pointerInputChange ->
                drawStartedWithOnePointer = globalTouchPointersCount.intValue <= 1

                if (drawStartedWithOnePointer) {
                    onReceiveMotionEvent(MotionEvent.Down)
                    onUpdateCurrentDrawPosition(pointerInputChange.position)
                    onUpdateDrawDownPosition(pointerInputChange.position)
                    pointerInputChange.consume()
                    onInvalidate()
                }
            },
            onMove = { pointerInputChange ->
                if (drawStartedWithOnePointer) {
                    onReceiveMotionEvent(MotionEvent.Move)
                    onUpdateCurrentDrawPosition(pointerInputChange.position)
                    pointerInputChange.consume()
                    onInvalidate()
                }
            },
            onUp = { pointerInputChange ->
                if (drawStartedWithOnePointer) {
                    onReceiveMotionEvent(MotionEvent.Up)
                    onUpdateCurrentDrawPosition(pointerInputChange.position)
                    pointerInputChange.consume()
                    onInvalidate()
                }
                drawStartedWithOnePointer = false
            },
            delayAfterDownInMillis = smartDelayAfterDownInMillis(globalTouchPointersCount.intValue)
        )
    }
} else {
    this
}
