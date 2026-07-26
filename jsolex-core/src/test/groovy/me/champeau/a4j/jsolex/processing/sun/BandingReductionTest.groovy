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
package me.champeau.a4j.jsolex.processing.sun

import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

class BandingReductionTest extends Specification {

    def "destripe fills dark lines back to the background"() {
        given: "a uniform background with sparse dark lines (a throughput loss)"
        def width = 256
        def height = 256
        def data = new float[height][width]
        for (int y = 0; y < height; y++) {
            float value = (y % 16) < 2 ? 700f : 1000f
            for (int x = 0; x < width; x++) {
                data[y][x] = value
            }
        }

        when:
        BandingReduction.removeStripes(width, height, data, 32, null, BandingReduction.Mode.WHOLE_LINE)

        then: "the dark lines are lifted close to the background"
        double maxDeviation = 0
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                maxDeviation = Math.max(maxDeviation, Math.abs(data[y][x] - 1000f))
            }
        }
        maxDeviation < 60
        !hasNaN(data, width, height)
    }

    def "destripe removes banding of both polarities"() {
        given: "a background with per-row banding, both darker and brighter than the mean"
        def width = 256
        def height = 256
        def data = new float[height][width]
        for (int y = 0; y < height; y++) {
            float band = (float) (200 * Math.sin(y / 2.0))
            for (int x = 0; x < width; x++) {
                data[y][x] = 1000f + band
            }
        }

        when:
        BandingReduction.removeStripes(width, height, data, 32, null, BandingReduction.Mode.WHOLE_LINE)

        then: "the banding is flattened, away from the smoothing border"
        double residual = 0
        for (int y = 96; y < height - 96; y++) {
            for (int x = 0; x < width; x++) {
                residual = Math.max(residual, Math.abs(data[y][x] - 1000f))
            }
        }
        residual < 40
        !hasNaN(data, width, height)
    }

    def "destripe preserves a localized bright feature"() {
        given: "gentle banding coherent along the width, plus a bright feature filling the central strip"
        def width = 800
        def height = 256
        def data = new float[height][width]
        def original = new float[height][width]
        for (int y = 0; y < height; y++) {
            float band = (float) (150 * Math.sin(y / 2.0))
            for (int x = 0; x < width; x++) {
                float value = 1000f + band
                if (y >= 110 && y < 140 && x >= 300 && x < 500) {
                    value += 6000f
                }
                data[y][x] = value
                original[y][x] = value
            }
        }

        when:
        BandingReduction.removeStripes(width, height, data, 32, null, BandingReduction.Mode.WHOLE_LINE)

        then: "the banding is removed on a plain background column"
        double residual = 0
        for (int y = 96; y < height - 96; y++) {
            residual = Math.max(residual, Math.abs(data[y][50] - 1000f))
        }
        residual < 50

        and: "the bright feature is not carved out as banding"
        data[125][400] > original[125][400] - 800
    }

    def "destripe removes banding uniformly when its amplitude varies along the width"() {
        given: "banding whose amplitude grows towards the centre of the image, as around a solar disk"
        def width = 2048
        def height = 512
        def data = new float[height][width]
        def before = new double[8]
        for (int y = 0; y < height; y++) {
            double stripe = Math.sin(y / 3.0d) + 0.6d * Math.sin(y / 11.0d)
            for (int x = 0; x < width; x++) {
                double profile = Math.sin(Math.PI * x / (double) width)
                data[y][x] = (float) (30000 + 300 * profile * stripe)
            }
        }
        for (int b = 0; b < 8; b++) {
            before[b] = stripeAmplitude(data, (int) (b * width / 8), (int) ((b + 1) * width / 8), height)
        }

        when:
        BandingReduction.removeStripes(width, height, data, 192, null, BandingReduction.Mode.WHOLE_LINE)

        then: "every part of the width is corrected, including the edge strips"
        for (int b = 0; b < 8; b++) {
            double after = stripeAmplitude(data, (int) (b * width / 8), (int) ((b + 1) * width / 8), height)
            assert after < before[b] / 3 : "bin $b barely corrected: ${before[b]} -> $after"
        }
    }

    def "a single strip applies the same correction to the whole line"() {
        int width = 2048
        int height = 512
        float[][] data = new float[height][width]
        float[][] original = new float[height][width]
        for (int y = 0; y < height; y++) {
            double stripe = Math.sin(y / 3.0d) + 0.6d * Math.sin(y / 11.0d)
            for (int x = 0; x < width; x++) {
                double profile = Math.sin(Math.PI * x / (double) width)
                data[y][x] = (float) (30000 + 300 * profile * stripe)
                original[y][x] = data[y][x]
            }
        }
        BandingReduction.removeStripes(width, height, data, 192, null, BandingReduction.Mode.WHOLE_LINE, 1)

        expect: "the correction of a line does not depend on the abscissa"
        for (int y = 0; y < height; y++) {
            double reference = data[y][0] - original[y][0]
            for (int x = 0; x < width; x += 64) {
                assert Math.abs((data[y][x] - original[y][x]) - reference) < 0.01
            }
        }
    }

    private static double stripeAmplitude(float[][] data, int x0, int x1, int height) {
        def rowMean = new double[height]
        for (int y = 0; y < height; y++) {
            double s = 0
            for (int x = x0; x < x1; x++) { s += data[y][x] }
            rowMean[y] = s / (x1 - x0)
        }
        double acc = 0
        int n = 0
        for (int y = 5; y < height - 5; y++) {
            double local = 0
            for (int k = -5; k <= 5; k++) { local += rowMean[y + k] }
            acc += Math.pow(rowMean[y] - local / 11, 2)
            n++
        }
        return Math.sqrt(acc / n)
    }

    def "banding reduction produces valid results without ellipse"() {
        given:
        def width = 256
        def height = 256
        def data = createTestImage(width, height)

        when:
        BandingReduction.reduceBanding(width, height, data, BandingReduction.DEFAULT_BAND_SIZE, null, BandingReduction.Mode.WHOLE_LINE)

        then:
        !hasNaN(data, width, height)
        !hasInfinity(data, width, height)
    }

    def "banding reduction produces valid results with ellipse"() {
        given:
        def width = 256
        def height = 256
        def data = createTestImage(width, height)
        def ellipse = createTestEllipse(width, height)

        when:
        BandingReduction.reduceBanding(width, height, data, BandingReduction.DEFAULT_BAND_SIZE, ellipse, BandingReduction.Mode.INSIDE_DISK)

        then:
        !hasNaN(data, width, height)
        !hasInfinity(data, width, height)
    }

    def "banding reduction with different band sizes"() {
        given:
        def width = 256
        def height = 256
        def data = createTestImage(width, height)

        when:
        BandingReduction.reduceBanding(width, height, data, bandSize, null, BandingReduction.Mode.WHOLE_LINE)

        then:
        !hasNaN(data, width, height)
        !hasInfinity(data, width, height)

        where:
        bandSize << [8, 16, 24, 32, 48]
    }

    def "the automatic band size grows with the image height within bounds"() {
        expect:
        BandingReduction.autoBandSize(height) == expected

        where:
        height | expected
        256    | 16
        1024   | 32
        2048   | 64
        8192   | 128
    }

    def "converging passes remove banding and stop once the corrections stall"() {
        given: "a noisy background with banding at several scales"
        def width = 256
        def height = 256
        def data = new float[height][width]
        def random = new Random(42)
        for (int y = 0; y < height; y++) {
            float band = (float) (150 * Math.sin(y / 3.0) + 100 * Math.sin(y / 11.0))
            for (int x = 0; x < width; x++) {
                data[y][x] = (float) (1000f + band + 20 * random.nextGaussian())
            }
        }

        when:
        def passes = BandingReduction.removeStripesUntilConvergence(width, height, data, BandingReduction.autoBandSize(height), null, BandingReduction.Mode.WHOLE_LINE, 0)

        then: "the banding is gone and the iteration stopped on its own"
        passes >= 2
        passes <= 4
        stripeAmplitude(data, 0, width, height) < 15
        !hasNaN(data, width, height)
    }

    def "converging passes leave an image without banding essentially untouched"() {
        given: "pure noise"
        def width = 256
        def height = 256
        def data = new float[height][width]
        def before = new float[height][width]
        def random = new Random(42)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = (float) (1000f + 20 * random.nextGaussian())
                before[y][x] = data[y][x]
            }
        }

        when:
        BandingReduction.removeStripesUntilConvergence(width, height, data, BandingReduction.autoBandSize(height), null, BandingReduction.Mode.WHOLE_LINE, 0)

        then: "only the random per-line offsets were touched, a small fraction of the pixel noise"
        double maxChange = 0
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                maxChange = Math.max(maxChange, Math.abs(data[y][x] - before[y][x]))
            }
        }
        maxChange < 10
        !hasNaN(data, width, height)
    }

    def "the local stage removes short streaks while preserving a smooth feature"() {
        given: "a sky with thin streaks anchored near the disk and a smooth prominence-like feature"
        def width = 1024
        def height = 1024
        def ellipse = Ellipse.ofCartesian(new DoubleSextuplet(1, 0, 1, -1024, -1024, 1024 * 1024 / 2 - 300 * 300))
        def data = new float[height][width]
        def random = new Random(42)
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                data[y][x] = (float) (30000 + 300 * random.nextGaussian())
            }
        }
        def streakRows = [200, 240, 700, 760]
        for (int row in streakRows) {
            for (int y = row; y < row + 8; y++) {
                for (int x = 830; x < 1010; x++) {
                    if (!ellipse.isWithin(x, y)) {
                        data[y][x] += 250f
                    }
                }
            }
        }
        int fx = 850
        int fy = 512
        def featureTotal = 0d
        for (int y = fy - 90; y < fy + 90; y++) {
            for (int x = fx - 60; x < fx + 60; x++) {
                if (!ellipse.isWithin(x, y)) {
                    def g = 6000 * Math.exp(-((x - fx) * (x - fx)) / (2.0 * 20 * 20) - ((y - fy) * (y - fy)) / (2.0 * 30 * 30))
                    data[y][x] += (float) g
                    featureTotal += g
                }
            }
        }
        def before = new float[height][width]
        for (int y = 0; y < height; y++) {
            System.arraycopy(data[y], 0, before[y], 0, width)
        }

        when:
        BandingReduction.removeLocalStripes(width, height, data, 16, ellipse, BandingReduction.Mode.OUTSIDE_DISK)

        then: "the streaks lose most of their amplitude"
        for (int row in streakRows) {
            double residual = 0
            int n = 0
            for (int y = row; y < row + 8; y++) {
                for (int x = 840; x < 1000; x++) {
                    if (!ellipse.isWithin(x, y)) {
                        residual += data[y][x] - 30000
                        n++
                    }
                }
            }
            assert Math.abs(residual / n) < 100
        }

        and: "the smooth feature keeps its flux"
        double lost = 0
        for (int y = fy - 90; y < fy + 90; y++) {
            for (int x = fx - 60; x < fx + 60; x++) {
                if (!ellipse.isWithin(x, y)) {
                    lost += before[y][x] - data[y][x]
                }
            }
        }
        Math.abs(lost / featureTotal) < 0.02
        !hasNaN(data, width, height)
    }

    private static float[][] createTestImage(int width, int height) {
        var data = new float[height][width]
        var random = new Random(42)
        for (int y = 0; y < height; y++) {
            float rowBias = (float) (1000 * Math.sin(y * 0.1))
            for (int x = 0; x < width; x++) {
                data[y][x] = 30000 + rowBias + random.nextFloat() * 5000
            }
        }
        return data
    }

    private static Ellipse createTestEllipse(int width, int height) {
        double cx = width / 2.0
        double cy = height / 2.0
        double rx = width * 0.4
        double ry = height * 0.4
        double a = 1.0 / (rx * rx)
        double c = 1.0 / (ry * ry)
        double d = -2.0 * cx / (rx * rx)
        double e = -2.0 * cy / (ry * ry)
        double f = cx * cx / (rx * rx) + cy * cy / (ry * ry) - 1.0
        return Ellipse.ofCartesian(new DoubleSextuplet(a, 0, c, d, e, f))
    }

    private static boolean hasNaN(float[][] data, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Float.isNaN(data[y][x])) {
                    return true
                }
            }
        }
        return false
    }

    private static boolean hasInfinity(float[][] data, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (Float.isInfinite(data[y][x])) {
                    return true
                }
            }
        }
        return false
    }
}
