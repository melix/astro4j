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
package me.champeau.a4j.jsolex.app.jfx.help;

import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Registry for mapping GeneratedImageKind to their rich help content providers.
 */
public final class ImageHelpContentRegistry {

    private static final Map<GeneratedImageKind, Supplier<ImageHelpContentProvider>> PROVIDERS = Map.of(
            GeneratedImageKind.DOPPLER, DopplerHelpContentProvider::new,
            GeneratedImageKind.DOPPLER_ECLIPSE, DopplerHelpContentProvider::new,
            GeneratedImageKind.RECONSTRUCTION, ReconstructionHelpContentProvider::new
    );

    private ImageHelpContentRegistry() {
    }

    /**
     * Gets a help content provider for the given image kind, if one exists.
     *
     * @param kind the generated image kind
     * @return an optional containing the provider, or empty if no rich content is available
     */
    public static Optional<ImageHelpContentProvider> getProvider(GeneratedImageKind kind) {
        var supplier = PROVIDERS.get(kind);
        return supplier != null ? Optional.of(supplier.get()) : Optional.empty();
    }

}
