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

import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record TransformationHistory(
    List<String> transforms
) {
    public TransformationHistory(List<String> transforms) {
        this.transforms = Collections.unmodifiableList(transforms);
    }

    public TransformationHistory() {
        this(Collections.emptyList());
    }

    public TransformationHistory transform(String transform) {
        List<String> allTransforms = new ArrayList<>(transforms);
        allTransforms.add(transform);
        return new TransformationHistory(Collections.unmodifiableList(allTransforms));
    }

    public static ImageWrapper recordTransform(ImageWrapper image, String transform) {
        var history = (TransformationHistory) image.metadata().computeIfAbsent(
            TransformationHistory.class,
            unused -> new TransformationHistory()
        );
        image.metadata().put(TransformationHistory.class, history.transform(transform));
        return image;
    }
}
