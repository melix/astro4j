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
package me.champeau.a4j.jsolex.processing.sun.detection;

public record RedshiftArea(
    String id,
    int pixelShift,
    int relPixelShift,
    double kmPerSec,
    int x1,
    int y1,
    int x2,
    int y2,
    int maxX,
    int maxY
) {
    public RedshiftArea {
        if (x1 > x2) {
            var tmp = x1;
            x1 = x2;
            x2 = tmp;
        }
        if (y1 > y2) {
            var tmp = y1;
            y1 = y2;
            y2 = tmp;
        }
    }

    public int width() {
        return x2 - x1;
    }

    public int height() {
        return y2 - y1;
    }

    public double size() {
        return Math.max(width(), height());
    }

    public int area() {
        return width() * height();
    }
}
