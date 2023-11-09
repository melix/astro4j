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
package me.champeau.a4j.jsolex.app.listeners;

import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageMetadata;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.math.regression.Ellipse;

import java.util.Comparator;
import java.util.List;

public class MetadataComparator implements Comparator<Class<?>> {
    private static final List<Class<?>> EXPLICIT_ORDER = List.of(
        GeneratedImageMetadata.class,
        PixelShift.class,
        TransformationHistory.class,
        Ellipse.class,
        SolarParameters.class
    );

    @Override
    public int compare(Class<?> o1, Class<?> o2) {
        if (o1 == o2) {
            return 0;
        }
        for (Class<?> clazz : EXPLICIT_ORDER) {
            if (clazz == o1) {
                return -1;
            }
            if (clazz == o2) {
                return 1;
            }
        }
        return o1.getSimpleName().compareTo(o2.getSimpleName());
    }
}
