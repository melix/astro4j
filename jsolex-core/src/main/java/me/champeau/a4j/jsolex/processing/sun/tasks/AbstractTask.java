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
package me.champeau.a4j.jsolex.processing.sun.tasks;

import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public abstract class AbstractTask<T> implements Callable<T>, Supplier<T> {
    protected final float[] buffer;
    protected final Broadcaster broadcaster;
    protected final int width;
    protected final int height;

    /**
     * Creates an abstract task
     *
     * @param image the current image. A copy will be created in the
     * constructor, so that this task works with its own buffer
     */
    protected AbstractTask(Broadcaster broadcaster,
                           ImageWrapper32 image) {
        this.buffer = new float[image.data().length];
        this.broadcaster = broadcaster;
        System.arraycopy(image.data(), 0, this.buffer, 0, buffer.length);
        this.width = image.width();
        this.height = image.height();
    }

    @Override
    public T get() {
        try {
            return call();
        } catch (Exception e) {
            throw ProcessingException.wrap(e);
        }
    }

    /**
     * Returns this task image buffer
     */
    public final float[] getBuffer() {
        return buffer;
    }

}
