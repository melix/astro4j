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
    A2PX,
    ADJUST_CONTRAST,
    ADJUST_GAMMA,
    ANIM,
    ASINH_STRETCH,
    AUTO_CONTRAST,
    AUTOCROP,
    AUTOCROP2,
    AVG,
    BG_MODEL,
    MEDIAN,
    BLUR,
    CLAHE,
    CHOOSE_FILE,
    CHOOSE_FILES,
    COLORIZE,
    CONCAT,
    CONTINUUM,
    CROP,
    CROP_RECT,
    CROP_AR,
    DEDISTORT,
    DISK_FILL,
    DISK_MASK,
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
    FLAT_CORRECTION,
    GET_AT,
    GET_B,
    GET_G,
    GET_R,
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
    MONO,
    MOSAIC,
    NEUTRALIZE_BG,
    POW,
    PX2A,
    RADIUS_RESCALE,
    RANGE,
    REMOVE_BG,
    REMOTE_SCRIPTGEN,
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
    STACK_REF,
    AR_OVERLAY,
    SORT,
    VFLIP,
    FILTER,
    VIDEO_DATETIME,
    WAVELEN,
    WEIGHTED_AVG,
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
