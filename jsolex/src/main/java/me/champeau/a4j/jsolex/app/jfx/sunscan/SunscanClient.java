/*
 * Copyright 2023-2025 the original author or authors.
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
package me.champeau.a4j.jsolex.app.jfx.sunscan;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Minimal HTTP client for the SunScan backend. The SunScan device is a Raspberry Pi
 * acting as a Wi-Fi access point; its FastAPI backend runs on port 8000 and exposes
 * the list of acquired scans as well as the raw {@code scan.ser} files.
 */
public class SunscanClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(SunscanClient.class);

    public static final int DEFAULT_PORT = 8000;
    public static final String DEFAULT_HOST = "sunscan.local";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);
    private static final int PAGE_SIZE = 50;

    /**
     * Hosts probed during auto-detection. {@code 10.42.0.1} is the default gateway
     * address used by NetworkManager when the SunScan shares its Wi-Fi connection.
     */
    private static final List<String> PROBE_HOSTS = List.of(DEFAULT_HOST, "10.42.0.1");

    private final HttpClient httpClient;
    private final Gson gson = new Gson();
    private final String baseUrl;

    public SunscanClient(String baseUrl) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public String baseUrl() {
        return baseUrl;
    }

    /**
     * Normalizes a user-supplied host or URL into a {@code http://host:port} base URL.
     */
    public static String normalizeBaseUrl(String hostOrUrl) {
        var value = hostOrUrl == null ? "" : hostOrUrl.trim();
        if (value.isEmpty()) {
            value = DEFAULT_HOST;
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        var uri = URI.create(value);
        var port = uri.getPort();
        if (port == -1) {
            value = value + ":" + DEFAULT_PORT;
        }
        return value;
    }

    /**
     * Attempts to locate a SunScan device on the network by probing the well-known
     * hostname and the default access-point gateway address.
     */
    public static Optional<SunscanClient> autoDetect() {
        for (var host : PROBE_HOSTS) {
            var candidate = new SunscanClient(host);
            if (candidate.ping()) {
                LOGGER.info("Detected SunScan device at {}", candidate.baseUrl());
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * Checks whether a SunScan backend answers at this client's base URL.
     */
    public boolean ping() {
        try {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/sunscan/scans?page=1&size=1"))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Retrieves every scan available on the device, following pagination.
     */
    public List<SunscanScan> listAllScans() throws IOException, InterruptedException {
        var result = new ArrayList<SunscanScan>();
        var page = 1;
        while (true) {
            var pageContent = listScans(page, PAGE_SIZE);
            result.addAll(pageContent.scans());
            if (result.size() >= pageContent.total() || pageContent.scans().isEmpty()) {
                break;
            }
            page++;
        }
        return result;
    }

    private SunscanScanList listScans(int page, int size) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/sunscan/scans?page=" + page + "&size=" + size))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Unexpected response from SunScan device: HTTP " + response.statusCode());
        }
        try {
            var list = gson.fromJson(response.body(), SunscanScanList.class);
            if (list == null) {
                throw new IOException("Empty response from SunScan device");
            }
            return list;
        } catch (JsonSyntaxException e) {
            throw new IOException("Invalid response from SunScan device", e);
        }
    }

    /**
     * Downloads the {@code scan.ser} file of the given scan into {@code destination}.
     *
     * @param scan the scan to download
     * @param destination the local file to write
     * @param progress a callback invoked with the number of bytes downloaded so far
     *                 and the expected total ({@code -1} when unknown)
     */
    public void downloadScan(SunscanScan scan, Path destination, DownloadProgress progress)
            throws IOException, InterruptedException {
        var serPath = scan.ser();
        if (serPath == null || serPath.isBlank()) {
            throw new IOException("Scan has no SER file");
        }
        var encodedPath = serPath.startsWith("/") ? serPath : "/" + serPath;
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + encodedPath))
                .timeout(DOWNLOAD_TIMEOUT)
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("Unable to download scan: HTTP " + response.statusCode());
        }
        var total = response.headers().firstValueAsLong("content-length").orElse(-1L);
        Files.createDirectories(destination.getParent());
        try (InputStream in = response.body();
             OutputStream out = Files.newOutputStream(destination)) {
            var buffer = new byte[64 * 1024];
            long downloaded = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                downloaded += read;
                if (progress != null) {
                    progress.onProgress(downloaded, total);
                }
            }
        }
    }

    /**
     * Callback invoked while a scan is being downloaded.
     */
    @FunctionalInterface
    public interface DownloadProgress {
        void onProgress(long bytesDownloaded, long totalBytes);
    }

    @Override
    public String toString() {
        return baseUrl.toLowerCase(Locale.US);
    }
}
