/*
 * Copyright 2023-2026 the original author or authors.
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

import me.champeau.a4j.jsolex.processing.sun.Broadcaster
import me.champeau.a4j.jsolex.processing.util.FitsUtils
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.RGBImage
import me.champeau.a4j.math.regression.Ellipse
import me.champeau.a4j.math.tuples.DoubleSextuplet
import spock.lang.Specification

import javax.imageio.ImageIO
import java.awt.image.BufferedImage

class DeghostTest extends Specification {

    static final int SIZE = 400
    static final double CX = 200
    static final double CY = 200
    static final double R = 120
    // amplitude of the synthetic reflection, a same-radius disk shifted to the right in single-ghost.png
    static final float GHOST = 800

    private static double background(double x, double y) {
        double r = Math.hypot(x - CX, y - CY)
        return 100 + 1500 * Math.exp(-Math.max(0, r - R) / 30.0)
    }

    private static float[][] loadData(String resource) {
        def image = ImageIO.read(DeghostTest.getResourceAsStream(resource))
        def raster = image.raster
        float[][] data = new float[image.height][image.width]
        for (int y = 0; y < image.height; y++) {
            for (int x = 0; x < image.width; x++) {
                data[y][x] = raster.getSample(x, y, 0)
            }
        }
        return data
    }

    private static void saveRenders(String name, float[][] before, float[][] after) {
        float min = Float.MAX_VALUE
        float max = -Float.MAX_VALUE
        for (float[] row : before) {
            for (float v : row) {
                min = Math.min(min, v)
                max = Math.max(max, v)
            }
        }
        def dir = new File('build/deghost-test-images')
        dir.mkdirs()
        [(name + '-before.png'): before, (name + '-after.png'): after].each { file, data ->
            def img = new BufferedImage(data[0].length, data.length, BufferedImage.TYPE_BYTE_GRAY)
            def raster = img.raster
            for (int y = 0; y < data.length; y++) {
                for (int x = 0; x < data[0].length; x++) {
                    int v = (int) (255 * (data[y][x] - min) / (max - min))
                    raster.setSample(x, y, 0, Math.min(255, Math.max(0, v)))
                }
            }
            ImageIO.write(img, 'png', new File(dir, file))
        }
    }

    private static Ellipse circle(double cx, double cy, double radius) {
        double a = 1.0 / (radius * radius), c = 1.0 / (radius * radius)
        double d = -2.0 * cx / (radius * radius), e = -2.0 * cy / (radius * radius)
        double f = cx * cx / (radius * radius) + cy * cy / (radius * radius) - 1.0
        Ellipse.ofCartesian(new DoubleSextuplet(a, 0, c, d, e, f))
    }

    private static Ellipse diskEllipse() {
        circle(CX, CY, R)
    }

    private static ImageWrapper32 imageFrom(String resource, Ellipse ellipse) {
        def data = loadData(resource)
        def meta = new HashMap<Class<?>, Object>()
        meta.put(Ellipse.class, ellipse)
        return new ImageWrapper32(data[0].length, data.length, data, meta)
    }

    private ImageWrapper32 syntheticImage() {
        imageFrom('/deghost/single-ghost.png', diskEllipse())
    }

    private ImageWrapper32 backgroundImage() {
        imageFrom('/deghost/background.png', diskEllipse())
    }

    // reflection side (326,200) minus its clean mirror (74,200), both at radius 126
    private static double crescentExcess(float[][] data) {
        return data[200][326] - data[200][74]
    }

    private static Deghost deghost() {
        new Deghost([:] as Map<Class<?>, Object>, Broadcaster.NO_OP)
    }

    def "attenuates the reflection while preserving the sharp feature and untouched regions"() {
        given:
        def before = syntheticImage().data()

        when:
        def after = (deghost().deghost([img: syntheticImage()]) as ImageWrapper32).data()
        saveRenders('single-ghost', before, after)

        then: "the reflection band is strongly attenuated"
        double excessBefore = crescentExcess(before)
        double excessAfter = crescentExcess(after)
        excessBefore > 600
        excessAfter < 0.4 * excessBefore

        and: "the sharp feature remains a local peak (fine detail preserved)"
        after[215][330] > after[209][330] + 80
        after[215][330] > after[221][330] + 80

        and: "no reflection-side pixel is dug catastrophically below the background (no hard dark ring)"
        // the synthetic ghost has sharp azimuthal edges the smoothing over-shoots slightly; a real ring is thousands deep
        double worst = Double.POSITIVE_INFINITY
        for (int y = 40; y < SIZE - 40; y++) {
            for (int x = (int) CX; x < SIZE; x++) {
                double r = Math.hypot(x - CX, y - CY)
                if (r > R && r < 1.3 * R) {
                    worst = Math.min(worst, after[y][x] - background(x, y))
                }
            }
        }
        worst > -0.35 * GHOST

        and: "the anti-reflection side and the interior are left untouched"
        Math.abs(after[200][74] - before[200][74]) < 1e-3
        Math.abs(after[100][100] - before[100][100]) < 1e-3
    }

    def "attenuates two reflections on opposite sides without digging dark rings"() {
        given: "two same-radius disk copies: one shifted far left (beyond the old fixed removal extent), one shifted right-down"
        double aAmp = 600
        def image = imageFrom('/deghost/two-ghosts.png', diskEllipse())
        def before = image.copy().data()

        when:
        def after = (deghost().deghost([img: image]) as ImageWrapper32).data()
        saveRenders('two-ghosts', before, after)

        then: "the left crescent, the right crescent and the outer part of the left crescent are all strongly attenuated"
        for (point in [[74, 200], [270, 305], [44, 200]]) {
            int x = point[0], y = point[1]
            double excessBefore = before[y][x] - background(x, y)
            double excessAfter = after[y][x] - background(x, y)
            assert excessBefore > 200
            assert excessAfter < 0.4 * excessBefore
        }

        and: "no annulus pixel is dug deeply below the background (no dark ring)"
        double worst = Double.POSITIVE_INFINITY
        for (int y = 10; y < SIZE - 10; y++) {
            for (int x = 10; x < SIZE - 10; x++) {
                double r = Math.hypot(x - CX, y - CY)
                if (r > R && r < 1.45 * R) {
                    worst = Math.min(worst, after[y][x] - background(x, y))
                }
            }
        }
        worst > -0.35 * aAmp

        and: "the interior is left untouched"
        Math.abs(after[200][200] - before[200][200]) < 1e-3
        Math.abs(after[140][260] - before[140][260]) < 1e-3
    }

    def "removes the reflections of a real batch average"() {
        given: "a real stacked series with two disk reflections, one large towards the upper left, a thinner one towards the lower right"
        def image = imageFrom('/deghost/real-batch-average.png', circle(520.1, 519.9, 347.65))
        def before = image.copy().data()

        when:
        def after = (deghost().deghost([img: image]) as ImageWrapper32).data()
        saveRenders('real-batch', before, after)

        then: "both reflection crescents are strongly attenuated"
        meanWindow(before, 188, 333) - meanWindow(after, 188, 333) > 300
        meanWindow(before, 882, 597) - meanWindow(after, 882, 597) > 100

        and: "a region beyond the processed annulus is untouched"
        Math.abs(meanWindow(before, 1000, 100) - meanWindow(after, 1000, 100)) < 1e-3

        and: "no pixel is brightened"
        double maxIncrease = 0
        for (int y = 0; y < after.length; y++) {
            for (int x = 0; x < after[0].length; x++) {
                maxIncrease = Math.max(maxIncrease, after[y][x] - before[y][x])
            }
        }
        maxIncrease < 1e-3
    }

    def "attenuates the reflections of a batch average stored as FITS with its metadata"() {
        given: "the same stacked series as a FITS file, which preserves ADUs and embeds the fitted ellipse"
        def file = File.createTempFile('deghost', '.fits')
        file.deleteOnExit()
        DeghostTest.getResourceAsStream('/deghost/real-batch-average.fits').withCloseable { input ->
            file.withOutputStream { it << input }
        }
        def image = FitsUtils.readFitsFile(file) as ImageWrapper32
        def before = image.copy().data()

        when:
        def after = (deghost().deghost([img: image]) as ImageWrapper32).data()
        saveRenders('real-batch-fits', before, after)

        then:
        image.findMetadata(Ellipse).isPresent()
        assertReflectionRemoved(before, after, 30, 30)
    }

    def "attenuates the reflections of a stacked corona image with prominences"() {
        given:
        def image = imageFrom('/deghost/ghosted-2026-07-05.jpg', circle(833.2, 832.9, 692.2))
        def before = image.copy().data()

        when:
        def after = (deghost().deghost([img: image]) as ImageWrapper32).data()
        saveRenders('ghosted-2026-07-05', before, after)

        then:
        assertReflectionRemoved(before, after, 30, 30)
    }

    def "attenuates the reflections of a stacked corona image with a tilted frame"() {
        given:
        def image = imageFrom('/deghost/ghosted-2026-07-19.jpg', circle(999.9, 1000.5, 666.4))
        def before = image.copy().data()

        when:
        def after = (deghost().deghost([img: image]) as ImageWrapper32).data()
        saveRenders('ghosted-2026-07-19', before, after)

        then:
        assertReflectionRemoved(before, after, 30, 30)
    }

    private static void assertReflectionRemoved(float[][] before, float[][] after, int cornerX, int cornerY) {
        double removed = 0
        double maxIncrease = 0
        for (int y = 0; y < after.length; y++) {
            for (int x = 0; x < after[0].length; x++) {
                removed += before[y][x] - after[y][x]
                maxIncrease = Math.max(maxIncrease, after[y][x] - before[y][x])
            }
        }
        assert removed > 0: "expected a reflection to be detected and removed"
        assert maxIncrease < 1e-3
        assert Math.abs(meanWindow(before, cornerX, cornerY) - meanWindow(after, cornerX, cornerY)) < 1e-3
    }

    private static double meanWindow(float[][] data, int cx, int cy) {
        double sum = 0
        for (int y = cy - 3; y <= cy + 3; y++) {
            for (int x = cx - 3; x <= cx + 3; x++) {
                sum += data[y][x]
            }
        }
        return sum / 49
    }

    def "strength 0 is a no-op"() {
        given:
        def before = syntheticImage().data()

        when:
        def after = (deghost().deghost([img: syntheticImage(), strength: 0d]) as ImageWrapper32).data()

        then:
        Math.abs(after[200][335] - before[200][335]) < 1e-3
    }

    def "more iterations attenuate the reflection further"() {
        given:
        double before = crescentExcess(syntheticImage().data())

        when:
        double one = crescentExcess((deghost().deghost([img: syntheticImage(), strength: 0.5d, iterations: 1]) as ImageWrapper32).data())
        double three = crescentExcess((deghost().deghost([img: syntheticImage(), strength: 0.5d, iterations: 3]) as ImageWrapper32).data())

        then:
        one < before
        three < one
        three > 0
    }

    def "no reflection leaves the image unchanged"() {
        given:
        def before = backgroundImage().data()

        when:
        def after = (deghost().deghost([img: backgroundImage()]) as ImageWrapper32).data()
        saveRenders('background', before, after)

        then:
        Math.abs(after[200][326] - before[200][326]) < 1e-3
        Math.abs(after[100][100] - before[100][100]) < 1e-3
    }

    def "debug shows the estimated reflection instead of subtracting it"() {
        when:
        def dbg = (deghost().deghost([img: syntheticImage(), debug: 1]) as ImageWrapper32).data()
        saveRenders('single-ghost-debug', syntheticImage().data(), dbg)

        then: "the reflection side carries the estimate, the anti-reflection side is zero"
        dbg[200][326] > 300
        dbg[200][74] < 1e-3
    }

    def "handles a disk large relative to the frame (sampling off-image) without crashing"() {
        given: "a disk whose sampling radius runs off the frame edges"
        def image = imageFrom('/deghost/large-disk.png', circle(150, 150, 130))
        def before = image.copy().data()

        when:
        def result = deghost().deghost([img: image])
        saveRenders('large-disk', before, (result as ImageWrapper32).data())

        then:
        noExceptionThrown()
        result instanceof ImageWrapper32
    }

    def "rejects RGB images"() {
        given:
        def plane = new float[SIZE][SIZE]
        def rgb = new RGBImage(SIZE, SIZE, plane, plane, plane, new HashMap<Class<?>, Object>())

        when:
        deghost().deghost([img: rgb])

        then:
        thrown(IllegalArgumentException)
    }
}
