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

import me.champeau.a4j.jsolex.processing.params.SpectralRay;

/**
 * Event fired when automatic spectral line detection completes.
 * This allows listeners to track which spectral lines were detected
 * for each file, particularly useful in batch processing to collect
 * automatic scripts associated with detected lines.
 */
public final class SpectralLineDetectedEvent extends ProcessingEvent<SpectralLineDetectedEvent.DetectedLine> {
    
    public SpectralLineDetectedEvent(DetectedLine payload) {
        super(payload);
    }
    
    public static SpectralLineDetectedEvent of(SpectralRay detectedLine) {
        return new SpectralLineDetectedEvent(new DetectedLine(detectedLine));
    }
    
    /**
     * Represents a detected spectral line.
     * 
     * @param spectralRay the detected spectral ray
     */
    public record DetectedLine(SpectralRay spectralRay) {
    }
}