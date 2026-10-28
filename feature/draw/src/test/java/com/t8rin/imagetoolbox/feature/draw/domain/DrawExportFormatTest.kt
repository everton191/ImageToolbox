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

package com.t8rin.imagetoolbox.feature.draw.domain

import com.t8rin.imagetoolbox.core.domain.image.model.ImageFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawExportFormatTest {
    @Test
    fun newCanvasUsesLosslessPng() {
        assertEquals(ImageFormat.Png.Lossless, drawExportFormat())
    }

    @Test
    fun importedPngKeepsLosslessAlphaDespiteGlobalLossyPreference() {
        for (extension in listOf("png", "PNG", ".png")) {
            for (preferred in listOf(ImageFormat.Jpg, ImageFormat.Png.Lossy)) {
                assertEquals(
                    ImageFormat.Png.Lossless,
                    drawExportFormat(extension, preferred)
                )
            }
        }
    }

    @Test
    fun otherImportsKeepExistingFormatPreference() {
        assertEquals(ImageFormat.Jpg, drawExportFormat("jpg", ImageFormat.Jpg))
        assertEquals(ImageFormat.Webp.Lossless, drawExportFormat("webp", ImageFormat.Webp.Lossless))
        assertEquals(ImageFormat.Jpg, drawExportFormat(null, ImageFormat.Jpg))
    }
}
