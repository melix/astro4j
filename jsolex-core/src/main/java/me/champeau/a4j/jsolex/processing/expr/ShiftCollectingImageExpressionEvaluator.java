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
package me.champeau.a4j.jsolex.processing.expr;

import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

public class ShiftCollectingImageExpressionEvaluator extends AbstractImageExpressionEvaluator {

    private final Set<Integer> shifts = new TreeSet<>();

    private final Function<Integer, ImageWrapper32> imageFactory;

    public ShiftCollectingImageExpressionEvaluator(Function<Integer, ImageWrapper32> imageFactory) {
        this.imageFactory = imageFactory;
    }

    @Override
    public Object evaluate(String expression) {
        shifts.clear();
        return super.evaluate(expression);
    }

    protected ImageWrapper32 findImage(int shift) {
        shifts.add(shift);
        return imageFactory.apply(shift);
    }

    public Set<Integer> getShifts() {
        return Collections.unmodifiableSet(shifts);
    }

    public void clearShifts() {
        shifts.clear();
    }
}
