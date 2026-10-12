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
package me.champeau.a4j.jsolex.app.listeners;

import me.champeau.a4j.jsolex.processing.event.ProgressOperation;

import java.util.List;
import java.util.Optional;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * Combines the progress of the steps of an image reconstruction into a single progress
 * which never goes backwards. Each step receives a share of the progress bar which
 * matches its typical duration, and the operations which are not one of these steps are ignored.
 */
final class ReconstructionProgress {
    private static final List<Stage> STAGES = List.of(
            new Stage(message("computing.average.image.limb.detect"), 0, 0.12, true),
            new Stage(message("reconstruction"), 0.12, 0.25, false),
            new Stage(message("preparing.for.corrections"), 0.25, 0.9, false),
            new Stage(message("generating.images"), 0.9, 1, false)
    );

    private int stage = -1;
    private double progress;

    /**
     * Takes an operation into account.
     *
     * @param operation an operation of the reconstruction
     * @return the current step and the overall progress, if the operation is a step of the reconstruction
     */
    synchronized Optional<State> update(ProgressOperation operation) {
        var index = indexOf(operation.task());
        if (index < 0 || !isReachable(index)) {
            return Optional.empty();
        }
        stage = index;
        var current = STAGES.get(index);
        var fraction = Math.clamp(operation.progress(), 0, 1);
        progress = Math.max(progress, current.start() + fraction * (current.end() - current.start()));
        return Optional.of(new State(current.task(), progress));
    }

    private boolean isReachable(int index) {
        if (index == stage || index == stage + 1) {
            return true;
        }
        return index == stage + 2 && STAGES.get(stage + 1).optional();
    }

    private static int indexOf(String task) {
        for (var i = 0; i < STAGES.size(); i++) {
            if (STAGES.get(i).task().equals(task)) {
                return i;
            }
        }
        return -1;
    }

    record State(String task, double progress) {
    }

    private record Stage(String task, double start, double end, boolean optional) {
    }
}
