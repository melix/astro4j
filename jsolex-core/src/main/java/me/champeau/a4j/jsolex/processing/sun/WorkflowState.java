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

import me.champeau.a4j.jsolex.processing.sun.workflow.WorkflowStep;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Optional;

public final class WorkflowState {
    private final int width;
    private final int height;
    private final int pixelShift;
    private final float[] reconstructed;
    private final EnumSet<WorkflowStep> enabledSteps;
    private final EnumMap<WorkflowStep, Object> outcomes = new EnumMap<>(WorkflowStep.class);
    private ImageWrapper32 image;

    public WorkflowState(
            int width,
            int height,
            int pixelShift,
            float[] buffer,
            EnumSet<WorkflowStep> enabledSteps
    ) {
        this.width = width;
        this.height = height;
        this.pixelShift = pixelShift;
        this.enabledSteps = enabledSteps;
        this.reconstructed = buffer;
    }

    public static WorkflowState prepare(int width, int height, int pixelShift, EnumSet<WorkflowStep> steps) {
        return new WorkflowState(width, height, pixelShift, new float[width * height], steps);
    }

    public static WorkflowState prepare(int width, int height, int pixelShift, WorkflowStep... steps) {
        var enabledSteps = steps.length == 0 ? EnumSet.noneOf(WorkflowStep.class) : EnumSet.copyOf(Arrays.asList(steps));
        return new WorkflowState(width, height, pixelShift, new float[width * height], enabledSteps);
    }

    public void recordResult(WorkflowStep step, Object result) {
        outcomes.put(step, result);
    }

    public <T> Optional<T> findResult(WorkflowStep step) {
        //noinspection unchecked
        return Optional.ofNullable((T) outcomes.get(step));
    }

    public boolean isEnabled(WorkflowStep step) {
        return enabledSteps.contains(step);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int pixelShift() {
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

    public EnumSet<WorkflowStep> steps() {
        return enabledSteps;
    }
}
