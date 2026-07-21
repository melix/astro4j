/*
 * Copyright 2023-2026 the original author or authors.
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

import java.time.LocalDateTime;

/**
 * Describes a flip which depends on the capture date of a video: files captured
 * on one side of the pivot date are flipped, the others are not.
 *
 * @param pivotUtc the pivot date, in UTC
 * @param mode which side of the pivot date must be flipped
 */
public record ConditionalFlip(
        LocalDateTime pivotUtc,
        Mode mode
) {
    public enum Mode {
        FLIP_BEFORE,
        FLIP_AFTER
    }

    public boolean flipAt(LocalDateTime utcDateTime) {
        if (utcDateTime.isBefore(pivotUtc)) {
            return mode == Mode.FLIP_BEFORE;
        }
        return mode == Mode.FLIP_AFTER;
    }

    public ConditionalFlip inverted() {
        return new ConditionalFlip(pivotUtc, mode == Mode.FLIP_BEFORE ? Mode.FLIP_AFTER : Mode.FLIP_BEFORE);
    }

    public boolean isSameSide(LocalDateTime first, LocalDateTime second) {
        return first.isBefore(pivotUtc) == second.isBefore(pivotUtc);
    }
}
