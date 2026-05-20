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
package me.champeau.a4j.jsolex.app.jfx;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

/**
 * Derives the lifecycle state of a {@link BatchItem} from its localized
 * status string. Resolving the four reference labels once is required
 * because the status property carries the already-localized text.
 */
public enum BatchItemState {
    QUEUED,
    RUNNING,
    DONE,
    ERROR;

    public static BatchItemState of(String status) {
        return Resolver.INSTANCE.classify(status);
    }

    private static final class Resolver {
        private static final Resolver INSTANCE = new Resolver();

        private final String pending = message("batch.pending");
        private final String ok = message("batch.ok");
        private final String error = message("batch.error");

        BatchItemState classify(String status) {
            if (ok.equals(status)) {
                return DONE;
            }
            if (error.equals(status)) {
                return ERROR;
            }
            if (pending.equals(status)) {
                return QUEUED;
            }
            return RUNNING;
        }
    }
}
