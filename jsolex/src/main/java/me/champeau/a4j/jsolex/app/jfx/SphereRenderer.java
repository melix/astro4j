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

import me.champeau.a4j.jsolex.app.JSolEx;

/**
 * Interface for sphere renderers used in the Spherical Tomography viewer.
 * Allows switching between different rendering implementations (shell-based vs volume).
 */
public interface SphereRenderer {

    /**
     * Color mapping modes for rendering shells.
     */
    enum ColorMap {
        /**
         * Monochrome rendering.
         */
        MONO,
        /**
         * Color gradient from red (innermost) to blue (outermost).
         */
        RED_TO_BLUE,
        /**
         * Color gradient from blue (innermost) to red (outermost).
         */
        BLUE_TO_RED;

        @Override
        public String toString() {
            return I18N.string(JSolEx.class, "spherical-tomography", "colormap." + name().toLowerCase().replace('_', '-'));
        }
    }

    /**
     * Loads all textures from the tomography data.
     */
    void loadTextures();

    /**
     * Checks if textures need to be reloaded.
     *
     * @return true if textures need reloading, false otherwise
     */
    boolean needsTextureReload();

    /**
     * Reloads all textures if they have been marked as needing reload.
     */
    void reloadTextures();

    /**
     * Renders the spherical tomography visualization.
     *
     * @param viewWidth the width of the viewport in pixels
     * @param viewHeight the height of the viewport in pixels
     */
    void render(int viewWidth, int viewHeight);

    /**
     * Sets the camera distance from the sphere.
     *
     * @param distance the camera distance
     */
    void setCameraDistance(float distance);

    /**
     * Gets the current camera distance.
     *
     * @return the camera distance
     */
    float getCameraDistance();

    /**
     * Sets the rotation angles for the sphere.
     *
     * @param x rotation around the X axis in degrees
     * @param y rotation around the Y axis in degrees
     */
    void setRotation(float x, float y);

    /**
     * Gets the rotation around the X axis.
     *
     * @return the X rotation in degrees
     */
    float getRotationX();

    /**
     * Gets the rotation around the Y axis.
     *
     * @return the Y rotation in degrees
     */
    float getRotationY();

    /**
     * Sets the radial exaggeration factor.
     *
     * @param exaggeration the exaggeration factor
     */
    void setRadialExaggeration(float exaggeration);

    /**
     * Gets the radial exaggeration factor.
     *
     * @return the radial exaggeration factor
     */
    float getRadialExaggeration();

    /**
     * Sets the color map for rendering.
     *
     * @param colorMap the color map to use
     */
    void setColorMap(ColorMap colorMap);

    /**
     * Gets the current color map.
     *
     * @return the current color map
     */
    ColorMap getColorMap();

    /**
     * Sets whether to show prominences.
     *
     * @param show true to show prominences, false to hide them
     */
    void setShowProminences(boolean show);

    /**
     * Checks if prominences are being shown.
     *
     * @return true if prominences are shown, false otherwise
     */
    boolean isShowProminences();

    /**
     * Sets whether to use contrast-enhanced images.
     *
     * @param enhanced true to use enhanced images, false to use raw images
     */
    void setContrastEnhanced(boolean enhanced);

    /**
     * Checks if contrast enhancement is enabled.
     *
     * @return true if contrast enhancement is enabled, false otherwise
     */
    boolean isContrastEnhanced();

    /**
     * Checks if contrast-enhanced data is available.
     *
     * @return true if enhanced data is available, false otherwise
     */
    boolean hasContrastEnhancement();

    /**
     * Sets the visibility of a shell.
     *
     * @param pixelShift the pixel shift identifying the shell
     * @param visible true to show the shell, false to hide it
     */
    void setShellVisible(double pixelShift, boolean visible);

    /**
     * Checks if a shell is visible.
     *
     * @param pixelShift the pixel shift identifying the shell
     * @return true if the shell is visible, false otherwise
     */
    boolean isShellVisible(double pixelShift);

    /**
     * Disposes all resources used by this renderer.
     */
    void dispose();
}
