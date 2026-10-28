/*
 * ImageToolbox is an image editor for android
 * Copyright (c) 2026 T8RIN (Malik Mukhametzyanov)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.t8rin.imagetoolbox.feature.draw.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.t8rin.imagetoolbox.core.data.image.utils.compressor.PngLosslessBackend
import com.t8rin.imagetoolbox.core.domain.image.ImageGetter
import com.t8rin.imagetoolbox.core.domain.image.model.Quality
import com.t8rin.imagetoolbox.core.domain.model.IntegerSize
import com.t8rin.imagetoolbox.core.domain.model.pt
import com.t8rin.imagetoolbox.feature.draw.domain.DrawBehavior
import com.t8rin.imagetoolbox.feature.draw.presentation.components.UiPathPaint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Proxy

@RunWith(AndroidJUnit4::class)
class StylusPngExportTest {
    @Test
    fun importedImageKeepsOriginalResolutionFreeRgbColorAndErasedAlphaInPng() = runBlocking {
        // The image exceeds the 2500 px preview limit. Strokes were entered at one-quarter size.
        val original = Bitmap.createBitmap(3072, 1024, Bitmap.Config.ARGB_8888).apply {
            setPixel(16, 16, android.graphics.Color.argb(128, 80, 160, 240))
            setPixel(3071, 1023, android.graphics.Color.MAGENTA)
        }
        var imageLoads = 0
        val getter = Proxy.newProxyInstance(
            ImageGetter::class.java.classLoader,
            arrayOf(ImageGetter::class.java)
        ) { _, method, args ->
            assertEquals("getImage", method.name)
            assertEquals("synthetic-original.png", args!![0])
            assertEquals("Export must request originalSize, not a preview size", true, args[1])
            imageLoads++
            original
        } as ImageGetter<Bitmap>
        val applier = AndroidImageDrawApplier(
            InstrumentationRegistry.getInstrumentation().targetContext,
            unusedDependency(), getter, unusedDependency()
        )
        val previewSize = IntegerSize(768, 256)
        val brushColor = 0xFF1A87D3.toInt()
        val pen = UiPathPaint(
            path = Path().apply { moveTo(128f, 128f); lineTo(640f, 128f) },
            strokeWidth = 20.pt,
            brushSoftness = 0.pt,
            drawColor = Color(brushColor),
            isErasing = false,
            canvasSize = previewSize
        )
        val eraser = pen.copy(
            path = Path().apply { moveTo(384f, 64f); lineTo(384f, 192f) },
            isErasing = true
        )
        val rendered = checkNotNull(
            applier.applyDrawToImage(
                DrawBehavior.Image(0), listOf(pen, eraser), "synthetic-original.png"
            )
        )
        try {
            assertEquals(1, imageLoads)
            assertEquals(3072, rendered.width)
            assertEquals(1024, rendered.height)
            assertEquals(brushColor, rendered.getPixel(1024, 512))
            assertEquals(0, android.graphics.Color.alpha(rendered.getPixel(1536, 512)))
            assertEquals(original.getPixel(16, 16), rendered.getPixel(16, 16))
            assertEquals(android.graphics.Color.MAGENTA, rendered.getPixel(3071, 1023))
            assertLosslessPngRoundTrip(rendered)
        } finally {
            rendered.recycle()
            original.recycle()
        }
    }

    @Test
    fun transparentCanvasKeepsRequestedDimensionsAndTransparencyInPng() = runBlocking {
        val applier = AndroidImageDrawApplier(
            InstrumentationRegistry.getInstrumentation().targetContext,
            unusedDependency(), unusedDependency(), unusedDependency()
        )
        val rendered = checkNotNull(
            applier.applyDrawToImage(
                DrawBehavior.Background(0, 31, 17, android.graphics.Color.TRANSPARENT),
                emptyList(), ""
            )
        )
        try {
            assertEquals(31, rendered.width)
            assertEquals(17, rendered.height)
            assertEquals(0, rendered.getPixel(15, 8))
            assertLosslessPngRoundTrip(rendered)
        } finally {
            rendered.recycle()
        }
    }

    private suspend fun assertLosslessPngRoundTrip(rendered: Bitmap) {
        val png = PngLosslessBackend.compress(rendered, Quality.Base())
        val decoded = checkNotNull(BitmapFactory.decodeByteArray(png, 0, png.size))
        try {
            assertEquals(rendered.width, decoded.width)
            assertEquals(rendered.height, decoded.height)
            assertTrue(decoded.hasAlpha())
            assertTrue("Lossless PNG changed drawing pixels or transparency", rendered.sameAs(decoded))
        } finally {
            decoded.recycle()
        }
    }

    private inline fun <reified T> unusedDependency(): T = Proxy.newProxyInstance(
        T::class.java.classLoader, arrayOf(T::class.java)
    ) { _, method, _ -> error("Unexpected dependency call: ${method.name}") } as T
}
