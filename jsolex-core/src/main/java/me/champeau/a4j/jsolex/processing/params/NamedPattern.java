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

/**
 * Represents a pattern for which a name is given, for example "default"
 * or "flat directory", etc...
 *
 * @param label a label given to a pattern
 * @param pattern the pattern
 */
public record NamedPattern(String label, String pattern, String datetimeFormat, String dateFormat) {
    @Override
    public String toString() {
        return label;
    }

    public NamedPattern withLabel(String label) {
        return new NamedPattern(label, pattern, datetimeFormat, dateFormat);
    }

    public NamedPattern withPattern(String pattern) {
        return new NamedPattern(label, pattern, datetimeFormat, dateFormat);
    }

    public NamedPattern withDateFormat(String dateFormat) {
        return new NamedPattern(label, pattern, datetimeFormat, dateFormat);
    }

    public NamedPattern withDateTimeFormat(String datetimeFormat) {
        return new NamedPattern(label, pattern, datetimeFormat, dateFormat);
    }
}
