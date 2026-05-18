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

import java.util.List;

/**
 * The paginated response of the SunScan backend {@code /sunscan/scans} endpoint.
 *
 * @param total the total number of scans available on the device
 * @param scans the scans contained in the requested page
 */
public record SunscanScanList(int total, List<SunscanScan> scans) {
    public SunscanScanList {
        scans = scans == null ? List.of() : List.copyOf(scans);
    }
}
