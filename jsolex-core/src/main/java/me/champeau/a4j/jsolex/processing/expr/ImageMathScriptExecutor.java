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

import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface ImageMathScriptExecutor {
    static void render(ImageMathScriptResult result, ImageEmitter emitter) {
        for (Map.Entry<String, ImageWrapper> entry : result.imagesByLabel().entrySet()) {
            var label = entry.getKey();
            var image = entry.getValue();
            if (image instanceof ImageWrapper32 mono) {
                emitter.newMonoImage(
                        GeneratedImageKind.IMAGE_MATH,
                        label,
                        label,
                        mono,
                        LinearStrechingStrategy.DEFAULT
                );
            } else if (image instanceof ColorizedImageWrapper colorized) {
                emitter.newColorImage(
                        GeneratedImageKind.IMAGE_MATH,
                        label,
                        label,
                        LinearStrechingStrategy.DEFAULT,
                        colorized.width(),
                        colorized.height(),
                        () -> colorized.converter().apply(colorized.mono().data())
                );
            }
        }
    }

    default ImageMathScriptResult execute(Path source) throws IOException {
        return execute(FilesUtils.readString(source));
    }

    ImageMathScriptResult execute(String script);

}
