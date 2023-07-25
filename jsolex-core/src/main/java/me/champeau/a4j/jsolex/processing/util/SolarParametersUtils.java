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

import java.time.LocalDateTime;

import static java.lang.Math.PI;
import static java.lang.Math.asin;
import static java.lang.Math.atan;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.tan;
import static java.lang.Math.toRadians;

public final class SolarParametersUtils {

    public static final double CARRINGTON_ROTATION_PERIOD = 27.2753;
    public static final int BASE_JULIAN_DATE = 2398167;

    private static final double I = 7.25d;
    private static final double COS_I = cos(toRadians(I));
    private static final double SIN_I = sin(toRadians(I));
    private static final double TAN_I = tan(toRadians(I));
    private static final int JULIAN_DATE_OFFSET_1 = 2398220;
    private static final int JULIAN_DATE_OFFSET_2 = 2396758;
    private static final int JULIAN_DATE_OFFSET_3 = 2451545;
    private static final double EPHEMERIS_DAYS = 36525d;

    private SolarParametersUtils() {

    }

    /**
     * Computes the Julian date from a local date time
     * @param dateTime the datetime
     * @return a Julian date
     */
    public static double localDateTimeToJulianDate(LocalDateTime dateTime) {
        int year = dateTime.getYear();
        int month = dateTime.getMonthValue();
        int day = dateTime.getDayOfMonth();
        int hour = dateTime.getHour();
        int minute = dateTime.getMinute();
        int second = dateTime.getSecond();
        int millisecond = dateTime.getNano() / 1_000_000;

        int a = (14 - month) / 12;
        int y = year + 4800 - a;
        int m = month + 12 * a - 3;
        int jd = day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045;

        double fractionalDay = (hour - 12) / 24.0 + minute / 1440.0 + second / 86400.0 + millisecond / 86400000.0;

        return jd + fractionalDay;
    }

    /**
     * Computes the Carrington rotation number for a particular Julian date
     * @param julianDate the Julian date
     * @return the Carrington rotation
     */
    public static int computeCarringtonRotationNumber(double julianDate) {
        double deltaJd = julianDate - BASE_JULIAN_DATE;
        return (int) Math.floor(deltaJd / CARRINGTON_ROTATION_PERIOD) + 1;
    }

    /**
     * Computes solar parameters at a given date.
     * Formulas from Astronomical Algoriths, Jean Meeus.
     */
    public static SolarParameters computeSolarParams(LocalDateTime localDateTime) {
        return computeSolarParams(localDateTimeToJulianDate(localDateTime));
    }

    /**
     * Computes solar parameters at a given date.
     * Formulas from Astronomical Algoriths, Jean Meeus.
     */
    public static SolarParameters computeSolarParams(double julianDate) {
        // Page 190
        var theta = (julianDate - JULIAN_DATE_OFFSET_1) * 360 / 25.38;
        var k = 73.6667 + 1.3958333 * (julianDate - JULIAN_DATE_OFFSET_2) / EPHEMERIS_DAYS;

        // Page 163
        var t = (julianDate - JULIAN_DATE_OFFSET_3) / EPHEMERIS_DAYS;
        // Geometric mean longitude of the Sun
        var geoMeanLong = 280.46646 + 36000.76983 * t + 0.0003032 * t * t;
        // Mean anomaly of the Sun
        var m = toRadians(357.52911 + 35999.05029 * t - 0.0001537 * t * t);
        // Page 164
        // Sun center
        var c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m)
                + (0.019993 - 0.000101 * t) * sin(2 * m)
                + 0.000289 * sin(3 * m);
        var trueLong = geoMeanLong + c;
        var apparentLong = trueLong - 0.00569 - 0.00478 * sin(toRadians(125.04 - 1934.136 * t));
        // Page 147
        var meanObliquity = 23.439291111d - 0.013004167d * t - 0.000000164d * t * t + 0.000000504d * t * t * t;
        var meanSunLong = 280.4665 + 36000.7698 * t;
        var meanMoonLong = 218.3165 + 481267.8813 * t;
        var longOfAscending = toRadians(125.04452 - 1934.136261 * t + 0.0020708 * t * t + t * t * t / 450000);
        var nutationObliquity = 0.002555556 * cos(longOfAscending)
                                + 0.000158333 * cos(2 * toRadians(meanSunLong))
                                + 0.000027778 * cos(2 * toRadians(meanMoonLong))
                                - 0.000025 * cos(2 * longOfAscending);

        // Page 190
        var obliquity = toRadians(meanObliquity + nutationObliquity);
        var correctedApparentLong = toRadians(apparentLong + nutationObliquity);
        var x = atan(-cos(correctedApparentLong) * tan(obliquity));
        var alk = toPositiveAngle(toRadians(apparentLong - k));
        var y = atan(-cos(alk) * TAN_I);
        var p = x + y;
        var b0 = asin(sin(alk) * SIN_I);
        var n = atan(tan(alk) * COS_I);
        var nInSameQuadrantAsAlk = (alk + Math.PI) % (2 * Math.PI);
        if (Math.abs(nInSameQuadrantAsAlk - n) >= Math.PI / 2) {
            n += Math.PI;
        }
        var l0 = toPositiveAngle(n - toRadians(theta % 360d));

        return new SolarParameters(
                computeCarringtonRotationNumber(julianDate),
                b0,
                l0,
                p
        );
    }

    /**
     * Makes sure that an angle is positive and within the [0; 2*PI] range
     * @param angle the angle
     * @return the same angle but as a positive value
     */
    private static double toPositiveAngle(double angle) {
        double positiveAngle = angle % (2 * PI);
        return positiveAngle >= 0 ? positiveAngle : (positiveAngle + 2 * PI);
    }

}
