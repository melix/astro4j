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
package me.champeau.a4j.jsolex.processing.params;

import me.champeau.a4j.math.tuples.DoublePair;

import java.time.ZonedDateTime;

public record ObservationDetails(
        String observer,
        String email,
        String instrument,
        String telescope,
        Integer focalLength,
        Integer aperture,
        DoublePair coordinates,
        ZonedDateTime date,
        String camera
) {
    public ObservationDetails withObserver(String observer) {
        return new ObservationDetails(observer, email, instrument, telescope, focalLength, aperture, coordinates, date, camera);
    }

    public ObservationDetails withEmail(String email) {
        return new ObservationDetails(observer, email, instrument, telescope, focalLength, aperture, coordinates, date, camera);
    }

    public ObservationDetails withInstrument(String instrument) {
        return new ObservationDetails(observer, email, instrument, telescope, focalLength, aperture, coordinates, date, camera);
    }

    public ObservationDetails withTelescope(String telescope) {
        return new ObservationDetails(observer, email, instrument, telescope, focalLength, aperture, coordinates, date, camera);
    }

    public ObservationDetails withFocalLength(Integer focalLength) {
        return new ObservationDetails(observer, email, instrument, telescope, focalLength, aperture, coordinates, date, camera);
    }

    public ObservationDetails withAperture(Integer aperture) {
        return new ObservationDetails(observer, email, instrument, telescope, focalLength, aperture, coordinates, date, camera);
    }

    public ObservationDetails withCoordinates(DoublePair coordinates) {
        return new ObservationDetails(observer, email, instrument, telescope, focalLength, aperture, coordinates, date, camera);
    }

    public ObservationDetails withDate(ZonedDateTime date) {
        return new ObservationDetails(observer, email, instrument, telescope, focalLength, aperture, coordinates, date, camera);
    }

    public ObservationDetails withCamera(String camera) {
        return new ObservationDetails(observer, email, instrument, telescope, focalLength, aperture, coordinates, date, camera);
    }
}
