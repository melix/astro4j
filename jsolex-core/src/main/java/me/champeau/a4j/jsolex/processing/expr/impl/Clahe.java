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
package me.champeau.a4j.jsolex.processing.expr.impl;

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.processing.stretching.ClaheStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;

import java.util.Map;

public class Clahe extends AbstractFunctionImpl {

    public Clahe(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object clahe(Map<String ,Object> arguments) {
        BuiltinFunction.CLAHE.validateArgs(arguments);
        int tileSize = intArg(arguments, "ts", ClaheStrategy.DEFAULT_TILE_SIZE);
        int bins = intArg(arguments, "bins", ClaheStrategy.DEFAULT_BINS);
        double clip = doubleArg(arguments, "clip", 1.0);
        if (tileSize * tileSize / (double) bins < 1.0) {
            throw new IllegalArgumentException("The number of bins is too high given the size of the tiles. Either reduce the bin count or increase the tile size");
        }
        return monoToMonoImageTransformer("clahe", "img", arguments, image -> new ClaheStrategy(tileSize, bins, clip).stretch(image));
    }
}
