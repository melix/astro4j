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
package me.champeau.a4j.jsolex.processing.expr;

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.Wavelen;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

public class ShiftCollectingImageExpressionEvaluator extends ImageExpressionEvaluator {

    private final Set<Double> shifts = ConcurrentHashMap.newKeySet();
    private final Set<Double> autoWavelenghts = ConcurrentHashMap.newKeySet();
    private final Map<PixelShift, ImageWrapper> cache = new ConcurrentHashMap<>();
    private final Map<PixelShift, ReentrantLock> loadLocks = new ConcurrentHashMap<>();
    private volatile boolean autoContinuum = false;

    public static boolean isShiftCollecting(Function<PixelShift, ImageWrapper> function) {
        return function instanceof ShiftCollectingFunction;
    }

    public static Function<PixelShift, ImageWrapper> zeroImages() {
        var map = new ConcurrentHashMap<PixelShift, ImageWrapper>();
        return (ShiftCollectingFunction) idx -> map.computeIfAbsent(idx, unused -> ImageWrapper32.createEmpty());
    }

    public ShiftCollectingImageExpressionEvaluator(Broadcaster broadcaster) {
        this(broadcaster, zeroImages());
    }

    public ShiftCollectingImageExpressionEvaluator(Broadcaster broadcaster, Function<PixelShift, ImageWrapper> imageFactory) {
        super(broadcaster, imageFactory);
    }

    @Override
    public boolean isShiftCollecting() {
        return isShiftCollecting(images);
    }

    @Override
    protected double computePixelShift(ProcessParams params, Wavelen targetWaveLength, Wavelen reference) {
        autoWavelenghts.add(targetWaveLength.angstroms());
        return super.computePixelShift(params, targetWaveLength, reference);
    }

    @Override
    public Object functionCall(BuiltinFunction function, Map<String, Object> arguments) {
        if (function == BuiltinFunction.CONTINUUM) {
            autoContinuum = true;
        }
        try {
            return super.functionCall(function, arguments);
        } catch (Exception ex) {
            if (isShiftCollecting(images)) {
                // return a dummy image
                return zeroImages().apply(new PixelShift(0.0));
            }
            throw ex;
        }
    }

    public ImageWrapper findImage(PixelShift shift) {
        shifts.add(shift.pixelShift());
        return cache.computeIfAbsent(shift, s -> FileBackedImage.wrap(super.findImage(shift)));
    }

    public Set<Double> getShifts() {
        return Collections.unmodifiableSet(shifts);
    }

    public Set<Double> getAutoWavelenghts() {
        return Collections.unmodifiableSet(autoWavelenghts);
    }

    public boolean usesAutoContinuum() {
        return autoContinuum;
    }

    public void clearShifts() {
        shifts.clear();
        autoWavelenghts.clear();
        autoContinuum = false;
    }

    public void clearCache() {
        cache.clear();
    }

    public void addShift(double shift) {
        shifts.add(shift);
    }

    private interface ShiftCollectingFunction extends Function<PixelShift, ImageWrapper> {
    }
}
