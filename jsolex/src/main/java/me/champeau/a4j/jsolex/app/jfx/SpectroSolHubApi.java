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
package me.champeau.a4j.jsolex.app.jfx;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SpectroSolHubApi {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final Gson GSON = new Gson();

    private final String baseUrl;
    private final HttpClient httpClient;

    public SpectroSolHubApi(String baseUrl) {
        this.baseUrl = baseUrl;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    }

    public List<RepositoryDetail> fetchAllRepositoryDetails() throws Exception {
        var summaries = fetchRepositories();
        List<RepositoryDetail> results = new ArrayList<>();
        for (var summary : summaries) {
            var detail = fetchRepositoryDetail(summary.owner(), summary.slug());
            results.add(detail != null ? detail : toDetail(summary));
        }
        results.sort(Comparator.comparingInt(RepositoryDetail::starCount)
            .thenComparingInt(RepositoryDetail::totalDownloads)
            .reversed());
        return results;
    }

    List<RepositorySummary> fetchRepositories() throws Exception {
        var request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/script-repositories"))
            .timeout(TIMEOUT)
            .GET()
            .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new SpectroSolHubException("HTTP " + response.statusCode());
        }
        return parseRepositoryList(response.body());
    }

    RepositoryDetail fetchRepositoryDetail(String owner, String slug) {
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/script-repositories/" + owner + "/" + slug))
                .timeout(TIMEOUT)
                .GET()
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return parseRepositoryDetail(response.body());
            }
        } catch (Exception e) {
            // fall back to summary data
        }
        return null;
    }

    public String buildScriptsUrl(String owner, String slug) {
        return baseUrl + "/scripts/" + owner + "/" + slug + "/";
    }

    static List<RepositorySummary> parseRepositoryList(String json) {
        var root = GSON.fromJson(json, JsonObject.class);
        var repos = root.getAsJsonArray("repositories");
        if (repos == null || repos.isEmpty()) {
            return Collections.emptyList();
        }
        List<RepositorySummary> result = new ArrayList<>();
        for (var element : repos) {
            var obj = element.getAsJsonObject();
            result.add(new RepositorySummary(
                getStringField(obj, "name"),
                getStringField(obj, "slug"),
                getStringField(obj, "description"),
                getStringField(obj, "owner"),
                getIntField(obj, "scriptCount"),
                getIntField(obj, "starCount")
            ));
        }
        return result;
    }

    static RepositoryDetail parseRepositoryDetail(String json) {
        var obj = GSON.fromJson(json, JsonObject.class);
        var scriptsArray = obj.has("scripts") && !obj.get("scripts").isJsonNull()
            ? obj.getAsJsonArray("scripts")
            : null;
        List<ScriptInfo> scripts = new ArrayList<>();
        if (scriptsArray != null) {
            for (var element : scriptsArray) {
                var s = element.getAsJsonObject();
                scripts.add(new ScriptInfo(
                    getStringField(s, "filename"),
                    getStringField(s, "title"),
                    getStringField(s, "author"),
                    getStringField(s, "version"),
                    getStringField(s, "description"),
                    getStringField(s, "requiresVersion"),
                    getIntField(s, "fileCount"),
                    getIntField(s, "downloadCount")
                ));
            }
        }
        return new RepositoryDetail(
            getStringField(obj, "name"),
            getStringField(obj, "slug"),
            getStringField(obj, "description"),
            getStringField(obj, "owner"),
            getIntField(obj, "starCount"),
            scripts
        );
    }

    private static RepositoryDetail toDetail(RepositorySummary summary) {
        return new RepositoryDetail(
            summary.name(),
            summary.slug(),
            summary.description(),
            summary.owner(),
            summary.starCount(),
            Collections.emptyList()
        );
    }

    static String getStringField(JsonObject obj, String field) {
        if (obj.has(field) && !obj.get(field).isJsonNull()) {
            return obj.get(field).getAsString();
        }
        return "";
    }

    static int getIntField(JsonObject obj, String field) {
        if (obj.has(field) && !obj.get(field).isJsonNull()) {
            return obj.get(field).getAsInt();
        }
        return 0;
    }

    public record RepositorySummary(
        String name,
        String slug,
        String description,
        String owner,
        int scriptCount,
        int starCount
    ) {
    }

    public record RepositoryDetail(
        String name,
        String slug,
        String description,
        String owner,
        int starCount,
        List<ScriptInfo> scripts
    ) {
        public int totalDownloads() {
            return scripts.stream().mapToInt(ScriptInfo::downloadCount).sum();
        }
    }

    public record ScriptInfo(
        String filename,
        String title,
        String author,
        String version,
        String description,
        String requiresVersion,
        int fileCount,
        int downloadCount
    ) {
    }

    public static class SpectroSolHubException extends Exception {
        public SpectroSolHubException(String message) {
            super(message);
        }
    }
}
