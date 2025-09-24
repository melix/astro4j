/*
 * Copyright 2023-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.util.List;
import java.util.Optional;

public record CollageParameters(
        List<ImageSelection> images,
        int rows,
        int columns,
        int maxOutputWidth,
        int maxOutputHeight,
        boolean maintainAspectRatio,
        int padding,
        boolean downscaleIfNeeded,
        float backgroundColor,
        float backgroundColorR,
        float backgroundColorG,
        float backgroundColorB
) {

    public record ImageSelection(
            ImageWrapper image,
            String title,
            Optional<Integer> row,
            Optional<Integer> column
    ) {
    }

}