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

internal fun drawExportFormat(
    sourceExtension: String? = null,
    suggestedFormat: ImageFormat = ImageFormat.Png.Lossless
): ImageFormat = if (sourceExtension?.lowercase()?.removePrefix(".") == "png") {
    ImageFormat.Png.Lossless
} else {
    suggestedFormat
}
