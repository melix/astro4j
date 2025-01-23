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

public record Sunspots(
    List<Sunspot> sunspotList
) {
    public Sunspots(List<Sunspot> sunspotList) {
        this.sunspotList = sunspotList.stream()
            .sorted(Comparator.comparingDouble(Sunspot::area).reversed())
            .toList();
    }

    public Sunspots transform(Function<? super Point2D, Point2D> transformer) {
        return new Sunspots(
            sunspotList.stream()
                .map(s -> Sunspot.of(
                    s.points().stream()
                        .map(transformer)
                        .toList()
                ))
                .toList()
        );
    }

    public Sunspots translate(double dx, double dy) {
        return transform(p -> p.translate(dx, dy));
    }

}
