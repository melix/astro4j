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
package me.champeau.a4j.jsolex.processing.stretching;

import java.util.List;

/**
 * Describes a parameter of a {@link StretchStep}.
 *
 * @param name the parameter name, used as a key for labels
 * @param min the minimum value
 * @param max the maximum value
 * @param defaultValue the default value
 * @param increment the smallest meaningful change of the value
 * @param choices the allowed values, or an empty list if any value in the range is allowed
 */
public record StretchParameter(
        String name,
        double min,
        double max,
        double defaultValue,
        double increment,
        List<Integer> choices
) {
    public StretchParameter {
        choices = List.copyOf(choices);
    }

    static StretchParameter range(String name, double min, double max, double defaultValue, double increment) {
        return new StretchParameter(name, min, max, defaultValue, increment, List.of());
    }

    static StretchParameter choice(String name, int defaultValue, Integer... choices) {
        var values = List.of(choices);
        return new StretchParameter(name, values.getFirst(), values.getLast(), defaultValue, 1, values);
    }

    /**
     * Returns true if this parameter only accepts integer values.
     *
     * @return true for integer parameters
     */
    public boolean isInteger() {
        return increment >= 1 && increment == Math.rint(increment);
    }

    /**
     * Brings a value into the range of this parameter, snapping it to the closest
     * choice for parameters with a list of allowed values.
     *
     * @param value the value
     * @return the normalized value
     */
    public double normalize(double value) {
        if (Double.isNaN(value)) {
            return defaultValue;
        }
        if (!choices.isEmpty()) {
            var best = choices.getFirst();
            for (var choice : choices) {
                if (Math.abs(choice - value) < Math.abs(best - value)) {
                    best = choice;
                }
            }
            return best;
        }
        var clamped = Math.clamp(value, min, max);
        return isInteger() ? Math.rint(clamped) : clamped;
    }
}
