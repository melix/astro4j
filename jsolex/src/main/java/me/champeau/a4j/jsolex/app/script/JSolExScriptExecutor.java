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
package me.champeau.a4j.jsolex.app.script;

import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.BatchOperations;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.impl.FileSelector;
import me.champeau.a4j.jsolex.processing.expr.impl.Loader;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * A script executor which exposes a JavaFX file chooser to the {@link Loader}.
 */
public class JSolExScriptExecutor extends DefaultImageScriptExecutor {
    public JSolExScriptExecutor(Function<PixelShift, ImageWrapper> imageSupplier,
                                Map<Class, Object> context,
                                Broadcaster broadcaster,
                                Stage stage) {
        super(imageSupplier, context, broadcaster);
        prepareContext(context, stage);
    }

    public JSolExScriptExecutor(Function<PixelShift, ImageWrapper> imagesByShift,
                                Map<Class, Object> context,
                                Stage stage) {
        super(imagesByShift, context);
        prepareContext(context, stage);
    }

    private static void prepareContext(Map<Class, Object> context, Stage stage) {
        context.put(FileSelector.class, new JSolExFileSelector(stage));
    }

    /**
     * A file selector which uses a JavaFX file chooser. Since the file chooser is a UI component,
     * it must be called from the JavaFX thread. This implementation uses {@link Platform#runLater(Runnable)}
     * to ensure that the file chooser is called from the JavaFX thread and blocks until the result is available.
     */
    private static class JSolExFileSelector implements FileSelector {
        private final Stage stage;
        private final Configuration configuration;

        private JSolExFileSelector(Stage stage) {
            this.stage = stage;
            this.configuration = Configuration.getInstance();
        }

        @Override
        public Optional<File> chooseFile(String id, String title) {
            var chooser = createFileChooser(id, title);
            var file = BatchOperations.blockingUntilResultAvailable(() -> chooser.showOpenDialog(stage));
            if (file != null) {
                configuration.updateLastOpenDirectory(file.getParentFile().toPath(), id);
            }
            return Optional.ofNullable(file);
        }

        private FileChooser createFileChooser(String id, String title) {
            var chooser = new FileChooser();
            chooser.setTitle(title);
            chooser.getExtensionFilters().add(JSolEx.IMAGE_FILES_EXTENSIONS);
            configuration.findLastOpenDirectory(id).ifPresent(path -> chooser.setInitialDirectory(path.toFile()));
            return chooser;
        }

        @Override
        public Optional<List<File>> chooseFiles(String id, String title) {
            var chooser = createFileChooser(id, title);
            var files = BatchOperations.blockingUntilResultAvailable(() -> chooser.showOpenMultipleDialog(stage));
            if (files != null && !files.isEmpty()) {
                configuration.updateLastOpenDirectory(files.get(0).getParentFile().toPath(), id);
            }
            return Optional.ofNullable(files);
        }

    }
}
