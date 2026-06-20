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
package me.champeau.a4j.jsolex.app.jfx;

/**
 * Remembers the last rectangular selection made by the user so that the same region can be
 * reproduced on another image. The memory is only reused when the target image has the same
 * dimensions as the one the selection was originally drawn on.
 */
public final class RectangleSelectionMemory {

    /**
     * A remembered selection region, in image-space pixels, together with the dimensions of the
     * image it was drawn on.
     */
    public record Region(double x, double y, double width, double height, double imageWidth, double imageHeight) {
        public boolean matches(double targetWidth, double targetHeight) {
            return imageWidth == targetWidth && imageHeight == targetHeight;
        }
    }

    private Region last;

    public void remember(Region region) {
        this.last = region;
    }

    /**
     * Returns the remembered region if it was drawn on an image of the given dimensions, or
     * {@code null} otherwise.
     */
    public Region recall(double imageWidth, double imageHeight) {
        return last != null && last.matches(imageWidth, imageHeight) ? last : null;
    }
}
