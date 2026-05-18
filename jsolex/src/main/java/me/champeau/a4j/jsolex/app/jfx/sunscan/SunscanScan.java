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

import com.google.gson.annotations.SerializedName;

/**
 * A single scan as exposed by the SunScan backend {@code /sunscan/scans} endpoint.
 *
 * @param path the relative directory of the scan on the SunScan device
 * @param ser the relative path of the {@code scan.ser} file, served under {@code /storage}
 * @param status the SunScan processing status (pending, completed, failed)
 * @param creationDate the scan creation date, as a Unix epoch in seconds
 * @param tag the spectral line tag, if any (for example {@code halpha})
 */
public record SunscanScan(
        String path,
        String ser,
        String status,
        @SerializedName("creation_date") long creationDate,
        String tag
) {
    /**
     * Returns the name of the scan directory, used to name the local download folder.
     */
    public String folderName() {
        if (path == null || path.isBlank()) {
            return "scan";
        }
        var normalized = path.replace('\\', '/');
        var idx = normalized.lastIndexOf('/');
        return idx >= 0 ? normalized.substring(idx + 1) : normalized;
    }
}
