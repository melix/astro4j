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
package me.champeau.a4j.ser;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Utility class to convert timestamps from and to long values.
 * SER file spec mentions:
 *  "Each increment represents 100 nanoseconds of elapsed time since the
 * beginning of January 1 of the year 1 in the Gregorian calendar"
 */
public abstract class TimestampConverter {
    private static final LocalDateTime BASE_DATE_TIME = LocalDateTime.of(1, 1, 1, 0, 0, 0, 0);

    private TimestampConverter() {

    }

    /**
     * Converts the long value timestamp found in a SER file
     * into a local date.
     * @param timestamp the timestamp value as found in a SER file
     * @return its local date time representation
     */
    public static Optional<LocalDateTime> of(long timestamp) {
        if (timestamp <= 0) {
            return Optional.empty();
        }
        LocalDateTime localDateTime = LocalDateTime.of(1, 1, 1, 0, 0, 0, 0);
        // Looping to avoid number overflow
        for (int i = 0; i < 100; i++) {
            localDateTime = localDateTime.plusNanos(timestamp);
        }
        return Optional.of(localDateTime);
    }


    /**
     * Converts a local datetime to the timestamp format that SER file use.
     * The complexity of this algorithm is because of number overflow :
     * simply using chrono unit, nanosBetween would lead to numbers larger
     * than long.
     * @param localDateTime the date to convert
     * @return the SER timestamp
     */
    public static long toTimestamp(LocalDateTime localDateTime) {
        if (localDateTime == null || localDateTime.isBefore(BASE_DATE_TIME)) {
            return -1;
        }

        long timestamp = 0;
        LocalDateTime temp = BASE_DATE_TIME;

        long days = ChronoUnit.DAYS.between(temp, localDateTime);
        temp = temp.plusDays(days);
        timestamp += days * (24L * 60 * 60 * 10_000_000);

        long hours = ChronoUnit.HOURS.between(temp, localDateTime);
        temp = temp.plusHours(hours);
        timestamp += hours * (60L * 60 * 10_000_000);

        long minutes = ChronoUnit.MINUTES.between(temp, localDateTime);
        temp = temp.plusMinutes(minutes);
        timestamp += minutes * (60L * 10_000_000);

        long seconds = ChronoUnit.SECONDS.between(temp, localDateTime);
        temp = temp.plusSeconds(seconds);
        timestamp += seconds * 10_000_000;

        long millis = ChronoUnit.MILLIS.between(temp, localDateTime);
        temp = temp.plus(millis, ChronoUnit.MILLIS);
        timestamp += millis * 10_000;

        long micros = ChronoUnit.MICROS.between(temp, localDateTime);
        temp = temp.plusNanos(micros * 1_000);
        timestamp += micros * 10;

        while (temp.isBefore(localDateTime)) {
            temp = temp.plusNanos(100);
            timestamp++;
        }

        return timestamp;
    }


}
