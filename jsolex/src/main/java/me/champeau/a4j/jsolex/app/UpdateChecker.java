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

import com.google.gson.Gson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static me.champeau.a4j.jsolex.processing.util.LoggingSupport.LOGGER;

class UpdateChecker {

    private static final String RELEASES_URI = "https://api.github.com/repos/melix/astro4j/releases";
    private static final String APPLICATION_VND_GITHUB_JSON = "application/vnd.github+json";

    private UpdateChecker() {

    }

    public static Optional<ReleaseInfo> findLatestRelease() {
        long now = System.currentTimeMillis();
        var lastCheck = Paths.get(System.getProperty("user.home"), ".jsolex/update-check.txt");
        if (Files.exists(lastCheck)) {
            try {
                var timestamp = Long.parseLong(Files.readString(lastCheck));
                if (Duration.ofMillis(now - timestamp).toHours() < 12) {
                    return Optional.empty();
                }
            } catch (IOException e) {
                // ignore
            }
        }
        var releaseInfo = doFindFromRemote();
        try {
            Files.writeString(lastCheck, String.valueOf(now), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            return releaseInfo;
        }
        return releaseInfo;
    }

    private static Optional<ReleaseInfo> doFindFromRemote() {
        try {
            var body = HttpClient.newHttpClient()
                    .send(HttpRequest.newBuilder()
                            .uri(new URI(RELEASES_URI))
                            .header("X-GitHub-Api-Version", "2022-11-28")
                            .header("Accept", APPLICATION_VND_GITHUB_JSON)
                            .build(), HttpResponse.BodyHandlers.ofString())
                    .body();
            var builder = new Gson().newBuilder();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> releases = builder.create().fromJson(body, List.class);
            var release = releases.stream()
                    .filter(r -> Boolean.FALSE.equals(r.get("prerelease")))
                    .findFirst();
            if (release.isPresent()) {
                var map = release.get();
                return Optional.of(new ReleaseInfo(
                        (String) map.get("name"),
                        (String) map.get("body")
                ));
            }
            return Optional.empty();
        } catch (Exception ex) {
            LOGGER.warn("Unable to fetch release information", ex);
            return Optional.empty();
        }
    }

    record ReleaseInfo(
            String version,
            String notes
    ) {}
}
