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
package me.champeau.a4j.jsolex.processing.expr.impl;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Interface for a file chooser, which can be injected in a loader.
 * The implementation may decide to use a native file chooser, or
 * a custom one.
 */
public interface FileSelector {
    /**
     * Asks the user to choose a single file.
     * @param id an identifier for the file chooser, which can be used to remember the last directory
     * @param title the title of the file chooser
     * @return the chosen file, or {@link Optional#empty()} if the user cancelled the operation
     */
    Optional<File> chooseFile(String id, String title);

    /**
     * Asks the user to choose multiple files.
     * @param id an identifier for the file chooser, which can be used to remember the last directory
     * @param title the title of the file chooser
     * @return the chosen files, or {@link Optional#empty()} if the user cancelled the operation
     */
    Optional<List<File>> chooseFiles(String id, String title);
}
