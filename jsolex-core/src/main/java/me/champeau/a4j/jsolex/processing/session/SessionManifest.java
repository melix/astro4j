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
package me.champeau.a4j.jsolex.processing.session;

import java.util.List;

/**
 * The JSON manifest stored at the root of a session archive. It describes the
 * images and media it contains and the entries holding their data.
 */
record SessionManifest(int version, String appVersion, List<ImageEntry> images, List<MediaEntry> media, ReRun reRun) {

    record ImageEntry(String id, String kind, String title, String baseName, String description) {
    }

    record MediaEntry(String id, String kind, String title, String description, String fileName, String type) {
    }

    record ReRun(List<SingleRun> singleRuns, Batch batch) {
    }

    record SingleRun(String serFilePath, String contextEntry) {
    }

    record Batch(List<Label> labels) {
    }

    record Label(String label, List<String> imageIds, List<Object> values) {
    }
}
