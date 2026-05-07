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
package me.champeau.a4j.jsolex.processing.sun.tasks;

import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Abstract base class for processing tasks.
 *
 * <p><b>Contract:</b> a task is a function from an input image to a result.
 * The input image — the {@code workImage} field set by {@link #prepareImage()}
 * — is treated as <b>read-only</b>. Subclasses must not mutate
 * {@code workImage.data()} (the float[][] buffer), nor add/remove entries
 * from {@code workImage.metadata()}. Reassigning the {@code workImage} field
 * to point at a different {@link ImageWrapper32} is fine; that is not a
 * mutation of the input.
 *
 * <p>If a task needs a buffer it can write to, it allocates one explicitly
 * (typically via {@code workImage.copy()} at the top of {@link #doCall()}).
 * Making mutation visible at the call site is the point — the previous
 * always-copy default hid an expensive allocation per task and was wasted
 * for every read-only subclass.
 *
 * <p><b>Mutation guard (development aid):</b> setting the environment
 * variable {@code JSOLEX_TASK_MUTATION_GUARD=true} enables a hash-based
 * before/after check around {@link #doCall()} that throws if the input
 * image was mutated. The guard is gated on a {@code static final boolean}
 * read once at class load, so when the variable is unset the JIT
 * dead-code-eliminates the check entirely (zero runtime cost).
 *
 * @param <T> the result type
 */
public abstract class AbstractTask<T> implements Callable<T>, Supplier<T> {
    private static final boolean MUTATION_GUARD_ENABLED =
            "true".equalsIgnoreCase(System.getenv("JSOLEX_TASK_MUTATION_GUARD"));

    /**
     * The progress operation for tracking task execution.
     */
    protected final ProgressOperation operation;

    /**
     * Supplier for the work image.
     */
    protected final Supplier<ImageWrapper32> workImageSupplier;

    /**
     * Broadcaster for publishing progress events.
     */
    protected final Broadcaster broadcaster;

    /**
     * The work image being processed. Treated as read-only by subclasses
     * (see class-level contract).
     */
    protected ImageWrapper32 workImage;

    /**
     * The width of the work image.
     */
    protected int width;

    /**
     * The height of the work image.
     */
    protected int height;

    /**
     * Creates an abstract task.
     *
     * @param broadcaster   the broadcaster for progress events
     * @param operation     the progress operation
     * @param imageSupplier the source image; this task must not mutate it
     */
    protected AbstractTask(Broadcaster broadcaster,
                           ProgressOperation operation,
                           Supplier<ImageWrapper32> imageSupplier) {
        this.broadcaster = broadcaster;
        this.operation = operation;
        this.workImageSupplier = imageSupplier;
    }

    @Override
    public final T call() throws Exception {
        prepareImage();
        ImageWrapper32 input = null;
        int hashBefore = 0;
        if (MUTATION_GUARD_ENABLED) {
            input = workImage;
            hashBefore = hashImage(input);
        }
        T result = doCall();
        if (MUTATION_GUARD_ENABLED) {
            int hashAfter = hashImage(input);
            if (hashBefore != hashAfter) {
                throw new IllegalStateException(getClass().getName()
                        + " mutated its input image (data hash " + hashBefore
                        + " -> " + hashAfter + ")");
            }
        }
        return result;
    }

    /**
     * Prepares the image for processing. Stores the source image directly —
     * no defensive copy. Subclasses that need to mutate must allocate their
     * own buffer in {@link #doCall()}.
     */
    protected void prepareImage() {
        var image = workImageSupplier.get();
        this.workImage = image;
        this.width = image.width();
        this.height = image.height();
    }

    /**
     * Performs the actual task computation.
     *
     * @return the task result
     * @throws Exception if an error occurs
     */
    protected abstract T doCall() throws Exception;

    @Override
    public final T get() {
        try {
            return call();
        } catch (Exception e) {
            throw ProcessingException.wrap(e);
        }
    }

    /**
     * Returns this task image buffer. Subclasses must not mutate the
     * returned array (see class-level contract).
     *
     * @return the image data buffer
     */
    public final float[][] getBuffer() {
        return workImage.data();
    }

    /**
     * Returns the image metadata. Subclasses must not add or remove
     * entries from the returned map (see class-level contract).
     *
     * @return the metadata map
     */
    public Map<Class<?>, Object> getMetadata() {
        return workImage.metadata();
    }

    private static int hashImage(ImageWrapper32 image) {
        int hash = 1;
        for (float[] row : image.data()) {
            for (float v : row) {
                hash = 31 * hash + Float.floatToIntBits(v);
            }
        }
        hash = 31 * hash + image.metadata().size();
        return hash;
    }

}
