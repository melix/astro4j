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
package me.champeau.a4j.jsolex.processing.util;

import com.google.gson.Gson;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.CreateSessionRequest;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.ImageResponse;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.InitiateUploadRequest;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.InitiateUploadResponse;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.QuotaResponse;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.SessionResponse;
import me.champeau.a4j.jsolex.processing.util.spectrosolhub.SpectroSolHubException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class SpectroSolHubClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpectroSolHubClient.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final String USER_AGENT = "JSolEx/" + VersionUtil.getVersion();

    private final HttpClient httpClient;
    private final Gson gson;
    private final String baseUrl;
    private final String token;

    public SpectroSolHubClient(String baseUrl, String token) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.token = token;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.gson = new Gson();
    }

    public static String login(String baseUrl, String username, String password, String tokenName, String totpCode) throws SpectroSolHubException {
        var normalizedUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        var gson = new Gson();

        Map<String, String> requestBody = new LinkedHashMap<>();
        requestBody.put("username", username);
        requestBody.put("password", password);
        requestBody.put("tokenName", tokenName);
        if (totpCode != null) {
            requestBody.put("totpCode", totpCode);
        }

        var body = gson.toJson(requestBody);
        var request = HttpRequest.newBuilder()
                .uri(URI.create(normalizedUrl + "/api/auth/token"))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 201) {
                @SuppressWarnings("unchecked")
                var tokenResponse = (Map<String, String>) gson.fromJson(response.body(), Map.class);
                return tokenResponse.get("token");
            }
            throw new SpectroSolHubException(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new SpectroSolHubException("Failed to connect to SpectroSolHub", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpectroSolHubException("Connection interrupted", e);
        }
    }

    public QuotaResponse fetchQuota() throws SpectroSolHubException {
        return get("/api/users/me/quota", QuotaResponse.class);
    }

    public SessionResponse createSession(CreateSessionRequest sessionRequest) throws SpectroSolHubException {
        return post("/api/sessions", sessionRequest, SessionResponse.class, 201);
    }

    public SessionResponse publishSession(long sessionId) throws SpectroSolHubException {
        return post("/api/sessions/" + sessionId + "/publish", null, SessionResponse.class, 200);
    }

    public InitiateUploadResponse initiateUpload(InitiateUploadRequest uploadRequest) throws SpectroSolHubException {
        return post("/api/uploads/initiate", uploadRequest, InitiateUploadResponse.class, 201);
    }

    public void uploadPart(String uploadId, int partNumber, byte[] data) throws SpectroSolHubException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/uploads/" + uploadId + "/parts/" + partNumber))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/octet-stream")
                .header("User-Agent", USER_AGENT)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new SpectroSolHubException(response.statusCode(), "Failed to upload part " + partNumber + ": " + response.body());
            }
        } catch (IOException e) {
            throw new SpectroSolHubException("Failed to upload part " + partNumber, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpectroSolHubException("Upload interrupted", e);
        }
    }

    public ImageResponse completeUpload(String uploadId) throws SpectroSolHubException {
        return post("/api/uploads/" + uploadId + "/complete", null, ImageResponse.class, 201);
    }

    public ImageResponse uploadImage(long sessionId, String title, String imageKind, String imageMetadataJson, byte[] imageData, UploadProgressListener listener) throws SpectroSolHubException {
        var initiateRequest = new InitiateUploadRequest(
                sessionId,
                title,
                null,
                imageKind,
                imageMetadataJson,
                imageData.length,
                "image/jpeg"
        );

        var initResponse = initiateUpload(initiateRequest);
        var chunkSize = initResponse.chunkSize();
        var totalParts = initResponse.totalParts();
        var uploadId = initResponse.uploadId();

        LOGGER.info("Uploading '{}' ({} bytes) in {} parts", title, imageData.length, totalParts);

        for (int part = 1; part <= totalParts; part++) {
            int offset = (part - 1) * chunkSize;
            int length = Math.min(chunkSize, imageData.length - offset);
            var chunk = Arrays.copyOfRange(imageData, offset, offset + length);
            uploadPart(uploadId, part, chunk);
            listener.onProgress(part, totalParts);
        }

        return completeUpload(uploadId);
    }

    private <T> T get(String path, Class<T> responseType) throws SpectroSolHubException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return gson.fromJson(response.body(), responseType);
            }
            throw new SpectroSolHubException(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new SpectroSolHubException("Failed to connect to SpectroSolHub", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpectroSolHubException("Connection interrupted", e);
        }
    }

    private <T> T post(String path, Object body, Class<T> responseType, int expectedStatus) throws SpectroSolHubException {
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", USER_AGENT);

        if (body != null) {
            requestBuilder
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)));
        } else {
            requestBuilder
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody());
        }

        try {
            var response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == expectedStatus) {
                return gson.fromJson(response.body(), responseType);
            }
            throw new SpectroSolHubException(response.statusCode(), response.body());
        } catch (IOException e) {
            throw new SpectroSolHubException("Failed to connect to SpectroSolHub", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SpectroSolHubException("Connection interrupted", e);
        }
    }

    @FunctionalInterface
    public interface UploadProgressListener {
        void onProgress(int partCompleted, int totalParts);
    }
}
