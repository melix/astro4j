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

import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.FilesUtils;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

public interface ImageMathScriptExecutor {
    @FunctionalInterface
    interface FileOutputHandler {
        void handle(String label, FileOutputResult fileOutput);
    }

    static void render(ImageMathScriptResult result, ImageEmitter emitter) {
        render(result, emitter, null);
    }

    static void render(ImageMathScriptResult result, ImageEmitter emitter, FileOutputHandler fileHandler) {
        for (Map.Entry<String, ImageWrapper> entry : result.imagesByLabel().entrySet()) {
            var label = entry.getKey();
            var image = entry.getValue();
            if (image instanceof FileBackedImage fileBackedImage) {
                image = fileBackedImage.unwrapToMemory();
            }
            if (image instanceof ImageWrapper32 mono) {
                emitter.newMonoImage(
                    GeneratedImageKind.IMAGE_MATH,
                    null, label,
                    label,
                    null,
                    mono);
            } else if (image instanceof RGBImage rgb) {
                emitter.newColorImage(
                    GeneratedImageKind.IMAGE_MATH,
                    null, label,
                    label,
                    null,
                    rgb.width(),
                    rgb.height(),
                    rgb.metadata(),
                    () -> new float[][][]{rgb.r(), rgb.g(), rgb.b()});
            }
        }
        for (Map.Entry<String, FileOutputResult> entry : result.filesByLabel().entrySet()) {
            var label = entry.getKey();
            var fileOutput = entry.getValue();
            if (fileHandler != null) {
                fileHandler.handle(label, fileOutput);
            } else {
                emitter.newGenericFile(GeneratedImageKind.IMAGE_MATH,
                    null, label,
                    label,
                    null,
                    fileOutput.displayFile());
            }
        }
    }

    void setIncludesDir(Path includesDir);

    default ImageMathScriptResult execute(Path source, SectionKind kind) throws IOException {
        setIncludesDir(source.getParent());
        return execute(FilesUtils.readString(source), kind);
    }

    ImageMathScriptResult execute(String script, SectionKind kind);

    void removeVariable(String variable);

    <T> Optional<T> getVariable(String name);

    default <T> void putInContext(Class<T> key, Object value) {

    }

    default void putVariable(String name, Object value) {

    }

    Map<String, Object> getVariables();

    enum SectionKind {
        SINGLE,
        BATCH
    }
}
