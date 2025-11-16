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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import static java.lang.Math.PI;
import static java.lang.Math.asin;
import static java.lang.Math.atan;
import static java.lang.Math.atan2;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.tan;
import static java.lang.Math.toDegrees;
import static java.lang.Math.toRadians;

public final class SolarParametersUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(SolarParametersUtils.class);

    public static final double CARRINGTON_ROTATION_PERIOD = 27.2753;
    public static final double CARRINGTON_ROTATION_1_START = 2398167.2763889;

    private static final double I = 7.25d;
    private static final double COS_I = cos(toRadians(I));
    private static final double SIN_I = sin(toRadians(I));
    private static final double TAN_I = tan(toRadians(I));
    private static final double JULIAN_DATE_OFFSET_2 = 2396758;
    private static final double JULIAN_DATE_OFFSET_3 = 2451545;
    private static final double EPHEMERIS_DAYS = 36525d;

    private SolarParametersUtils() {
    }

    /**
     * Computes the Julian date from a local date time
     *
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
     *
     * @param julianDate the Julian date
     * @return the Carrington rotation
     */
    public static int computeCarringtonRotationNumber(double julianDate) {
        double deltaJd = julianDate - CARRINGTON_ROTATION_1_START;
        return (int) Math.floor(deltaJd / CARRINGTON_ROTATION_PERIOD) + 1;
    }

    /**
     * Computes solar parameters at a given date.
     * Formulas from Astronomical Algorithms, Jean Meeus.
     */
    public static SolarParameters computeSolarParams(LocalDateTime localDateTime) {
        return computeSolarParams(localDateTimeToJulianDate(localDateTime));
    }

    /**
     * Computes solar parameters at a given date.
     * Formulas from Astronomical Algorithms, Jean Meeus.
     */
    public static SolarParameters computeSolarParams(double julianDate) {
        return computeFullSolarParams(julianDate)
            .solarParameters();
    }

    private static FullSolarParameters computeFullSolarParams(double julianDate) {
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
        var trueAnomaly = m + c;
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

        // Compute L0 using Carrington rotation (synodic period)
        var deltaJd = julianDate - CARRINGTON_ROTATION_1_START;
        var rotations = deltaJd / CARRINGTON_ROTATION_PERIOD;
        var fractionalRotation = rotations - Math.floor(rotations);
        var l0Degrees = 360.0 * (1.0 - fractionalRotation);
        if (l0Degrees >= 360.0) {
            l0Degrees -= 360.0;
        }
        var l0 = toRadians(l0Degrees);

        var eccentricity = 0.016708634 - 0.000042037 * t - 0.0000001267 * t * t;
        var earthSunDist = 1.000001018 * (1 - eccentricity * eccentricity) / (1 + eccentricity * cos(toRadians(trueAnomaly)));
        var sunApparentSizeDegrees = (959.63 / earthSunDist) / 1800;
        return new FullSolarParameters(
            new SolarParameters(
                computeCarringtonRotationNumber(julianDate),
                b0,
                l0,
                p,
                toRadians(sunApparentSizeDegrees)),
            apparentLong,
            meanObliquity
        );
    }

    /**
     * Computes the parallactic angle for a given observation date and observer's latitude.
     *
     * @param observationDate The date and time of observation.
     * @param observerLatitude The latitude of the observer in degrees.
     * @param observerLongitude The longitude of the observer in degrees.
     * @return The parallactic angle in radians.
     */
    public static double computeParallacticAngleRad(ZonedDateTime observationDate, double observerLatitude, double observerLongitude) {
        double julianDate = localDateTimeToJulianDate(observationDate.toLocalDateTime());
        var fullSolarParams = computeFullSolarParams(julianDate);

        // Compute the Sun's right ascension (RA) and declination (Dec)
        double apparentLongitude = toRadians(fullSolarParams.apparentLongitude());
        double obliquity = toRadians(fullSolarParams.meanObliquity());

        double ra = atan2(sin(apparentLongitude) * cos(obliquity), cos(apparentLongitude));
        double dec = asin(sin(apparentLongitude) * sin(obliquity));

        // Compute the hour angle (H)
        double greenwichSiderealTime = computeGreenwichSiderealTime(julianDate);
        double localSiderealTime = greenwichSiderealTime + observerLongitude;
        double hourAngleRad = toRadians(localSiderealTime - toDegrees(ra));

        // Compute the parallactic angle
        double latitudeRad = toRadians(observerLatitude);
        return atan2(sin(hourAngleRad), tan(latitudeRad) * cos(dec) - sin(dec) * cos(hourAngleRad));
    }

    /**
     * Computes the Greenwich Sidereal Time (GST) for a given Julian date.
     *
     * @param julianDate The Julian date.
     * @return The Greenwich Sidereal Time in degrees.
     */
    private static double computeGreenwichSiderealTime(double julianDate) {
        double t = (julianDate - 2451545.0) / 36525.0;
        double gst = 280.46061837 + 360.98564736629 * (julianDate - 2451545.0) + 0.000387933 * t * t - t * t * t / 38710000.0;
        return gst % 360.0;
    }

    /**
     * Makes sure that an angle is positive and within the [0; 2*PI] range
     *
     * @param angle the angle
     * @return the same angle but as a positive value
     */
    private static double toPositiveAngle(double angle) {
        double positiveAngle = angle % (2 * PI);
        return positiveAngle >= 0 ? positiveAngle : (positiveAngle + 2 * PI);
    }

    /**
     * Record to store the result of the parallactic angle computation.
     */
    public record ParallacticAngleResult(double parallacticAngle) {
    }

    private record FullSolarParameters(
        SolarParameters solarParameters,
        double apparentLongitude,
        double meanObliquity) {

    }
}
