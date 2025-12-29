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

import java.time.Duration;

public final class DurationFormatter {

    private DurationFormatter() {
    }

    /**
     * Formats a Duration into a human-readable string.
     * @param duration the duration to format
     * @return a formatted string
     */
    public static String format(Duration duration) {
        long totalMillis = duration.toMillis();

        if (totalMillis < 1000) {
            return totalMillis + "ms";
        }

        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        double seconds = duration.toSecondsPart() + (duration.toMillisPart() / 1000.0);

        if (hours > 0) {
            return String.format("%dh %dmin %.1fs", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dmin %.1fs", minutes, seconds);
        } else {
            return String.format("%.2fs", seconds);
        }
    }

    public static String formatNanos(long nanos) {
        return format(Duration.ofNanos(nanos));
    }
}
