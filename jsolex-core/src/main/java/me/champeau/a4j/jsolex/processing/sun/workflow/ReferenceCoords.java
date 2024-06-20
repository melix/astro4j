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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import me.champeau.a4j.math.Point2D;

import java.util.List;
import java.util.stream.Stream;

public record ReferenceCoords(
    List<Operation> operations
) {

    public static final int NO_LIMIT = -1;
    public static final int GEO_CORRECTION = 0;

    /**
     * Determines the original coordinates of a point, by replaying the operations
     * in reverse order.
     *
     * @param point the point to transform
     * @param rotationCenter the center of rotation
     * @param limitMarker the marker value to use
     * @return the original coordinates of the point
     */
    public Point2D determineOriginalCoordinates(Point2D point, Point2D rotationCenter, int limitMarker) {
        var current = point;
        for (var i = operations.size() - 1; i >= 0; i--) {
            var operation = operations.get(i);
            if (operation.kind() == OperationKind.MARKER && operation.value() == limitMarker) {
                break;
            }
            switch (operation.kind()) {
                case OFFSET_X -> current = new Point2D(current.x() + operation.value(), current.y());
                case OFFSET_Y -> current = new Point2D(current.x(), current.y() + operation.value());
                case ROTATION -> current = rotate(rotationCenter, current, -operation.value());
                // in hflip/vflip, the value is the width/height of the image
                case HFLIP -> current = new Point2D(operation.value() - current.x(), current.y());
                case VFLIP -> current = new Point2D(current.x(), operation.value() - current.y());
            }
        }
        return current;
    }

    private static Point2D rotate(Point2D rotationCenter, Point2D point, double angle) {
        var dx = point.x() - rotationCenter.x();
        var dy = point.y() - rotationCenter.y();
        var cos = Math.cos(angle);
        var sin = Math.sin(angle);
        return new Point2D(
            rotationCenter.x() + dx * cos - dy * sin,
            rotationCenter.y() + dx * sin + dy * cos
        );
    }

    public ReferenceCoords addOffsetX(double value) {
        if (value != 0) {
            return new ReferenceCoords(
                Stream.concat(operations.stream(), Stream.of(new Operation(OperationKind.OFFSET_X, value))).toList()
            );
        }
        return this;
    }

    public ReferenceCoords addOffsetY(double value) {
        if (value != 0) {
            return new ReferenceCoords(
                Stream.concat(operations.stream(), Stream.of(new Operation(OperationKind.OFFSET_Y, value))).toList()
            );
        }
        return this;
    }

    public ReferenceCoords addRotation(double value) {
        if (value != 0) {
            return new ReferenceCoords(
                Stream.concat(operations.stream(), Stream.of(new Operation(OperationKind.ROTATION, value))).toList()
            );
        }
        return this;
    }

    public ReferenceCoords addHFlip(double width) {
        return new ReferenceCoords(
            Stream.concat(operations.stream(), Stream.of(new Operation(OperationKind.HFLIP, width))).toList()
        );
    }

    public ReferenceCoords addVFlip(double height) {
        return new ReferenceCoords(
            Stream.concat(operations.stream(), Stream.of(new Operation(OperationKind.VFLIP, height))).toList()
        );
    }

    public ReferenceCoords geoCorrectionMarker() {
        return new ReferenceCoords(
            Stream.concat(operations.stream(), Stream.of(new Operation(OperationKind.MARKER, GEO_CORRECTION))).toList()
        );
    }

    public List<Operation> after(int marker) {
        var i = operations.indexOf(new Operation(OperationKind.MARKER, marker));
        if (i >= 0) {
            return operations.subList(i + 1, operations.size());
        }
        return List.of();
    }

    public record Operation(
        OperationKind kind,
        double value
    ) {
    }

    public enum OperationKind {
        OFFSET_X,
        OFFSET_Y,
        ROTATION,
        HFLIP,
        VFLIP,
        MARKER
    }
}
