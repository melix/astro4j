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
package me.champeau.a4j.jsolex.expr;

import java.util.Locale;

public enum BuiltinFunction {
    ADJUST_CONTRAST,
    ADJUST_GAMMA,
    ANIM,
    ASINH_STRETCH,
    AUTO_CONTRAST,
    AUTOCROP,
    AUTOCROP2,
    AVG,
    MEDIAN,
    BLUR,
    CLAHE,
    CHOOSE_FILE,
    CHOOSE_FILES,
    COLORIZE,
    CONTINUUM,
    CROP,
    CROP_RECT,
    DISK_FILL,
    DRAW_ARROW,
    DRAW_CIRCLE,
    DRAW_RECT,
    DRAW_GLOBE,
    DRAW_OBS_DETAILS,
    DRAW_SOLAR_PARAMS,
    DRAW_TEXT,
    DRAW_EARTH,
    ELLIPSE_FIT,
    EXP,
    FIND_SHIFT,
    FIX_BANDING,
    FIX_GEOMETRY,
    HFLIP,
    IMG,
    INVERT,
    LINEAR_STRETCH,
    LIST,
    LOAD,
    LOAD_MANY,
    LOG,
    MAX,
    MIN,
    MOSAIC,
    NEUTRALIZE_BG,
    POW,
    RADIUS_RESCALE,
    RANGE,
    REMOVE_BG,
    RESCALE_ABS,
    RESCALE_REL,
    RL_DECON,
    RGB,
    ROTATE_LEFT,
    ROTATE_RIGHT,
    ROTATE_DEG,
    ROTATE_RAD,
    SATURATE,
    SHARPEN,
    STACK,
    SORT,
    VFLIP,
    FILTER,
    VIDEO_DATETIME,
    WORKDIR(true);

    private final boolean hasSideEffect;

    BuiltinFunction(boolean hasSideEffect) {
        this.hasSideEffect = hasSideEffect;
    }

    BuiltinFunction() {
        this(false);
    }

    public String lowerCaseName() {
        return name().toLowerCase(Locale.US);
    }

    static BuiltinFunction of(String value) {
        return valueOf(value.toUpperCase(Locale.US));
    }

    public boolean hasSideEffect() {
        return hasSideEffect;
    }
}
