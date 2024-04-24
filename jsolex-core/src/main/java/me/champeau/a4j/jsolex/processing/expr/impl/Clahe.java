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

import me.champeau.a4j.jsolex.processing.stretching.ClaheStrategy;

import java.util.List;
import java.util.Map;

public class Clahe extends AbstractFunctionImpl {

    public Clahe(Map<Class<?>, Object> context) {
        super(context);
    }

    public Object clahe(List<Object> arguments) {
        if (arguments.size() != 4 && arguments.size() != 2) {
            throw new IllegalArgumentException("clahe takes either 2 or 4 arguments (image(s), [tile_size, bins], clip)");
        }
        if (arguments.size() == 2) {
            double clip = doubleArg(arguments, 1);
            return ScriptSupport.monoToMonoImageTransformer("clahe", 2, arguments, image -> new ClaheStrategy(ClaheStrategy.DEFAULT_TILE_SIZE, ClaheStrategy.DEFAULT_BINS, clip).stretch(image));

        }
        int tileSize = intArg(arguments, 1);
        int bins = intArg(arguments, 2);
        double clip = doubleArg(arguments, 3);
        if (tileSize * tileSize / (double) bins < 1.0) {
            throw new IllegalArgumentException("The number of bins is too high given the size of the tiles. Either reduce the bin count or increase the tile size");
        }
        return ScriptSupport.monoToMonoImageTransformer("clahe", 4, arguments, image -> new ClaheStrategy(tileSize, bins, clip).stretch(image));
    }
}
