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
package me.champeau.a4j.jsolex.processing.expr.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class DirectoryListingParser {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirectoryListingParser.class);

    private static final Pattern HREF_PATTERN = Pattern.compile("href=[\"']([^\"']*\\.(math|zip))[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern A_TAG_PATTERN = Pattern.compile("<a[^>]*>([^<]*\\.(math|zip))</a>", Pattern.CASE_INSENSITIVE);

    public List<String> parseHtmlListing(String html) {
        var scriptFiles = new ArrayList<String>();

        var hrefMatcher = HREF_PATTERN.matcher(html);
        while (hrefMatcher.find()) {
            var filename = hrefMatcher.group(1);
            if (!scriptFiles.contains(filename) && !filename.contains("/")) {
                scriptFiles.add(filename);
            }
        }

        if (scriptFiles.isEmpty()) {
            var aTagMatcher = A_TAG_PATTERN.matcher(html);
            while (aTagMatcher.find()) {
                var filename = aTagMatcher.group(1);
                if (!scriptFiles.contains(filename) && !filename.contains("/")) {
                    scriptFiles.add(filename);
                }
            }
        }

        LOGGER.debug(message("repository.directory.listing.found"), scriptFiles.size());
        return scriptFiles;
    }

    public List<String> parseScriptsTxt(String content) {
        var scriptFiles = new ArrayList<String>();
        var lines = content.split("\\r?\\n");

        for (var line : lines) {
            var trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.endsWith(".math") || trimmed.endsWith(".zip")) {
                scriptFiles.add(trimmed);
            }
        }

        LOGGER.debug(message("repository.scripts.txt.parsed"), scriptFiles.size());
        return scriptFiles;
    }
}
