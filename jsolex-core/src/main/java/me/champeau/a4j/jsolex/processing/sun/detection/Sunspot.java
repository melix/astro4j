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

/**
 * Represents a sunspot, which is a group of points, guaranteed to be sorted by x and y.
 * @param points points belonging to the sunspot
 */
public record Sunspot(
    List<Point2D> points,
    Point2D topLeft,
    Point2D bottomRight
) {

    private static final Comparator<Point2D> POINTS_COMPARATOR = Comparator.comparingDouble(Point2D::x).thenComparingDouble(Point2D::y);

    public static Sunspot of(List<Point2D> points) {
        var sortedPoints = points.stream()
            .sorted(POINTS_COMPARATOR)
            .toList();
        // compute bounding box
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double maxY = Double.MIN_VALUE;
        for (var point : sortedPoints) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
        }
        var topLeft = new Point2D(minX, minY);
        var bottomRight = new Point2D(maxX, maxY);
        return new Sunspot(sortedPoints, topLeft, bottomRight);
    }

    public Point2D topLeft() {
        return topLeft;
    }

    public Point2D bottomRight() {
        return bottomRight;
    }

    public double width() {
        return bottomRight().x() - topLeft().x();
    }

    public double height() {
        return bottomRight().y() - topLeft().y();
    }

    public double area() {
        return width() * height();
    }
}
