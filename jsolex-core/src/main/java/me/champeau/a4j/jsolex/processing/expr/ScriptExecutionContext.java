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
package me.champeau.a4j.jsolex.processing.expr;

import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageEmitter;
import me.champeau.a4j.jsolex.processing.sun.workflow.ImageStats;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShiftRange;
import me.champeau.a4j.jsolex.processing.sun.workflow.ReferenceCoords;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.sun.workflow.SpectralLinePolynomial;
import me.champeau.a4j.jsolex.processing.util.AnimationFormat;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.jsolex.processing.util.SolarParametersUtils;
import me.champeau.a4j.math.regression.Ellipse;
import me.champeau.a4j.ser.Header;
import me.champeau.a4j.ser.SerFileReader;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public final class ScriptExecutionContext {
    private final Map<Class<?>, Object> entries;

    private ScriptExecutionContext(Map<Class<?>, Object> entries) {
        this.entries = new HashMap<>(entries);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ScriptExecutionContext empty() {
        return new ScriptExecutionContext(Map.of());
    }

    public static Builder forProcessing(ProcessParams processParams, Path serFile, PixelShiftRange pixelShiftRange, Header header) {
        return builder()
            .processParams(processParams)
            .solarParameters(SolarParametersUtils.computeSolarParams(processParams.observationDetails().date().toLocalDateTime()))
            .referenceCoords(new ReferenceCoords(List.of()))
            .sourceInfo(serFile, header)
            .pixelShiftRange(pixelShiftRange);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> key) {
        return (T) entries.get(key);
    }

    public boolean containsKey(Class<?> key) {
        return entries.containsKey(key);
    }

    public void put(Class<?> key, Object value) {
        if (value != null) {
            entries.put(key, value);
        }
    }

    public ScriptExecutionContext mergeAll(Map<Class<?>, Object> additional) {
        if (additional != null) {
            entries.putAll(additional);
        }
        return this;
    }

    public Map<Class<?>, Object> toMap() {
        return new HashMap<>(entries);
    }

    public void forEach(BiConsumer<Class<?>, Object> action) {
        entries.forEach(action);
    }

    public static final class Builder {
        private final Map<Class<?>, Object> entries = new HashMap<>();

        private Builder() {
        }

        public Builder processParams(ProcessParams processParams) {
            if (processParams != null) {
                entries.put(ProcessParams.class, processParams);
            }
            return this;
        }

        public Builder solarParameters(SolarParameters solarParameters) {
            if (solarParameters != null) {
                entries.put(SolarParameters.class, solarParameters);
            }
            return this;
        }

        public Builder referenceCoords(ReferenceCoords referenceCoords) {
            if (referenceCoords != null) {
                entries.put(ReferenceCoords.class, referenceCoords);
            }
            return this;
        }

        public Builder sourceInfo(Path serFile, Header header) {
            if (serFile != null && header != null) {
                var file = serFile.toFile();
                entries.put(SourceInfo.class, new SourceInfo(
                    file.getName(),
                    file.getParentFile().getName(),
                    header.metadata().utcDateTime(),
                    header.geometry().width(),
                    header.frameCount()
                ));
            }
            return this;
        }

        public Builder pixelShiftRange(PixelShiftRange pixelShiftRange) {
            if (pixelShiftRange != null) {
                entries.put(PixelShiftRange.class, pixelShiftRange);
            }
            return this;
        }

        public Builder imageEmitter(ImageEmitter imageEmitter) {
            if (imageEmitter != null) {
                entries.put(ImageEmitter.class, imageEmitter);
            }
            return this;
        }

        public Builder progressOperation(ProgressOperation progressOperation) {
            if (progressOperation != null) {
                entries.put(ProgressOperation.class, progressOperation);
            }
            return this;
        }

        public Builder serFileReader(SerFileReader serFileReader) {
            if (serFileReader != null) {
                entries.put(SerFileReader.class, serFileReader);
            }
            return this;
        }

        public Builder ellipse(Ellipse ellipse) {
            if (ellipse != null) {
                entries.put(Ellipse.class, ellipse);
            }
            return this;
        }

        public Builder imageStats(ImageStats imageStats) {
            if (imageStats != null) {
                entries.put(ImageStats.class, imageStats);
            }
            return this;
        }

        public Builder spectralLinePolynomial(SpectralLinePolynomial spectralLinePolynomial) {
            if (spectralLinePolynomial != null) {
                entries.put(SpectralLinePolynomial.class, spectralLinePolynomial);
            }
            return this;
        }

        public Builder animationFormats(Set<AnimationFormat> animationFormats) {
            if (animationFormats != null) {
                entries.put(AnimationFormat.class, animationFormats);
            }
            return this;
        }

        public ScriptExecutionContext build() {
            return new ScriptExecutionContext(entries);
        }
    }
}
