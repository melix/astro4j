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
package me.champeau.a4j.jsolex.processing.util;

import java.util.Locale;

public record DegreesArcMinutesSeconds(int degrees, int arcMinutes, double arcSeconds) {
    public DegreesArcMinutesSeconds {
        if (arcMinutes < 0 || arcMinutes >= 60) {
            throw new IllegalArgumentException("Arc minutes must be between 0 and 59");
        }
        if (arcSeconds < 0 || arcSeconds >= 60) {
            throw new IllegalArgumentException("Arc seconds must be between 0 and < 60");
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%02d°%02d'%.2f\"", degrees, arcMinutes, arcSeconds);
    }

    public String latitude() {
        String direction = degrees < 0 ? "S" : "N";
        return String.format(Locale.US, "%02d°%02d'%.2f\" %s", Math.abs(degrees), arcMinutes, arcSeconds, direction);
    }

    public String longitude() {
        String direction = degrees < 0 ? "W" : "E";
        return String.format(Locale.US, "%02d°%02d'%.2f\" %s", Math.abs(degrees), arcMinutes, arcSeconds, direction);
    }
}
