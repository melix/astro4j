/*
 * Copyright 2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.params;

/**
 * How the spectral line being observed is determined.
 */
public enum LineDetectionMode {
    /**
     * The line is looked for among all the deep lines of the solar spectrum, which
     * allows identifying a line the user has no entry for. Falls back to {@link #AUTO}
     * when no line can be identified with confidence.
     */
    FREE_SEARCH,
    /**
     * The line is looked for among the ones the user has configured.
     */
    AUTO,
    /**
     * The user selected the line themselves.
     */
    MANUAL;

    /**
     * Whether the line has to be determined from the scan rather than being known upfront.
     */
    public boolean requiresDetection() {
        return this != MANUAL;
    }
}
