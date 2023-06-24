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
package me.champeau.a4j.jsolex.processing.params;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public abstract class ImageMathProfileIO {

    public static final String EXPRESSION = "expression=";
    public static final String OUTPUT = "output=";

    private ImageMathProfileIO() {

    }

    public static void export(ImageMathParams params, Path outputFile) throws IOException {
        Files.createDirectories(outputFile.getParent());
        var lines = new ArrayList<String>();
        var expressions = params.expressions();
        var imagesToGenerate = params.imagesToGenerate();
        for (Map.Entry<String, String> entry : expressions.entrySet()) {
            var label = entry.getKey();
            lines.add("[" + label + "]");
            lines.add(EXPRESSION + entry.getValue());
            lines.add(OUTPUT + imagesToGenerate.contains(label));
        }
        Files.write(outputFile, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }


    public static ImageMathParams importFrom(Path inputFile) throws IOException {
        var lines = Files.readAllLines(inputFile, StandardCharsets.UTF_8);
        var imagesToGenerate = new HashSet<String>();
        var expressions = new HashMap<String, String>();
        String label = null;
        String expression = null;
        Boolean output = null;
        for (String line : lines) {
            if (line.startsWith("[") && line.endsWith("]")) {
                label = line.substring(1, line.length() - 1);
            } else if (label != null && line.startsWith(EXPRESSION)) {
                expression = line.substring(EXPRESSION.length());
            } else if (label != null && line.startsWith(OUTPUT)) {
                output = Boolean.parseBoolean(line.substring(OUTPUT.length()));
            }
            if (label != null && expression != null && output != null) {
                if (Boolean.TRUE.equals(output)) {
                    imagesToGenerate.add(label);
                }
                expressions.put(label, expression);
            }
        }
        return new ImageMathParams(expressions, imagesToGenerate);

    }
}
