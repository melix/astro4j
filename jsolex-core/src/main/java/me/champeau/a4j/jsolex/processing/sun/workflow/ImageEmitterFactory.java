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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ParallelExecutor;

import java.nio.file.Path;

/**
 * An image emitter factory is responsible for creating
 * instances of image emitters, which can for example
 * be no-op (in case we don't care about intermediate
 * images) or renaming files.
 */
public interface ImageEmitterFactory {
    ImageEmitter newEmitter(Broadcaster broadcaster,
                            ParallelExecutor executor,
                            String kind,
                            Path outputDirectory);
}
