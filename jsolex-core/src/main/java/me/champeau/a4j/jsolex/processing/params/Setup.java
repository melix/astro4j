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

public record Setup(
    String label,
    String telescope,
    Integer focalLength,
    Integer aperture,
    Integer stop,
    String energyRejectionFilter,
    String camera,
    Double pixelSize,
    Double latitude,
    Double longitude,
    String mount,
    boolean forceCamera,
    boolean showCoordinatesInDetails,
    boolean altAzMode
) {
    @Override
    public String toString() {
        return label;
    }
}
