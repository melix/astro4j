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

import java.util.ArrayList;
import java.util.List;

/**
 * A stretching step: a kind of stretch and the values of its parameters, in the
 * order of {@link StretchStepKind#parameters()}.
 *
 * @param kind the kind of stretch
 * @param values the parameter values
 */
public record StretchStep(StretchStepKind kind, List<Double> values) {
    public StretchStep {
        values = List.copyOf(values);
        if (values.size() != kind.parameters().size()) {
            throw new IllegalArgumentException("Expected " + kind.parameters().size() + " values for " + kind + " but got " + values.size());
        }
    }

    /**
     * Creates a step from its parameter values.
     *
     * @param kind the kind of stretch
     * @param values the parameter values
     * @return the step
     */
    public static StretchStep of(StretchStepKind kind, double... values) {
        var list = new ArrayList<Double>(values.length);
        for (var value : values) {
            list.add(value);
        }
        return new StretchStep(kind, list);
    }

    /**
     * Creates a step with the default values of its parameters.
     *
     * @param kind the kind of stretch
     * @return the step
     */
    public static StretchStep defaultsOf(StretchStepKind kind) {
        return new StretchStep(kind, kind.parameters().stream().map(StretchParameter::defaultValue).toList());
    }

    /**
     * Returns the value of a parameter.
     *
     * @param name the parameter name
     * @return the value
     */
    public double value(String name) {
        return values.get(kind.indexOf(name));
    }

    /**
     * Returns a copy of this step with a new value for a parameter. The value is
     * brought into the range of the parameter.
     *
     * @param name the parameter name
     * @param value the new value
     * @return the updated step
     */
    public StretchStep withValue(String name, double value) {
        var index = kind.indexOf(name);
        var newValues = new ArrayList<>(values);
        newValues.set(index, kind.parameters().get(index).normalize(value));
        return new StretchStep(kind, newValues);
    }

    /**
     * Creates the stretching strategy which implements this step.
     *
     * @return the strategy
     */
    public StretchingStrategy toStrategy() {
        return kind.toStrategy(this);
    }

    /**
     * Returns a description of this step, as recorded in the transformation history.
     *
     * @return the description
     */
    public String describe() {
        return kind.describe(this);
    }

    /**
     * Returns true if this step needs the solar disk to be detected in the image.
     *
     * @return true if the solar disk is required
     */
    public boolean requiresSolarDisk() {
        return kind.requiresSolarDisk();
    }
}
