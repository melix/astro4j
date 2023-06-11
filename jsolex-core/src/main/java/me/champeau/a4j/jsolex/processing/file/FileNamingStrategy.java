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
package me.champeau.a4j.jsolex.processing.file;

import me.champeau.a4j.jsolex.processing.params.NamedPattern;
import me.champeau.a4j.ser.Header;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class FileNamingStrategy {
    public static final String DEFAULT_TEMPLATE = "%BASENAME%/%KIND%/%VIDEO_DATE%_%LABEL%";
    public static final String SAME_DIRECTORY = "%BASENAME%_%CURRENT_DATETIME%_%LABEL%";
    public static final List<NamedPattern> DEFAULTS = List.of(
            new NamedPattern(message("template.default"), DEFAULT_TEMPLATE),
            new NamedPattern(message("template.same.directory"), SAME_DIRECTORY)
    );

    private final String pattern;
    private final LocalDateTime processingDate;
    private final Header serHeader;

    public FileNamingStrategy(String pattern,
                              LocalDateTime processingDate,
                              Header serHeader) {
        this.pattern = pattern;
        this.processingDate = processingDate;
        this.serHeader = serHeader;
    }

    private Map<String, String> buildReplacementsMap(int sequenceNumber,
                                                     String imageKind,
                                                     String imageLabel,
                                                     String serFileBasename) {
        Map<String, String> replacements = new HashMap<>();
        for (Token token : Token.values()) {
            replacements.put(token.token(), switch (token) {
                case BASENAME -> serFileBasename;
                case KIND -> imageKind;
                case LABEL -> imageLabel;
                case CURRENT_DATETIME -> processingDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(':','-');
                case CURRENT_DATE -> processingDate.format(DateTimeFormatter.ISO_DATE);
                case VIDEO_DATE -> serHeader.metadata().utcDateTime().format(DateTimeFormatter.ISO_DATE);
                case VIDEO_DATETIME -> serHeader.metadata().utcDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(':','-');
                case SEQUENCE_NUMBER -> String.valueOf(sequenceNumber);
            });
        }
        return Collections.unmodifiableMap(replacements);
    }

    public String render(
            int sequenceNumber,
            String imageKind,
            String imageLabel,
            String serFileBasename

    ) {
        var replacements = buildReplacementsMap(sequenceNumber, imageKind, imageLabel, serFileBasename);
        var result = pattern;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replaceAll(Pattern.quote(entry.getKey()), entry.getValue());
        }
        return result;
    }

}
