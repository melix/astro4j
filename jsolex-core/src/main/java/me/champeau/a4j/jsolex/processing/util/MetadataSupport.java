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

import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.detection.Redshifts;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageMetadata;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * A utility class to render image metadata as text.
 */
public class MetadataSupport {
    private MetadataSupport() {
    }

    public static Optional<String> render(Class<?> clazz, Object value) {
        return Optional.ofNullable(switch (value) {
            case PixelShift pixelShift -> renderPixelShift(pixelShift);
            case Ellipse ellipse -> ellipse.toString();
            case SolarParameters sp -> sp.toString();
            case ProcessParams unused -> null;
            case TransformationHistory history -> renderTransformationHistory(history);
            case GeneratedImageMetadata generatedImage -> renderGeneratedImageMetadata(generatedImage);
            case Redshifts redshifts -> renderRedshifts(redshifts);
            case ReferenceCoords coords -> renderReferenceCoords(coords);
            default -> renderToString(clazz, value);
        });
    }

    private static String renderGeneratedImageMetadata(GeneratedImageMetadata image) {
        var sb = new StringBuilder();
        sb.append(message("generated.image.metadata")).append("\n");
        sb.append("  - ").append(message("generated.image.title")).append(": ").append(image.title()).append("\n");
        sb.append("  - ").append(message("generated.image.kind")).append(": ").append(message("imagekind." + image.kind())).append("\n");
        sb.append("  - ").append(message("generated.image.path")).append(": ").append(image.name()).append("\n");
        return sb.toString();
    }

    private static String renderTransformationHistory(TransformationHistory history) {
        var sb = new StringBuilder();
        sb.append(message("transformation.history")).append("\n");
        history.transforms().forEach(t -> sb.append("  - ").append(t).append("\n"));
        return sb.toString();
    }

    public static String renderPixelShift(PixelShift value) {
        var v = value.pixelShift();
        if (Math.round(v) == v) {
            return String.format(message("pixel.shift.integer.pattern"), Math.round(v));
        }
        return String.format(message("pixel.shift.real.pattern"), v);
    }

    public static String renderRedshifts(Redshifts redshifts) {
        var sb = new StringBuilder();
        sb.append(message("redshifts.measurements")).append("\n");
        redshifts.redshifts().forEach(rs -> {
            sb.append("  - ");
            sb.append(String.format(Locale.US, "%.2f km/s", rs.kmPerSec()));
            sb.append(" (pixel shift = ").append(rs.relPixelShift()).append(")");
            sb.append(" ").append(message("at.coord")).append(" ").append(rs.maxX()).append("x").append(rs.maxY());
            sb.append("\n");
        });
        return sb.toString();
    }

    public static String renderReferenceCoords(ReferenceCoords coords) {
        var sb = new StringBuilder(message("reference.coords"));
        coords.operations().forEach(op -> {
            sb.append(op.kind());
            sb.append("[");
            sb.append(op.value());
            sb.append("] ");
        });
        return sb.toString();
    }

    private static String renderToString(Class<?> clazz, Object value) {
        return clazz.getSimpleName() + " = " + value.toString();
    }

    public static Object applyMetadata(String message, Supplier<Object> supplier) {
        var object = supplier.get();
        if (object instanceof ImageWrapper wrapper) {
            return TransformationHistory.recordTransform(wrapper, message);
        }
        if (object instanceof List<?> list) {
            return list.stream()
                    .map(o -> applyMetadata(message, () -> o))
                    .toList();
        }
        return object;
    }
}
