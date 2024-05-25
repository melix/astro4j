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
package me.champeau.a4j.jsolex.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class GONG {
    private static final Logger LOGGER = LoggerFactory.getLogger(GONG.class);
    private static final Pattern LINK = Pattern.compile("a href=\"([^\"]*)\"");
    private static final Pattern FILENAME = Pattern.compile("(\\d{4})(\\d{2})(\\d{2})(\\d{2})(\\d{2})(\\d{2})[a-zA-Z]+.jpg");

    public static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
        .appendLiteral("UT: ")
        .append(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
        .toFormatter(Locale.US);

    private GONG() {
    }

    public static Optional<URL> fetchGongImage(ZonedDateTime date) {
        var yearmo = String.format("%04d%02d", date.getYear(), date.getMonthValue());
        var yearmoday = String.format("%04d%02d%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        try {
            var baseUrl = new URI(String.format("https://gong2.nso.edu/ftp/HA/hav/%s/%s/", yearmo, yearmoday)).toURL();
            var availableLinks = new ArrayList<String>();
            try (var reader = new BufferedReader(new InputStreamReader(baseUrl.openStream()));) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // find all target links (a href="...")
                    var matcher = LINK.matcher(line);
                    while (matcher.find()) {
                        var link = matcher.group(1);
                        if (link.endsWith(".jpg")) {
                            availableLinks.add(link);
                        }
                    }
                }
            }
            // links match a date pattern: 20240525000002Bh.jpg
            // and we want to find the closest one to the date we're looking for
            var closest = availableLinks.stream()
                .map(link -> {
                    var parts = FILENAME.matcher(link);
                    if (parts.find()) {
                        var linkDate = ZonedDateTime.of(
                            Integer.parseInt(parts.group(1)),
                            Integer.parseInt(parts.group(2)),
                            Integer.parseInt(parts.group(3)),
                            Integer.parseInt(parts.group(4)),
                            Integer.parseInt(parts.group(5)),
                            Integer.parseInt(parts.group(6)),
                            0,
                            ZoneId.of("UTC"));
                        return new Object() {
                            final String target = link;
                            final ZonedDateTime date = linkDate;
                        };
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .min(Comparator.comparingLong(l -> Math.abs(l.date.toEpochSecond() - date.toEpochSecond())));
            if (closest.isPresent()) {
                return Optional.of(new URI(baseUrl + closest.get().target).toURL());
            }
        } catch (URISyntaxException | IOException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
