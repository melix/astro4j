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
module me.champeau.a4j.jsolex.core {
    requires transitive me.champeau.a4j.jserfile;
    requires transitive me.champeau.a4j.math;
    requires transitive org.slf4j;
    requires transitive ch.qos.logback.core;
    requires commons.math3;
    requires ch.qos.logback.classic;
    requires java.prefs;
    requires jdk.incubator.vector;
    requires com.google.gson;
    requires java.desktop;
    requires nom.tam.fits;
    requires jcodec;
    exports me.champeau.a4j.jsolex.expr;
    exports me.champeau.a4j.jsolex.processing.color;
    exports me.champeau.a4j.jsolex.processing.expr;
    exports me.champeau.a4j.jsolex.processing.expr.impl;
    exports me.champeau.a4j.jsolex.processing.event;
    exports me.champeau.a4j.jsolex.processing.file;
    exports me.champeau.a4j.jsolex.processing.params;
    exports me.champeau.a4j.jsolex.processing.stats;
    exports me.champeau.a4j.jsolex.processing.stretching;
    exports me.champeau.a4j.jsolex.processing.spectrum;
    exports me.champeau.a4j.jsolex.processing.sun;
    exports me.champeau.a4j.jsolex.processing.sun.crop;
    exports me.champeau.a4j.jsolex.processing.util;
    exports me.champeau.a4j.jsolex.processing.sun.workflow;
    exports me.champeau.a4j.jsolex.processing.sun.detection;
    exports me.champeau.a4j.jsolex.processing.expr.stacking;
    uses nom.tam.fits.compress.ICompressProvider;
}
