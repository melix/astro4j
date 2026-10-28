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
package me.champeau.a4j.jsolex.processing.stretching;

import me.champeau.a4j.jsolex.processing.params.AutoStretchParams;
import me.champeau.a4j.jsolex.processing.params.ClaheParams;
import me.champeau.a4j.jsolex.processing.params.ContrastEnhancement;
import me.champeau.a4j.jsolex.processing.sun.workflow.TransformationHistory;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered list of stretching steps. When stored in the metadata of an image,
 * it describes how the image is meant to be stretched for display or when saved.
 *
 * @param steps the steps, applied in order
 */
public record StretchRecipe(List<StretchStep> steps) {
    public static final StretchRecipe NONE = new StretchRecipe(List.of());

    public StretchRecipe {
        steps = List.copyOf(steps);
    }

    /**
     * Creates a recipe from a list of steps.
     *
     * @param steps the steps
     * @return the recipe
     */
    public static StretchRecipe of(StretchStep... steps) {
        return new StretchRecipe(List.of(steps));
    }

    /**
     * Returns the recipe which corresponds to a contrast enhancement technique of the
     * processing parameters. {@link ContrastEnhancement#AUTO} must have been resolved
     * to an actual technique before calling this method.
     *
     * @param contrastEnhancement the contrast enhancement technique
     * @param claheParams the CLAHE parameters, used by CLAHE and CLAHE2
     * @param autoStretchParams the AutoStretch parameters
     * @return the recipe, empty if the parameters of the technique are missing
     */
    public static StretchRecipe forContrastEnhancement(ContrastEnhancement contrastEnhancement, ClaheParams claheParams, AutoStretchParams autoStretchParams) {
        return switch (contrastEnhancement) {
            case AUTO -> throw new IllegalArgumentException("The AUTO contrast enhancement must be resolved first");
            case AUTOSTRETCH -> autoStretchParams == null ? NONE : of(StretchStep.of(StretchStepKind.AUTOSTRETCH,
                    autoStretchParams.gamma(), autoStretchParams.bgThreshold(), autoStretchParams.protusStretch()));
            case CLAHE -> claheParams == null ? NONE : of(StretchStep.of(StretchStepKind.CLAHE,
                    claheParams.tileSize(), claheParams.bins(), claheParams.clipping()));
            case CLAHE2 -> claheParams == null ? NONE : of(StretchStep.of(StretchStepKind.CLAHE2, claheParams.clipping()));
        };
    }

    /**
     * Returns true if this recipe has no step.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return steps.isEmpty();
    }

    /**
     * Returns a copy of this recipe with a step added at the end.
     *
     * @param step the step to add
     * @return the new recipe
     */
    public StretchRecipe append(StretchStep step) {
        var newSteps = new ArrayList<>(steps);
        newSteps.add(step);
        return new StretchRecipe(newSteps);
    }

    /**
     * Returns a copy of this recipe with a step replaced.
     *
     * @param index the index of the step to replace
     * @param step the new step
     * @return the new recipe
     */
    public StretchRecipe replace(int index, StretchStep step) {
        var newSteps = new ArrayList<>(steps);
        newSteps.set(index, step);
        return new StretchRecipe(newSteps);
    }

    /**
     * Returns a copy of this recipe without a step.
     *
     * @param index the index of the step to remove
     * @return the new recipe
     */
    public StretchRecipe remove(int index) {
        var newSteps = new ArrayList<>(steps);
        newSteps.remove(index);
        return new StretchRecipe(newSteps);
    }

    /**
     * Returns the recipe made of the first steps of this recipe.
     *
     * @param count the number of steps to keep
     * @return the new recipe
     */
    public StretchRecipe prefix(int count) {
        return new StretchRecipe(steps.subList(0, count));
    }

    /**
     * Creates the stretching strategy which applies all steps in order.
     *
     * @return the strategy
     */
    public StretchingStrategy toStrategy() {
        return new StretchingChain(steps.stream().map(StretchStep::toStrategy).toList());
    }

    /**
     * Applies the steps in place on an image and records them in its
     * transformation history.
     *
     * @param image the image to stretch
     */
    public void applyTo(ImageWrapper image) {
        for (var step : steps) {
            step.toStrategy().stretch(image);
            TransformationHistory.recordTransform(image, step.describe());
        }
    }

    /**
     * Returns the stretched version of an image which carries a recipe in its metadata:
     * a copy of the image, without the recipe, on which the recipe has been applied.
     * Images without a recipe are returned unchanged.
     *
     * @param image the image
     * @return the stretched image, or the image itself if it has no recipe
     */
    public static ImageWrapper materialize(ImageWrapper image) {
        var recipe = image.findMetadata(StretchRecipe.class);
        if (recipe.isEmpty()) {
            return image;
        }
        var copy = withoutRecipe(image);
        recipe.get().applyTo(copy);
        return copy;
    }

    /**
     * Returns a copy of an image, without the recipe stored in its metadata.
     *
     * @param image the image
     * @return the copy
     */
    public static ImageWrapper withoutRecipe(ImageWrapper image) {
        var copy = image.unwrapToMemory().copy();
        copy.metadata().remove(StretchRecipe.class);
        return copy;
    }
}
