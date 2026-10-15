/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.app.jfx;

import javafx.scene.control.Label;
import javafx.scene.text.Text;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;

import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Formats a progress task path so that it fits in a given width, by replacing
 * the tasks in the middle of the path with an ellipsis. The first and the last
 * tasks are always kept.
 */
public final class ProgressPathFormatter {
    static final String ELLIPSIS = "…";

    private ProgressPathFormatter() {
    }

    /**
     * Formats the path so that it fits in the maximum width of the label, using the label font.
     *
     * @param segments the task names from the root down to the current task
     * @param label the label which displays the path
     * @return the formatted path
     */
    public static String fit(List<String> segments, Label label) {
        var text = new Text();
        text.setFont(label.getFont());
        var insets = label.getInsets();
        return fit(segments, label.getMaxWidth() - insets.getLeft() - insets.getRight(), s -> {
            text.setText(s);
            return text.getLayoutBounds().getWidth();
        });
    }

    /**
     * Formats the path so that it fits in the given width.
     *
     * @param segments the task names from the root down to the current task
     * @param maxWidth the available width
     * @param measure computes the width of a text
     * @return the formatted path
     */
    public static String fit(List<String> segments, double maxWidth, ToDoubleFunction<String> measure) {
        var full = String.join(ProgressOperation.PATH_SEPARATOR, segments);
        if (segments.size() < 3 || measure.applyAsDouble(full) <= maxWidth) {
            return full;
        }
        var shortened = full;
        for (int kept = segments.size() - 2; kept >= 1; kept--) {
            shortened = shorten(segments, kept);
            if (measure.applyAsDouble(shortened) <= maxWidth) {
                return shortened;
            }
        }
        return shortened;
    }

    private static String shorten(List<String> segments, int keptAtEnd) {
        var tail = segments.subList(segments.size() - keptAtEnd, segments.size());
        return segments.getFirst() + ProgressOperation.PATH_SEPARATOR + ELLIPSIS + ProgressOperation.PATH_SEPARATOR
               + String.join(ProgressOperation.PATH_SEPARATOR, tail);
    }
}
