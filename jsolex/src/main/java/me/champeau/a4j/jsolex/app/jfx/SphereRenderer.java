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

    enum ColorMap {
        MONO,
        RED_TO_BLUE,
        BLUE_TO_RED;

        @Override
        public String toString() {
            return I18N.string(JSolEx.class, "spherical-tomography", "colormap." + name().toLowerCase().replace('_', '-'));
        }
    }

    void loadTextures();

    boolean needsTextureReload();

    void reloadTextures();

    void render(int viewWidth, int viewHeight);

    void setCameraDistance(float distance);

    float getCameraDistance();

    void setRotation(float x, float y);

    float getRotationX();

    float getRotationY();

    void setRadialExaggeration(float exaggeration);

    float getRadialExaggeration();

    void setColorMap(ColorMap colorMap);

    ColorMap getColorMap();

    void setShowProminences(boolean show);

    boolean isShowProminences();

    void setContrastEnhanced(boolean enhanced);

    boolean isContrastEnhanced();

    boolean hasContrastEnhancement();

    void setShellVisible(double pixelShift, boolean visible);

    boolean isShellVisible(double pixelShift);

    void dispose();
}
