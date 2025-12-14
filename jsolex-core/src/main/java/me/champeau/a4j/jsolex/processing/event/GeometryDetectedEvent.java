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
package me.champeau.a4j.jsolex.processing.event;

/**
 * Event fired when geometry detection completes, providing tilt angle and X/Y ratio.
 */
public final class GeometryDetectedEvent extends ProcessingEvent<GeometryDetectedEvent.Geometry> {

    public GeometryDetectedEvent(Geometry payload) {
        super(payload);
    }

    public static GeometryDetectedEvent of(double tiltDegrees, double xyRatio) {
        return new GeometryDetectedEvent(new Geometry(tiltDegrees, xyRatio));
    }

    /**
     * Represents the detected geometry parameters.
     *
     * @param tiltDegrees the tilt angle in degrees
     * @param xyRatio the X/Y ratio
     */
    public record Geometry(double tiltDegrees, double xyRatio) {
    }
}
