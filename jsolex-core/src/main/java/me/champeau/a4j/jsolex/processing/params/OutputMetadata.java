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

import java.util.Map;

public record OutputMetadata(
        String name,
        Map<String, String> title,
        Map<String, String> description
) {
    public OutputMetadata {
        title = title != null ? Map.copyOf(title) : Map.of();
        description = description != null ? Map.copyOf(description) : Map.of();
    }

    public String getDisplayTitle(String language) {
        var result = title.get(language);
        if (result != null) {
            return result;
        }

        result = title.get("default");
        if (result != null) {
            return result;
        }

        result = title.get("en");
        if (result != null) {
            return result;
        }

        if (!title.isEmpty()) {
            return title.values().iterator().next();
        }

        return name;
    }

    public String getDisplayDescription(String language) {
        var result = description.get(language);
        if (result != null) {
            return result;
        }

        result = description.get("default");
        if (result != null) {
            return result;
        }

        result = description.get("en");
        if (result != null) {
            return result;
        }

        if (!description.isEmpty()) {
            return description.values().iterator().next();
        }

        return null;
    }
}
