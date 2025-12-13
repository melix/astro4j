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
 * Listener for rectangular region selections on images.
 */
@FunctionalInterface
public interface RectangleSelectionListener {
    /**
     * Determines if this listener supports the given action kind.
     *
     * @param kind the action kind
     * @return true if the action kind is supported
     */
    default boolean supports(ActionKind kind) {
        return true;
    }

    /**
     * Called when a rectangular region is selected.
     *
     * @param kind the action kind for this selection
     * @param x the X coordinate of the selection
     * @param y the Y coordinate of the selection
     * @param width the width of the selection
     * @param height the height of the selection
     */
    void onSelectRegion(ActionKind kind, int x, int y, int width, int height);

    /**
     * Types of actions that can be performed on a selected region.
     */
    enum ActionKind {
        /** Create animation or panel from selected region */
        CREATE_ANIM_OR_PANEL,
        /** Crop the image to the selected region */
        CROP,
        /** Crop for ImageMath processing */
        IMAGEMATH_CROP,
        /** Extract frames from SER file */
        EXTRACT_SER_FRAMES;

        /**
         * Checks if this action kind is a crop operation.
         *
         * @return true if this is a crop action
         */
        public boolean isCrop() {
            return this == CROP || this == IMAGEMATH_CROP;
        }
    }
}
