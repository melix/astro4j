/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.server.ui;

/**
 * Error reported to the UI API client with an HTTP status.
 */
public class UiApiException extends RuntimeException {
    private final int status;

    /**
     * Creates an exception.
     *
     * @param status the HTTP status
     * @param message the message
     */
    public UiApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Returns the HTTP status.
     *
     * @return the status
     */
    public int status() {
        return status;
    }
}
