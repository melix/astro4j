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
 * Optional state, stored only when the user opts in, that lets scripts be re-run
 * after a session is imported.
 *
 * @param singleRuns   the single-file runs (each able to reconstruct shifts from its SER file); may be empty
 * @param batchOutputs the collected batch outputs to restore as variables for the batch section (may be {@code null})
 */
public record SessionReRunData(List<SessionSingleRun> singleRuns, SessionBatchOutputs batchOutputs) {
    public boolean isEmpty() {
        return (singleRuns == null || singleRuns.isEmpty()) && batchOutputs == null;
    }
}
