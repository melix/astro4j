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
package me.champeau.a4j.jsolex.processing.sun;

import me.champeau.a4j.jsolex.processing.sun.workflow.WorkflowResults;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.util.EnumMap;
import java.util.Optional;

public final class WorkflowState {
    private final int width;
    private final int height;
    private final double pixelShift;
    private final float[] reconstructed;
    private final EnumMap<WorkflowResults, Object> outcomes = new EnumMap<>(WorkflowResults.class);
    private ImageWrapper32 image;
    private boolean internal;

    public WorkflowState(
            int width,
            int height,
            double pixelShift,
            float[] buffer
    ) {
        this.width = width;
        this.height = height;
        this.pixelShift = pixelShift;
        this.reconstructed = buffer;
    }

    public boolean isInternal() {
        return internal;
    }

    public void setInternal(boolean internal) {
        this.internal = internal;
    }

    public static WorkflowState prepare(int width, int height, double pixelShift) {
        return new WorkflowState(width, height, pixelShift, new float[width * height]);
    }

    public void recordResult(WorkflowResults step, Object result) {
        outcomes.put(step, result);
    }

    public void discardResult(WorkflowResults step) {
        outcomes.remove(step);
    }

    public <T> Optional<T> findResult(WorkflowResults step) {
        //noinspection unchecked
        return Optional.ofNullable((T) outcomes.get(step));
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public double pixelShift() {
        return pixelShift;
    }

    public void setImage(ImageWrapper32 image) {
        this.image = image;
    }

    public ImageWrapper32 image() {
        return image;
    }

    public float[] reconstructed() {
        return reconstructed;
    }
}
