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
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.ser.Header;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class FileNamingStrategy {
    public static final String DEFAULT_TEMPLATE = "%BASENAME%/%KIND%/%VIDEO_DATE%_%LABEL%";
    public static final String SAME_DIRECTORY = "%BASENAME%_%CURRENT_DATETIME%_%LABEL%";
    public static final String BATCH_TEMPLATE = "batch/%CURRENT_DATETIME%/%CATEGORY%/%SEQUENCE_NUMBER%_%BASENAME%_%LABEL%";
    public static final String DEFAULT_DATE_FORMAT = "yyyyMMdd";
    public static final String DEFAULT_DATETIME_FORMAT = "yyyy-MM-dd'T'HHmmss";
    public static final List<NamedPattern> DEFAULTS = List.of(
            new NamedPattern(message("template.default"), DEFAULT_TEMPLATE, DEFAULT_DATETIME_FORMAT, DEFAULT_DATE_FORMAT),
            new NamedPattern(message("template.same.directory"), SAME_DIRECTORY, DEFAULT_DATETIME_FORMAT, DEFAULT_DATE_FORMAT),
            new NamedPattern(message("batch"), BATCH_TEMPLATE, DEFAULT_DATETIME_FORMAT, DEFAULT_DATE_FORMAT)
    );
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("^(.*?)(?:-\\d+)?$");

    private final String pattern;
    private final String dateTimeFormat;
    private final String dateFormat;
    private final LocalDateTime processingDate;
    private final Header serHeader;

    public FileNamingStrategy(String pattern,
                              String dateTimeFormat,
                              String dateFormat,
                              LocalDateTime processingDate,
                              Header serHeader) {
        this.pattern = pattern;
        this.dateTimeFormat = dateTimeFormat;
        this.dateFormat = dateFormat;
        this.processingDate = processingDate;
        this.serHeader = serHeader;
    }

    private Map<String, String> buildReplacementsMap(int sequenceNumber,
                                                     ImageWrapper image,
                                                     String imageKind,
                                                     String imageLabel,
                                                     String category,
                                                     String serFileBasename) {
        Map<String, String> replacements = new HashMap<>();
        for (Token token : Token.values()) {
            replacements.put(token.token(), switch (token) {
                case BASENAME -> serFileBasename;
                case KIND -> imageKind;
                case LABEL -> imageLabel;
                case CATEGORY -> {
                    if (category != null) {
                        yield category;
                    }
                    var m = CATEGORY_PATTERN.matcher(imageLabel);
                    if (m.find()) {
                        yield m.group(1);
                    }
                    yield imageLabel;
                }
                case CURRENT_DATETIME -> processingDate.atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern(dateTimeFormat)).replace(':','-');
                case CURRENT_DATE -> processingDate.atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern(dateFormat));
                case VIDEO_DATE -> serHeader.metadata().utcDateTime().format(DateTimeFormatter.ofPattern(dateFormat));
                case VIDEO_DATETIME -> serHeader.metadata().utcDateTime().format(DateTimeFormatter.ofPattern(dateTimeFormat)).replace(':','-');
                case SEQUENCE_NUMBER -> String.format("%04d", sequenceNumber);
                case PIXEL_SHIFT -> pixelShiftOf(image);
            });
        }
        return Collections.unmodifiableMap(replacements);
    }

    private static String pixelShiftOf(ImageWrapper image) {
        if (image != null) {
            var ps = image.findMetadata(PixelShift.class);
            if (ps.isPresent()) {
                var shift = ps.get().pixelShift();
                if ((int) shift == shift) {
                    return String.format(Locale.US, "%d", (int) shift);
                }
                return String.format(Locale.US, "%.2f", shift);
            }
        }
        return "unknown";
    }

    public String render(
        int sequenceNumber,
        String category,
        String imageKind,
        String imageLabel,
        String serFileBasename,
        ImageWrapper image
    ) {
        var replacements = buildReplacementsMap(sequenceNumber, image, imageKind, imageLabel, category, serFileBasename);
        var result = pattern;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            result = result.replaceAll(Pattern.quote(entry.getKey()), entry.getValue());
        }
        // replacement is bugfix for issue #65
        return result.replaceAll(":", "");
    }

}
