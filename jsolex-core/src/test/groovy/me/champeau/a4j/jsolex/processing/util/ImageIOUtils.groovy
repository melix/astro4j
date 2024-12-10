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
package me.champeau.a4j.jsolex.processing.util

import groovy.transform.CompileStatic
import me.champeau.a4j.jsolex.processing.sun.ImageUtils
import me.champeau.a4j.math.image.Image

import javax.imageio.ImageIO

@CompileStatic
class ImageIOUtils {
    static Image loadImage(String name) {
        var img = ImageIO.read(ImageUtils.getResource(name))
        var refW = img.width
        var refH = img.height
        var loaded = new float[refH][refW]
        for (int x = 0; x < refW; x++) {
            for (int y = 0; y < refH; y++) {
                loaded[y][x] = (img.getRGB(x, y) & 0xFF) << 8
            }
        }
        new Image(refW, refH, loaded)
    }
}
