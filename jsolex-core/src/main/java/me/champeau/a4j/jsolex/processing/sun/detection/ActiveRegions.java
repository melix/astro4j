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
package me.champeau.a4j.jsolex.processing.sun.detection;

import me.champeau.a4j.math.Point2D;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * A collection of active regions.
 *
 * @param regionList the list of active regions
 */
public record ActiveRegions(
    List<ActiveRegion> regionList
) {
    /**
     * Creates a new collection of active regions, sorted by area in descending order.
     *
     * @param regionList the list of active regions
     */
    public ActiveRegions(List<ActiveRegion> regionList) {
        this.regionList = regionList.stream()
            .sorted(Comparator.comparingDouble(ActiveRegion::area).reversed())
            .toList();
    }

    /**
     * Transforms all regions using the given transformer.
     *
     * @param transformer the point transformer
     * @return the transformed regions
     */
    public ActiveRegions transform(Function<? super Point2D, Point2D> transformer) {
        return new ActiveRegions(
            regionList.stream()
                .map(s -> ActiveRegion.of(
                    s.points().stream()
                        .map(transformer)
                        .toList()
                ))
                .toList()
        );
    }

    /**
     * Translates all regions by the given offset.
     *
     * @param dx the x offset
     * @param dy the y offset
     * @return the translated regions
     */
    public ActiveRegions translate(double dx, double dy) {
        return transform(p -> p.translate(dx, dy));
    }

}
