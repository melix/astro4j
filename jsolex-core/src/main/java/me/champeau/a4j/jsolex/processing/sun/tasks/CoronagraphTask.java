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

import me.champeau.a4j.jsolex.processing.stretching.ArcsinhStretchingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

public class CoronagraphTask extends AbstractTask<ImageWrapper32> {
    private final EllipseFittingTask.Result fitting;
    private final float blackPoint;

    public CoronagraphTask(Broadcaster broadcaster,
                           ImageWrapper32 image,
                           EllipseFittingTask.Result fitting,
                           float blackPoint) {
        super(broadcaster, image);
        this.fitting = fitting;
        this.blackPoint = blackPoint;
    }

    @Override
    public ImageWrapper32 call() throws Exception {
        var ellipse = fitting.ellipse();
        fill(ellipse, buffer, width, 0);
        new ArcsinhStretchingStrategy(blackPoint * .25f, 5000, 20000).stretch(buffer);
        return new ImageWrapper32(width, height, buffer);

    }

    private static void fill(Ellipse ellipse, float[] image, int width, int color) {
        int height = image.length / width;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (ellipse.isWithin(x, y)) {
                    image[x + y * width] = color;
                }
            }
        }
    }

}
