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

import me.champeau.a4j.jsolex.processing.params.ImageMathParameterExtractor;
import me.champeau.a4j.jsolex.processing.util.LocaleUtils;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;
import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

public class ScriptRepositoryManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScriptRepositoryManager.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String SCRIPTS_TXT = "scripts.txt";
    private static final String ZIP_DESCRIPTOR = "main.txt";

    private final HttpClient httpClient;
    private final DirectoryListingParser parser;
    private final Path cacheRoot;

    public ScriptRepositoryManager() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
        this.parser = new DirectoryListingParser();
        this.cacheRoot = VersionUtil.getJsolexDir().resolve("repository-scripts");
    }

    public List<RemoteScript> refreshRepository(ScriptRepository repository) {
        try {
            var scripts = new ArrayList<RemoteScript>();
            var scriptFiles = discoverScripts(repository);

            var repoDir = getCacheDirectory(repository);
            createDirectoriesIfNeeded(repoDir);

            for (var filename : scriptFiles) {
                try {
                    var script = downloadScript(repository, filename, repoDir);
                    if (script != null) {
                        scripts.add(script);
                    }
                } catch (Exception e) {
                    LOGGER.warn(message("repository.script.download.failed"), filename, e);
                }
            }

            LOGGER.info(message("repository.refreshed"), repository.name(), scripts.size());
            return scripts;
        } catch (Exception e) {
            LOGGER.error(message("repository.refresh.failed"), repository.name(), e);
            return Collections.emptyList();
        }
    }

    public List<RemoteScript> getLocalScripts(ScriptRepository repository) {
        var scripts = new ArrayList<RemoteScript>();
        var repoDir = getCacheDirectory(repository);

        if (!Files.exists(repoDir)) {
            return scripts;
        }

        try (var stream = Files.list(repoDir)) {
            stream.filter(path -> path.toString().endsWith(".math"))
                .forEach(path -> {
                    try {
                        var script = loadLocalScript(repository, path);
                        if (script != null) {
                            scripts.add(script);
                        }
                    } catch (Exception e) {
                        LOGGER.warn(message("repository.script.load.failed"), path, e);
                    }
                });
        } catch (IOException e) {
            LOGGER.error(message("repository.list.scripts.failed"), repository.name(), e);
        }

        return scripts;
    }

    public void cleanRepository(ScriptRepository repository) {
        var repoDir = getCacheDirectory(repository);
        if (!Files.exists(repoDir)) {
            return;
        }

        try (var stream = Files.walk(repoDir)) {
            stream.sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        LOGGER.warn(message("repository.delete.failed"), path, e);
                    }
                });
        } catch (IOException e) {
            LOGGER.error(message("repository.clean.failed"), repository.name(), e);
        }
    }

    private List<String> discoverScripts(ScriptRepository repository) throws IOException, InterruptedException {
        var url = repository.url();
        if (!url.endsWith("/")) {
            url = url + "/";
        }

        var scriptsTxtUrl = url + SCRIPTS_TXT;
        var scriptsTxtRequest = HttpRequest.newBuilder()
            .uri(URI.create(scriptsTxtUrl))
            .timeout(TIMEOUT)
            .GET()
            .build();

        LOGGER.debug(message("repository.scripts.txt.trying"), scriptsTxtUrl);
        var scriptsTxtResponse = httpClient.send(scriptsTxtRequest, HttpResponse.BodyHandlers.ofString());
        if (scriptsTxtResponse.statusCode() == 200) {
            LOGGER.debug(message("repository.scripts.txt.found"), scriptsTxtUrl);
            return parser.parseScriptsTxt(scriptsTxtResponse.body());
        }

        LOGGER.debug(message("repository.scripts.txt.not.found"), scriptsTxtResponse.statusCode(), url);
        var dirRequest = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(TIMEOUT)
            .GET()
            .build();

        var dirResponse = httpClient.send(dirRequest, HttpResponse.BodyHandlers.ofString());
        if (dirResponse.statusCode() == 200) {
            return parser.parseHtmlListing(dirResponse.body());
        }

        throw new IOException("Unable to discover scripts: HTTP " + dirResponse.statusCode());
    }

    private RemoteScript downloadScript(ScriptRepository repository, String filename, Path repoDir) throws IOException, InterruptedException {
        if (filename.toLowerCase().endsWith(".zip")) {
            return downloadAndExtractZip(repository, filename, repoDir);
        }
        return downloadMathScript(repository, filename, repoDir);
    }

    private RemoteScript downloadAndExtractZip(ScriptRepository repository, String zipFilename, Path repoDir) throws IOException, InterruptedException {
        var url = repository.url();
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        var zipUrl = url + zipFilename;

        var request = HttpRequest.newBuilder()
            .uri(URI.create(zipUrl))
            .timeout(TIMEOUT)
            .GET()
            .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            LOGGER.warn(message("repository.download.http.failed"), zipUrl, response.statusCode());
            return null;
        }

        return processZipContent(repository, zipFilename, response.body(), repoDir);
    }

    /**
     * Result of parsing a zip file's contents.
     * @param mainScriptName the name of the main script from main.txt descriptor
     * @param files map of filename to content for all files in the zip (excluding main.txt)
     */
    record ZipContents(String mainScriptName, Map<String, byte[]> files) {}

    /**
     * Parses zip bytes and extracts contents. Package-private for testing.
     * @param zipBytes the raw zip file bytes
     * @return parsed contents, or null if zip is invalid (missing descriptor)
     */
    ZipContents parseZipContents(byte[] zipBytes) throws IOException {
        var extractedFiles = new HashMap<String, byte[]>();
        String mainScriptName = null;

        try (var zipStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zipStream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                var entryName = entry.getName();
                var content = zipStream.readAllBytes();

                // main.txt at any level is the descriptor
                var simpleName = Path.of(entryName).getFileName().toString();
                if (simpleName.equals(ZIP_DESCRIPTOR)) {
                    mainScriptName = new String(content).trim();
                } else {
                    // Preserve the full path for directory structure
                    extractedFiles.put(entryName, content);
                }
            }
        }

        // If no main.txt, try to find the single .math file
        if (mainScriptName == null || mainScriptName.isEmpty()) {
            var mathFiles = extractedFiles.keySet().stream()
                .filter(name -> name.endsWith(".math"))
                .toList();
            if (mathFiles.size() == 1) {
                mainScriptName = mathFiles.getFirst();
            } else {
                // No main.txt and zero or multiple .math files - can't determine main script
                return null;
            }
        }

        return new ZipContents(mainScriptName, extractedFiles);
    }

    /**
     * Processes zip content and extracts files to the repository cache. Package-private for testing.
     */
    RemoteScript processZipContent(ScriptRepository repository, String zipFilename, byte[] zipBytes, Path repoDir) throws IOException {
        var contents = parseZipContents(zipBytes);

        if (contents == null) {
            LOGGER.warn(message("repository.zip.missing.descriptor"), zipFilename);
            return null;
        }

        var mainScriptName = contents.mainScriptName();
        var extractedFiles = contents.files();

        if (!extractedFiles.containsKey(mainScriptName)) {
            LOGGER.warn(message("repository.zip.main.not.found"), zipFilename, mainScriptName);
            return null;
        }

        var mainScriptContent = new String(extractedFiles.get(mainScriptName));
        var mainScriptPath = repoDir.resolve(mainScriptName);

        try {
            var extractor = new ImageMathParameterExtractor();
            var result = extractor.extractParameters(mainScriptContent, mainScriptName);
            var requiredVersion = result.getRequiredVersion();

            if (!VersionUtil.isVersionSupported(requiredVersion)) {
                LOGGER.debug(message("repository.script.version.checking"),
                    mainScriptName, requiredVersion, VersionUtil.getVersion());

                if (Files.exists(mainScriptPath)) {
                    var localScript = loadLocalScript(repository, mainScriptPath);
                    if (localScript != null) {
                        LOGGER.info(message("repository.script.keeping.local"),
                            mainScriptName, requiredVersion);
                        return localScript;
                    }
                }

                LOGGER.info(message("repository.script.skipping"),
                    mainScriptName, requiredVersion, VersionUtil.getVersion());
                return null;
            }

            // Extract all files to the cache directory, preserving directory structure
            for (var fileEntry : extractedFiles.entrySet()) {
                var filePath = repoDir.resolve(fileEntry.getKey());
                createDirectoriesIfNeeded(filePath.getParent());
                Files.write(filePath, fileEntry.getValue(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            LOGGER.info(message("repository.zip.extracted"), zipFilename, extractedFiles.size());
            return loadLocalScript(repository, mainScriptPath);
        } catch (Exception e) {
            LOGGER.warn(message("repository.script.metadata.parse.failed"), mainScriptName, e);
            return null;
        }
    }

    private RemoteScript downloadMathScript(ScriptRepository repository, String filename, Path repoDir) throws IOException, InterruptedException {
        var url = repository.url();
        if (!url.endsWith("/")) {
            url = url + "/";
        }
        var scriptUrl = url + filename;

        var request = HttpRequest.newBuilder()
            .uri(URI.create(scriptUrl))
            .timeout(TIMEOUT)
            .GET()
            .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            LOGGER.warn(message("repository.download.http.failed"), scriptUrl, response.statusCode());
            return null;
        }

        var scriptContent = response.body();
        var localPath = repoDir.resolve(filename);

        try {
            var extractor = new ImageMathParameterExtractor();
            var result = extractor.extractParameters(scriptContent, filename);
            var requiredVersion = result.getRequiredVersion();

            if (!VersionUtil.isVersionSupported(requiredVersion)) {
                LOGGER.debug(message("repository.script.version.checking"),
                    filename, requiredVersion, VersionUtil.getVersion());

                if (Files.exists(localPath)) {
                    var localScript = loadLocalScript(repository, localPath);
                    if (localScript != null) {
                        LOGGER.info(message("repository.script.keeping.local"),
                            filename, requiredVersion);
                        return localScript;
                    }
                }

                LOGGER.info(message("repository.script.skipping"),
                    filename, requiredVersion, VersionUtil.getVersion());
                return null;
            }

            Files.writeString(localPath, scriptContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return loadLocalScript(repository, localPath);
        } catch (Exception e) {
            LOGGER.warn(message("repository.script.metadata.parse.failed"), filename, e);
            return null;
        }
    }

    private RemoteScript loadLocalScript(ScriptRepository repository, Path localPath) {
        try {
            var extractor = new ImageMathParameterExtractor();
            var result = extractor.extractParameters(localPath);

            var author = result.getAuthor();
            var title = result.getDisplayTitle(LocaleUtils.getConfiguredLanguageCode());
            var version = result.getVersion();

            if (author == null || title == null || version == null) {
                LOGGER.debug(message("repository.script.missing.fields"), localPath.getFileName());
                return null;
            }

            return new RemoteScript(
                repository,
                localPath.getFileName().toString(),
                localPath,
                author,
                title,
                version
            );
        } catch (Exception e) {
            LOGGER.error(message("repository.script.parse.failed"), localPath, e);
            return null;
        }
    }

    private Path getCacheDirectory(ScriptRepository repository) {
        var safeName = repository.name().replaceAll("[^a-zA-Z0-9-_]", "_");
        return cacheRoot.resolve(safeName);
    }

    public boolean shouldCheckForUpdates(ScriptRepository repository) {
        if (repository.lastCheck() == null) {
            return true;
        }
        var daysSinceLastCheck = Duration.between(repository.lastCheck(), Instant.now()).toDays();
        return daysSinceLastCheck >= 1;
    }
}
