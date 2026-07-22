/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.expr.impl.deghost;

/**
 * The offset of a reflected disk copy relative to the disk centre, in polar form.
 */
public record GhostShift(double amount, double directionRad) {

    /**
     * Radius of the rim of the shifted disk copy along the given azimuth, from the disk centre.
     */
    public double rimAt(int azimuthBin, double radius) {
        var delta = PolarGrid.azimuthAt(azimuthBin) - directionRad;
        var under = radius * radius - amount * amount * Math.sin(delta) * Math.sin(delta);
        if (under <= 0) {
            return 0;
        }
        return amount * Math.cos(delta) + Math.sqrt(under);
    }
}
