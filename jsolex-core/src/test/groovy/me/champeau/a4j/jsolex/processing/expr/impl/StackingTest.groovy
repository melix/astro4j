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
package me.champeau.a4j.jsolex.processing.expr.impl

import groovy.transform.CompileStatic
import me.champeau.a4j.jsolex.processing.util.ImageIOUtils
import me.champeau.a4j.math.fft.FFTSupport
import me.champeau.a4j.math.image.Image
import me.champeau.a4j.math.image.ImageMath
import me.champeau.a4j.math.image.Kernel33
import me.champeau.a4j.math.tuples.DoublePair
import spock.lang.Specification

import javax.imageio.ImageIO
import java.awt.image.BufferedImage

import static java.lang.Math.round

class StackingTest extends Specification {
    private static final Image REFERENCE = ImageIOUtils.loadImage("meudon-ref.png")
    private static final TOP = ImageIOUtils.loadImage("mosaic_top.png")
    private static final MIDDLE = ImageIOUtils.loadImage("mosaic_middle.png")

    private static final int WIDTH = REFERENCE.width()
    private static final int HEIGHT = REFERENCE.height()

    def "finds best tile by translation"() {
        var translated = translate(REFERENCE, dx, dy)
        var refX = 1000
        var refY = 400
        var lookup = 32

        when:
        var tiles = Dedistort.createTilesForComparison(lookup, refX, WIDTH, refY, HEIGHT, REFERENCE.data(), translated.data(), 0f)
        var best = Dedistort.bestShiftFFT(tiles.referenceTile(), tiles.dataTile())

        then:
        round(best.a()) == -dy
        round(best.b()) == -dx

        where:
        [dx, dy] << [(-5..5), (-5..5)].combinations()
    }

    def "finds best tile by translation and blur"() {
        var translated = ImageMath.newInstance().convolve(translate(REFERENCE, dx, dy), Kernel33.GAUSSIAN_BLUR)
        var refX = 1000
        var refY = 400
        int lookup = 32

        when:
        var tiles = Dedistort.createTilesForComparison(lookup, refX, WIDTH, refY, HEIGHT, REFERENCE.data(), translated.data(), 0f)
        var best = Dedistort.bestShiftFFT(tiles.referenceTile(), tiles.dataTile())

        then:
        round(best.a()) == -dy
        round(best.b()) == -dx

        where:
        [dx, dy] << [(-5..5), (-5..5)].combinations()
    }

    def "finds best tile by translation and sharpen"() {
        var translated = ImageMath.newInstance().convolve(translate(REFERENCE, dx, dy), Kernel33.SHARPEN)
        var refX = 1000
        var refY = 400
        var lookup = 16

        when:
        var tiles = Dedistort.createTilesForComparison(lookup, refX, WIDTH, refY, HEIGHT, REFERENCE.data(), translated.data(), 0f)
        var best = Dedistort.bestShiftFFT(tiles.referenceTile(), tiles.dataTile())

        then:
        round(best.a()) == -dy
        round(best.b()) == -dx

        where:
        [dx, dy] << [(-5..5), (-5..5)].combinations()
    }

    def "finds correspondance between 2 images"() {

        def width = TOP.width()
        def height = TOP.height()

        when:
        var tiles = Dedistort.createTilesForComparison(tileSize, refX, width, refY, height, TOP.data(), MIDDLE.data(), 0f)
        var best = Dedistort.bestShiftFFT(tiles.referenceTile(), tiles.dataTile())

        def actualY = refY - best.a()
        def actualX = refX - best.b()
        def yOk = withinTolerance(actualY, expectedY, tolerance)
        def xOk = withinTolerance(actualX, expectedX, tolerance)

        if (!yOk || !xOk) {
            saveDiagnosticImage(tiles.referenceTile(), tiles.dataTile(), best,
                    "diagnostic_${refX}_${refY}_tile${tileSize}.png")
        }

        then:
        verifyAll {
            yOk
            xOk
        }

        where:
        // Note: for tileSize N, maximum detectable shift is N/2 pixels
        refX | refY | tileSize | expectedX | expectedY | tolerance
        1030 | 1087 | 16       | 1031      | 1078      | 4          // Y shift=9, borderline but works
        1030 | 1087 | 32       | 1031      | 1078      | 1
        1030 | 1087 | 64       | 1031      | 1078      | 1
        283  | 1137 | 32       | 285       | 1127      | 1
        283  | 1137 | 64       | 285       | 1127      | 1
        682  | 739  | 64       | 684       | 734       | 1
        1894 | 872  | 64       | 1893      | 864       | 1.5
        480  | 1008 | 32       | 482       | 999       | 1
        480  | 1008 | 64       | 482       | 999       | 1
    }

