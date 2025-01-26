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

import java.time.ZoneId;
import java.time.ZonedDateTime;

public record NOAAActiveRegion(
    String id,
    double latitudeDeg,
    double longitudeDeg
) {
    private static final double ROTATION_DAYS = 27.2753;
    private static final double ROTATION_DEGREES_PER_DAY = 360.0 / ROTATION_DAYS;
    private static final double ROTATION_DEGREES_PER_HOUR = ROTATION_DEGREES_PER_DAY / 24.0;

    // Adjusts the longitude of the active region for the given time, knowing that the Sun rotates
    // around its axis in about 27 days. This method is used to adjust the position of the active
    // region on the solar disk.
    public NOAAActiveRegion adjustForTime(ZonedDateTime time) {
        var utc = time.withZoneSameInstant(ZoneId.of("UTC"));
        // compute number of hours since midnight
        var hours = utc.getHour() + utc.getMinute() / 60.0 + utc.getSecond() / 3600.0;
        return new NOAAActiveRegion(
            id,
            latitudeDeg,
            longitudeDeg + hours * ROTATION_DEGREES_PER_HOUR
        );
    }
}
