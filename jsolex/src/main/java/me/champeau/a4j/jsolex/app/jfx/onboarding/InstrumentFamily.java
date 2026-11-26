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
package me.champeau.a4j.jsolex.app.jfx.onboarding;

import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

enum InstrumentFamily {
    SOLEX("solex", "img/onboarding-solex.jpg", List.of(SpectroHeliograph.SOLEX_10, SpectroHeliograph.SOLEX_7, SpectroHeliograph.SOLEX)),
    MLASTRO("mlastro", "img/onboarding-shg700.jpg", List.of(SpectroHeliograph.MLASTRO_SHG_700, SpectroHeliograph.MLASTRO_SHG_400)),
    SUNSCAN("sunscan", "img/onboarding-sunscan.jpg", List.of(SpectroHeliograph.SUNSCAN)),
    OTHER("other", null, List.of());

    private final String key;
    private final String image;
    private final List<SpectroHeliograph> preferredOrder;

    InstrumentFamily(String key, String image, List<SpectroHeliograph> preferredOrder) {
        this.key = key;
        this.image = image;
        this.preferredOrder = preferredOrder;
    }

    String key() {
        return key;
    }

    Optional<String> image() {
        return Optional.ofNullable(image);
    }

    static InstrumentFamily of(SpectroHeliograph instrument) {
        var label = instrument.label() == null ? "" : instrument.label().toLowerCase(Locale.US);
        if (label.contains("sunscan")) {
            return SUNSCAN;
        }
        if (label.contains("sol'ex") || label.contains("solex")) {
            return SOLEX;
        }
        if (label.contains("mlastro") || (label.contains("shg") && (label.contains("700") || label.contains("400")))) {
            return MLASTRO;
        }
        return OTHER;
    }

    List<SpectroHeliograph> variantsAmong(List<SpectroHeliograph> instruments) {
        return instruments.stream()
                .filter(instrument -> of(instrument) == this)
                .sorted(Comparator.comparingInt(this::rank))
                .toList();
    }

    private int rank(SpectroHeliograph instrument) {
        for (int i = 0; i < preferredOrder.size(); i++) {
            if (preferredOrder.get(i).label().equals(instrument.label())) {
                return i;
            }
        }
        return preferredOrder.size();
    }

    static Optional<String> noteKey(SpectroHeliograph instrument) {
        var label = instrument.label();
        if (SpectroHeliograph.SOLEX_10.label().equals(label)) {
            return Optional.of("variant.solex.10");
        }
        if (SpectroHeliograph.SOLEX_7.label().equals(label)) {
            return Optional.of("variant.solex.7");
        }
        if (SpectroHeliograph.SOLEX.label().equals(label)) {
            return Optional.of("variant.solex.original");
        }
        if (SpectroHeliograph.MLASTRO_SHG_700.label().equals(label)) {
            return Optional.of("variant.shg700");
        }
        if (SpectroHeliograph.SUNSCAN.label().equals(label)) {
            return Optional.of("variant.sunscan");
        }
        return Optional.empty();
    }
}
