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
package me.champeau.a4j.jsolex.app.jfx.help;

import javafx.scene.Node;

/**
 * Interface for providing rich help content for specific image kinds.
 * Implementations can provide animated diagrams or other interactive content
 * to explain how certain images are generated.
 */
public interface ImageHelpContentProvider {

    /**
     * Creates the content node to display in the help popup.
     *
     * @return the content node
     */
    Node createContent();

    /**
     * Called when the help popup is shown.
     * Can be used to start animations.
     */
    default void onShown() {
    }

    /**
     * Called when the help popup is hidden.
     * Can be used to stop animations and free resources.
     */
    default void onHidden() {
    }

}
