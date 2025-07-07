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
     * in reverse order. Uses stored rotation centers from the transform history.
     *
     * @param point the point to transform
     * @param limitMarker the marker value to use
     * @return the original coordinates of the point
     */
    public Point2D determineOriginalCoordinates(Point2D point, int limitMarker) {
        var current = point;
        
        for (var i = operations.size() - 1; i >= 0; i--) {
            var operation = operations.get(i);
            if (operation.kind() == OperationKind.MARKER && operation.value() == limitMarker) {
                break;
            }
            switch (operation.kind()) {
                case ROTATION -> {
                    // Use stored rotation center - if not available, throw error
                    if (operation.values.length < 3) {
                        throw new IllegalStateException("Rotation operation missing stored center: " + java.util.Arrays.toString(operation.values));
                    }
                    Point2D opRotationCenter = new Point2D(operation.value(1), operation.value(2));
                    current = rotate(opRotationCenter, current, -operation.value());
                }
                case LEFT_ROTATION -> {
                    // Reverse of left rotation: (x, y) -> (height - y, x)
                    // where height is the height before the left rotation
                    double originalHeight = operation.value();
                    current = new Point2D(originalHeight - current.y(), current.x());
                }
                case RIGHT_ROTATION -> {
                    // Reverse of right rotation: (x, y) -> (y, width - x)
                    // where width is the width before the right rotation
                    double originalWidth = operation.value();
                    current = new Point2D(current.y(), originalWidth - current.x());
                }
                // in hflip/vflip, the value is the width/height of the image
                case HFLIP -> current = new Point2D(operation.value() - current.x(), current.y());
                case VFLIP -> current = new Point2D(current.x(), operation.value() - current.y());
                // Geometry correction transformations - reverse them
                case SCALE_X -> current = new Point2D(current.x() / operation.value(), current.y());
                case SCALE_Y -> current = new Point2D(current.x(), current.y() / operation.value());
                case SHEAR_SHIFT_COMBINED -> {
                    // Forward transformation was: nx = x - shift + y * shear
                    // Reverse: x = nx + shift - y * shear  
                    double shear = operation.value(0);
                    double shift = operation.value(1);
                    var newX = current.x() + shift - current.y() * shear;
                    current = new Point2D(newX, current.y());
                }
                case OFFSET_2D -> current = new Point2D(current.x() + operation.value(0), current.y() + operation.value(1));
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
        return addOffset2D(value, 0);
    }

    public ReferenceCoords addOffsetY(double value) {
        return addOffset2D(0, value);
    }

    public ReferenceCoords addRotation(double value) {
        if (value != 0) {
            return new ReferenceCoords(
                Stream.concat(operations.stream(), Stream.of(new Operation(OperationKind.ROTATION, value))).toList()
            );
        }
        return this;
    }

    public ReferenceCoords addRotation(double value, Point2D rotationCenter) {
        if (value != 0) {
            return new ReferenceCoords(
                Stream.concat(operations.stream(), Stream.of(new Operation(OperationKind.ROTATION, value, rotationCenter.x(), rotationCenter.y()))).toList()
            );
        }
        return this;
    }

    public ReferenceCoords addLeftRotation(double originalHeight) {
        return new ReferenceCoords(
            Stream.concat(operations.stream(), Stream.of(new Operation(OperationKind.LEFT_ROTATION, originalHeight))).toList()
        );
    }

    public ReferenceCoords addRightRotation(double originalWidth) {
        return new ReferenceCoords(
            Stream.concat(operations.stream(), Stream.of(new Operation(OperationKind.RIGHT_ROTATION, originalWidth))).toList()
        );
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


    public ReferenceCoords addScaleX(double value) {
        if (value != 1.0) {
            return new ReferenceCoords(
                Stream.concat(operations.stream(), Stream.of(new Operation(OperationKind.SCALE_X, value))).toList()
            );
        }
        return this;
    }

    public ReferenceCoords addScaleY(double value) {
        if (value != 1.0) {
            return new ReferenceCoords(
                Stream.concat(operations.stream(), Stream.of(new Operation(OperationKind.SCALE_Y, value))).toList()
            );
        }
        return this;
    }

    public ReferenceCoords addShearShiftCombined(double shear, double shift) {
        return new ReferenceCoords(
            Stream.concat(operations.stream(), Stream.of(new Operation(OperationKind.SHEAR_SHIFT_COMBINED, shear, shift))).toList()
        );
    }

    public ReferenceCoords addOffset2D(double offsetX, double offsetY) {
        if (offsetX != 0 || offsetY != 0) {
            return new ReferenceCoords(
                Stream.concat(operations.stream(), Stream.of(new Operation(OperationKind.OFFSET_2D, offsetX, offsetY))).toList()
            );
        }
        return this;
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
        double[] values
    ) {
        // Convenience constructor for single value operations
        public Operation(OperationKind kind, double value) {
            this(kind, new double[]{value});
        }
        
        // Convenience constructor for two-value operations
        public Operation(OperationKind kind, double value1, double value2) {
            this(kind, new double[]{value1, value2});
        }
        
        // Convenience constructor for three-value operations (e.g., rotation with center)
        public Operation(OperationKind kind, double value1, double value2, double value3) {
            this(kind, new double[]{value1, value2, value3});
        }
        
        // Get single value (for backward compatibility)
        public double value() {
            return values.length > 0 ? values[0] : 0;
        }
        
        // Get specific value by index
        public double value(int index) {
            return index < values.length ? values[index] : 0;
        }

        @Override
        public String toString() {
            return kind + (values.length > 0 ? " (" + java.util.Arrays.toString(values) + ")" : "");
        }
    }

    /**
     * Be careful when adding new operation kinds, as they are used in the serialization
     * with an index. If you change the order or add new ones, you may break backward compatibility.
     */
    public enum OperationKind {
        HFLIP,
        LEFT_ROTATION,
        MARKER,
        OFFSET_2D,
        RIGHT_ROTATION,
        ROTATION,
        SCALE_X,
        SCALE_Y,
        SHEAR_SHIFT_COMBINED,
        VFLIP,
    }
}
