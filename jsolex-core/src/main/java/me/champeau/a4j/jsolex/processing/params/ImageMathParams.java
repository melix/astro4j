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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Map;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public record ImageMathParams(
        List<File> scriptFiles,
        Map<File, Map<String, Object>> parameterValues
) {
    public static final Logger LOGGER = LoggerFactory.getLogger(ImageMathParams.class);

    public static final ImageMathParams NONE = new ImageMathParams(
            List.of(),
            Map.of()
    );

    @Override
    public List<File> scriptFiles() {
        return scriptFiles.stream()
                .filter(f -> {
                    if (!f.exists()) {
                        LOGGER.warn(message("script.file.missing"), f);
                        return false;
                    }
                    return true;
                })
                .toList();
    }
}