    private static boolean withinTolerance(double a, double b, double tolerance) {
        Math.abs(a - b) <= tolerance
    }

    private static void saveDiagnosticImage(float[][] refTile, float[][] dataTile,
                                            DoublePair shift, String filename) {
        int size = refTile.length
        // 5 panels: ref, data, overlay, cross-correlation, phase-correlation
        int outputWidth = size * 5 + 8  // 5 tiles + 4 pixel gaps
        int outputHeight = size

        def image = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_RGB)

        // Find min/max for tile normalization
        float tileMin = Float.MAX_VALUE
        float tileMax = Float.MIN_VALUE
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                tileMin = Math.min(tileMin, Math.min(refTile[y][x], dataTile[y][x]))
                tileMax = Math.max(tileMax, Math.max(refTile[y][x], dataTile[y][x]))
            }
        }
        float tileRange = tileMax - tileMin
        if (tileRange < 1e-6f) {
            tileRange = 1f
        }

        // Panel 1: reference tile (grayscale)
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int v = (int) (255 * (refTile[y][x] - tileMin) / tileRange)
                v = Math.max(0, Math.min(255, v))
                image.setRGB(x, y, (v << 16) | (v << 8) | v)
            }
        }

        // Panel 2: data tile (grayscale)
        int offset2 = size + 2
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int v = (int) (255 * (dataTile[y][x] - tileMin) / tileRange)
                v = Math.max(0, Math.min(255, v))
                image.setRGB(offset2 + x, y, (v << 16) | (v << 8) | v)
            }
        }

        // Panel 3: overlay with computed shift (Red=ref, Blue=shifted data, Green=mean)
        int offset3 = size * 2 + 4
        double shiftY = -shift.a()
        double shiftX = -shift.b()

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int refVal = (int) (255 * (refTile[y][x] - tileMin) / tileRange)
                refVal = Math.max(0, Math.min(255, refVal))

                int dataVal = 0
                int srcX = (int) round(x + shiftX)
                int srcY = (int) round(y + shiftY)
                if (srcX >= 0 && srcX < size && srcY >= 0 && srcY < size) {
                    dataVal = (int) (255 * (dataTile[srcY][srcX] - tileMin) / tileRange)
                    dataVal = Math.max(0, Math.min(255, dataVal))
                }

                int green = (int) ((refVal + dataVal) / 2)
                image.setRGB(offset3 + x, y, (refVal << 16) | (green << 8) | dataVal)
            }
        }

        // Compute cross-correlation surface
        def fftRef = FFTSupport.fft2Float(refTile)
        def fftDef = FFTSupport.fft2Float(dataTile)
        def crossCorr = Dedistort.fftShift(FFTSupport.crossCorrelationFloat(fftRef, fftDef))
        def crossPeak = Dedistort.findMaxIndex(crossCorr)

        // Compute phase correlation surface
        def window = createHannWindow(size)
        def windowedRef = applyWindow(refTile, window)
        def windowedDef = applyWindow(dataTile, window)
        def fftRefW = FFTSupport.fft2Float(windowedRef)
        def fftDefW = FFTSupport.fft2Float(windowedDef)

        int rows = fftRefW.real.length
        int cols = fftRefW.real[0].length
        def realResult = new float[rows][cols]
        def imagResult = new float[rows][cols]

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                float refR = fftRefW.real[i][j]
                float refI = fftRefW.imaginary[i][j]
                float defR = fftDefW.real[i][j]
                float defI = fftDefW.imaginary[i][j]

                float crossR = refR * defR + refI * defI
                float crossI = refI * defR - refR * defI

                float magSq = crossR * crossR + crossI * crossI
                if (magSq > 1e-20f) {
                    float mag = (float) Math.sqrt(magSq)
                    realResult[i][j] = crossR / mag
                    imagResult[i][j] = crossI / mag
                }
            }
        }
        def phaseCorr = Dedistort.fftShift(FFTSupport.ifft2Float(new FFTSupport.FloatFFT2DResult(realResult, imagResult)))
        def phasePeak = Dedistort.findMaxIndex(phaseCorr)

        // Panel 4: cross-correlation surface
        int offset4 = size * 3 + 6
        drawCorrelationPanel(image, crossCorr.real, offset4, crossPeak, size)

        // Panel 5: phase correlation surface
        int offset5 = size * 4 + 8
        drawCorrelationPanel(image, phaseCorr.real, offset5, phasePeak, size)

        // Save to build directory
        def outputDir = new File("build/test-diagnostics")
        outputDir.mkdirs()
        ImageIO.write(image, "PNG", new File(outputDir, filename))

        double center = size / 2.0
        println "Diagnostic image saved: ${new File(outputDir, filename).absolutePath}"
        println "  Cross-correlation peak at (${crossPeak[1]}, ${crossPeak[0]}) -> shift (${crossPeak[1] - center}, ${crossPeak[0] - center})"
        println "  Phase correlation peak at (${phasePeak[1]}, ${phasePeak[0]}) -> shift (${phasePeak[1] - center}, ${phasePeak[0] - center})"
    }

    private static void drawCorrelationPanel(BufferedImage image, float[][] surface, int xOffset, int[] peak, int size) {
        // Find min/max for normalization
        float min = Float.MAX_VALUE
        float max = Float.MIN_VALUE
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                min = Math.min(min, surface[y][x])
                max = Math.max(max, surface[y][x])
            }
        }
        float range = max - min
        if (range < 1e-10f) {
            range = 1f
        }

        // Draw surface as grayscale
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int v = (int) (255 * (surface[y][x] - min) / range)
                v = Math.max(0, Math.min(255, v))
                image.setRGB(xOffset + x, y, (v << 16) | (v << 8) | v)
            }
        }

        // Mark peak with red cross
        int peakX = peak[1]
        int peakY = peak[0]
        for (int d = -2; d <= 2; d++) {
            if (peakX + d >= 0 && peakX + d < size) {
                image.setRGB(xOffset + peakX + d, peakY, 0xFF0000)
            }
            if (peakY + d >= 0 && peakY + d < size) {
                image.setRGB(xOffset + peakX, peakY + d, 0xFF0000)
            }
        }

        // Mark center with green dot
        int center = size / 2
        image.setRGB(xOffset + center, center, 0x00FF00)
    }

    private static float[][] createHannWindow(int size) {
        def window = new float[size][size]
        def weights1D = new float[size]
        double scale = 2 * Math.PI / (size - 1)
        for (int i = 0; i < size; i++) {
            weights1D[i] = 0.5f * (1 - (float) Math.cos(scale * i))
        }
        for (int y = 0; y < size; y++) {
            float wy = weights1D[y]
            for (int x = 0; x < size; x++) {
                window[y][x] = weights1D[x] * wy
            }
        }
        return window
    }

    private static float[][] applyWindow(float[][] tile, float[][] window) {
        int size = tile.length
        def result = new float[size][size]
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                result[y][x] = tile[y][x] * window[y][x]
            }
        }
        return result
    }

    @CompileStatic
    private static Image translate(Image image, int dx, int dy) {
        var refW = image.width()
        var refH = image.height()
        var translated = new float[refH][refW]
        def source = image.data()
        for (int x = 0; x < refW; x++) {
            for (int y = 0; y < refH; y++) {
                def xx = x + dx
                def yy = y + dy
                if (xx >= 0 && xx < refW && yy >= 0 && yy < refH) {
                    translated[yy][xx] = source[y][x]
                }
            }
        }
        new Image(refW, refH, translated)
    }
}
