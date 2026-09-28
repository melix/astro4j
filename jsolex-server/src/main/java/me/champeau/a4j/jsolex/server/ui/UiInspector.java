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

import java.util.List;

/**
 * Read-only access to the state of the user interface, used by the UI API.
 * Implementations must be safe to call from any thread.
 */
public interface UiInspector {

    /**
     * Lists the windows currently showing.
     *
     * @return the windows, main window first
     */
    List<UiWindow> windows();

    /**
     * Returns the node tree of a window.
     *
     * @param windowId the window identifier, as returned by {@link #windows()}
     * @param maxDepth the maximum depth to descend, or a negative value for no limit
     * @return the root node of the window
     * @throws UiApiException with status 404 if the window does not exist
     */
    UiNode tree(String windowId, int maxDepth);

    /**
     * Finds the visible nodes matching a query, across all windows or in one window.
     *
     * @param query the query
     * @return the matching nodes, without their children
     * @throws UiApiException with status 404 if the query names a window which does not exist
     */
    List<UiMatch> find(UiQuery query);

    /**
     * Renders a window, or one of its nodes, as a PNG image.
     *
     * @param windowId the window identifier
     * @param nodeRef the node reference, or null for the whole window
     * @return the PNG bytes
     * @throws UiApiException with status 400 if the node reference is malformed, 404 if the window or the node does not exist
     */
    byte[] snapshot(String windowId, String nodeRef);

    /**
     * Returns the state of the application.
     *
     * @return the state
     */
    UiState state();
}
