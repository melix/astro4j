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
package me.champeau.a4j.jsolex.app.util;

import javafx.application.Platform;
import me.champeau.a4j.jsolex.processing.util.ProcessingLogContext;

/**
 * JavaFX helpers that preserve the processing log context across the hop to
 * the JavaFX Application Thread.
 */
public final class FxUtils {

    private FxUtils() {

    }

    /**
     * Equivalent to {@link Platform#runLater(Runnable)}, but captures the
     * current {@link ProcessingLogContext} file identity on the calling thread
     * and re-establishes it while the action runs on the JavaFX Application
     * Thread, so log statements emitted from the deferred action are still
     * attributed to the file being processed. When no file is being processed
     * on the calling thread the capture is a no-op.
     */
    public static void runLater(Runnable action) {
        var fileId = ProcessingLogContext.currentFileId();
        if (fileId.isEmpty()) {
            Platform.runLater(action);
        } else {
            int id = fileId.getAsInt();
            Platform.runLater(() -> ProcessingLogContext.runWith(id, action));
        }
    }
}
