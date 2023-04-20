package me.champeau.a4j.jsolex.processing.stats

import groovy.transform.CompileStatic

import javax.imageio.ImageIO

@CompileStatic
class ImageWrapper {
    final double[] rgb
    final int width
    final int height

    ImageWrapper(double[] rgb, int width, int height) {
        this.rgb = rgb
        this.width = width
        this.height = height
    }

    static ImageWrapper load(String name) {
     def image = ImageIO.read(RGBImageStats.getResourceAsStream("/${name}.tif"))
     def rgb = new double[image.width * image.height * 3]
     for (int y = 0; y < image.height; y++) {
         for (int x = 0; x < image.width; x++) {
             int pixel = image.getRGB(x, y)
             double r = (pixel >> 16) & 0xFF
             double g = (pixel >> 8) & 0xFF
             double b = pixel & 0xFF
             int i = (y * image.width + x) * 3
             rgb[i] = r
             rgb[i + 1] = g
             rgb[i + 2] = b
         }
     }
     return new ImageWrapper(rgb, image.width, image.height)
 }
}
