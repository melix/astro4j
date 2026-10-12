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
package me.champeau.a4j.jsolex.processing.expr.impl;

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudInversion;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudInversionResult;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudMap;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudMapRenderer;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.CloudModelProcessor;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.RGBImage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static me.champeau.a4j.jsolex.processing.util.Constants.message;

/**
 * ImageMath access to the cloud model inversion of H-alpha profiles.
 */
public class CloudModelFunctions extends AbstractFunctionImpl {

    public CloudModelFunctions(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object cloudInversion(Map<String, Object> arguments) {
        BuiltinFunction.CLOUD_INVERSION.validateArgs(arguments);
        if (!(arguments.get("images") instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException(message("cloud.model.function.images.required"));
        }
        var images = new ArrayList<ImageWrapper>();
        for (var item : list) {
            if (!(item instanceof ImageWrapper image)) {
                throw new IllegalArgumentException(message("cloud.model.function.images.required"));
            }
            images.add(image);
        }
        var params = getFromContext(ProcessParams.class)
                .orElseThrow(() -> new IllegalStateException(message("cloud.model.function.no.params")));
        var binning = intArg(arguments, "binning", 0);
        var components = intArg(arguments, "components", 3);
        if (components != 1 && components != 3) {
            throw new IllegalArgumentException(message("cloud.model.function.components"));
        }
        var options = new CloudInversion.Options(
                doubleArg(arguments, "turbulence", CloudInversion.Options.DEFAULT.nonThermalSpeed()),
                CloudInversion.Options.DEFAULT.fitHalfWidth(),
                CloudInversion.Options.DEFAULT.minMu(),
                components == 3,
                binning <= 0 ? CloudModelProcessor.defaultBinning(params) : Math.min(binning, CloudModelProcessor.MAX_BINNING)
        );
        return CloudModelProcessor.invert(params, images, options, broadcaster, newOperation());
    }

    public Object cloudMap(Map<String, Object> arguments) {
        BuiltinFunction.CLOUD_MAP.validateArgs(arguments);
        if (!(arguments.get("inversion") instanceof CloudInversionResult result)) {
            throw new IllegalArgumentException(message("cloud.model.function.inversion.required"));
        }
        var map = CloudMap.fromId(stringArg(arguments, "map", null));
        var image = result.image(map);
        image.metadata().put(CloudMap.class, map);
        if (intArg(arguments, "colorize", 0) == 0) {
            return image;
        }
        var rgb = CloudMapRenderer.colorize(result, map);
        var colored = new RGBImage(result.width(), result.height(), rgb[0], rgb[1], rgb[2], new HashMap<>(image.metadata()));
        return ImageDraw.drawOnImageInPlace(colored, (g, img) -> CloudMapRenderer.paintLegend(g, result, map));
    }
}
