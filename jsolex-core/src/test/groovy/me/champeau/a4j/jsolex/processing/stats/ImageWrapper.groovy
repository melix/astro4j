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
package me.champeau.a4j.jsolex.processing.stats

import groovy.transform.CompileStatic

import javax.imageio.ImageIO

@CompileStatic
class ImageWrapper {
    final float[] rgb
    final int width
    final int height

    ImageWrapper(float[] rgb, int width, int height) {
        this.rgb = rgb
        this.width = width
        this.height = height
    }

    static ImageWrapper load(String name) {
     def image = ImageIO.read(RGBImageStats.getResourceAsStream("/${name}.tif"))
     def rgb = new float[image.width * image.height * 3]
     for (int y = 0; y < image.height; y++) {
         for (int x = 0; x < image.width; x++) {
             int pixel = image.getRGB(x, y)
             double r = (pixel >> 16) & 0xFF
             double g = (pixel >> 8) & 0xFF
             double b = pixel & 0xFF
             int i = (y * image.width + x) * 3
             rgb[i] = (float) r
             rgb[i + 1] = (float) g
             rgb[i + 2] = (float) b
         }
     }
     return new ImageWrapper(rgb, image.width, image.height)
 }
}
