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
package me.champeau.a4j.jsolex.processing.color;

import java.util.List;

public class KnownCurves {
    private KnownCurves() {

    }

    public static final ColorCurve H_ALPHA = new ColorCurve(
            "H-alpha",
            84, 139,
            95, 20,
            218, 65
    );

    public static final ColorCurve CALCIUM = new ColorCurve(
            "Calcium",
            147, 102,
            180, 81,
            100, 75
    );

    public static final ColorCurve HELIUM = new ColorCurve(
            "Helium",
            81, 152,
            91, 140,
            175, 88
    );

    public static List<ColorCurve> all() {
        return List.of(H_ALPHA, CALCIUM, HELIUM);
    }
}
