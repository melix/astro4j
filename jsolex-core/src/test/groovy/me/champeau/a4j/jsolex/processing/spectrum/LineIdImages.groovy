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
package me.champeau.a4j.jsolex.processing.spectrum

import me.champeau.a4j.jsolex.processing.params.SpectralRay
import me.champeau.a4j.jsolex.processing.params.SpectroHeliograph
import me.champeau.a4j.jsolex.processing.sun.SpectralLineRealigner
import me.champeau.a4j.jsolex.processing.sun.SpectrumFrameAnalyzer
import me.champeau.a4j.jsolex.processing.util.FitsUtils
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32
import me.champeau.a4j.jsolex.processing.util.Wavelen
import spock.lang.IgnoreIf
import spock.lang.Specification

import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

/**
 * Renders debug PNGs of the line realignment into the directory given by the
 * {@code LINE_ID_OUT} environment variable. Development aid, skipped otherwise.
 */
@IgnoreIf({ System.getenv('LINE_ID_OUT') == null })
class LineIdImages extends Specification {

    private static final Wavelen FE_5302 = Wavelen.ofAngstroms(5302.3d)
    private static final SpectralRay FE_5302_RAY = new SpectralRay('Fe 5302', null, FE_5302, false, [])
    private static final SpectrumAnalyzer.QueryDetails DETAILS =
            new SpectrumAnalyzer.QueryDetails(FE_5302_RAY, 2.0d, 1, SpectroHeliograph.MLASTRO_SHG_700)

    def "renders the average image, the detected line and the realigned line"() {
        given:
        def out = new File(System.getenv('LINE_ID_OUT'))
        out.mkdirs()
        def file = new File(LineIdImages.getResource('/lineid/fe5302-misidentified.fits').toURI())
        def image = (ImageWrapper32) FitsUtils.readFitsFile(file)
        int width = image.width()
        int height = image.height()
        def data = image.data()

        when:
        def analysis = new SpectrumFrameAnalyzer(width, height, false, null).analyze(data)
        def detectedPolynomial = analysis.distortionPolynomial().get()
        def realigned = SpectralLineRealigner.realign(data, width, height, false, analysis, DETAILS)
        def realignedPolynomial = realigned.distortionPolynomial().get()

        def dispersion = SpectrumAnalyzer.computeSpectralDispersion(DETAILS.instrument(), FE_5302, DETAILS.pixelSize() * DETAILS.binning())
        def profile = SpectrumAnalyzer.computeDataPoints(DETAILS, detectedPolynomial,
                analysis.leftBorder().orElse(0), analysis.rightBorder().orElse(width), width, height, data)
        def located = SpectralLineLocator.locate(profile, FE_5302, dispersion, height / 2d).get()

        def base = render(data, width, height)
        ImageIO.write(base, 'png', new File(out, '01-average.png'))

        def detected = copy(base)
        drawCurve(detected, detectedPolynomial, width, height, Color.RED)
        label(detected, 'detected: darkest line of the window', Color.RED, 4)
        ImageIO.write(detected, 'png', new File(out, '02-detected-line.png'))

        def fixed = copy(base)
        drawCurve(fixed, detectedPolynomial, width, height, new Color(255, 80, 80))
        drawCurve(fixed, realignedPolynomial, width, height, new Color(0, 255, 0))
        label(fixed, String.format(Locale.US,
                'red = detected   green = realigned on Fe 5302.3 (%+.1f px, match %.4f)',
                located.pixelOffset(), located.score()), Color.WHITE, 4)
        ImageIO.write(fixed, 'png', new File(out, '03-realigned.png'))
        ImageIO.write(crop(fixed, 1500, 0, 700, height, 2, 4), 'png', new File(out, '04-realigned-zoom.png'))

        ImageIO.write(profileChart(profile, located, dispersion), 'png', new File(out, '05-profile-match.png'))

        then:
        println String.format(Locale.US, 'offset %+.2f px, score %.4f, margin %.4f, sigma %.1f px',
                located.pixelOffset(), located.score(), located.margin(), located.sigmaPixels())
        out.listFiles().sort { it.name }.each { println "  ${it.name}" }
        true
    }

    /**
     * Absorption lines are only a few percent deep on a bright continuum, so a
     * plain min/max stretch shows nothing. Divide every column by its own upper
     * quartile and map the resulting [0.75, 1.0] band over the full range.
     */
    private static BufferedImage render(float[][] data, int width, int height) {
        def img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        for (int x = 0; x < width; x++) {
            def col = new double[height]
            for (int y = 0; y < height; y++) {
                col[y] = data[y][x]
            }
            def sorted = col.clone()
            Arrays.sort(sorted)
            double continuum = sorted[(int) (height * 0.75d)]
            for (int y = 0; y < height; y++) {
                double ratio = continuum > 1e-6d ? col[y] / continuum : 1d
                int v = (int) Math.round(255d * Math.max(0d, Math.min(1d, (ratio - 0.75d) / 0.25d)))
                img.setRGB(x, y, (v << 16) | (v << 8) | v)
            }
        }
        return img
    }

    private static BufferedImage copy(BufferedImage src) {
        def out = new BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB)
        def g = out.createGraphics()
        g.drawImage(src, 0, 0, null)
        g.dispose()
        return out
    }

    private static void drawCurve(BufferedImage img, polynomial, int width, int height, Color color) {
        for (int x = 0; x < width; x++) {
            int y = (int) Math.round(polynomial.applyAsDouble(x))
            if (y >= 0 && y < height) {
                img.setRGB(x, y, color.getRGB())
            }
        }
    }

    private static void label(BufferedImage img, String text, Color color, int y) {
        def g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14))
        g.setColor(Color.BLACK)
        g.drawString(text, 9, y + 16)
        g.setColor(color)
        g.drawString(text, 8, y + 15)
        g.dispose()
    }

    private static BufferedImage crop(BufferedImage src, int x, int y, int w, int h, int sx, int sy) {
        def out = new BufferedImage(w * sx, h * sy, BufferedImage.TYPE_INT_RGB)
        def g = out.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.drawImage(src.getSubimage(x, y, w, h), 0, 0, w * sx, h * sy, null)
        g.dispose()
        return out
    }

    private static BufferedImage profileChart(List<SpectrumAnalyzer.DataPoint> profile,
                                              SpectralLineLocator.Result located,
                                              dispersion) {
        int w = 1200
        int h = 500
        def shifts = profile.collect { it.pixelShift() }
        def maxIntensity = profile.collect { it.intensity() }.max()
        def observed = profile.collect { it.intensity() / maxIntensity }
        def img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        def g = img.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setColor(new Color(24, 24, 28))
        g.fillRect(0, 0, w, h)
        def lo = observed.min() - 0.03d
        def hi = 1.02d
        def px = { int i -> (int) (60 + (w - 90) * (i / (double) (shifts.size() - 1))) }
        def py = { double v -> (int) (h - 60 - (h - 100) * ((v - lo) / (hi - lo))) }
        def indexOf = { double shift ->
            int best = 0
            double bestDistance = Double.MAX_VALUE
            shifts.eachWithIndex { s, i ->
                if (Math.abs(s - shift) < bestDistance) {
                    bestDistance = Math.abs(s - shift); best = i
                }
            }
            best
        }

        g.setStroke(new BasicStroke(2f))
        g.setColor(new Color(230, 60, 60, 150))
        g.drawLine(px.call(indexOf.call(0d)), 40, px.call(indexOf.call(0d)), h - 60)
        g.setColor(new Color(0, 210, 0, 150))
        g.drawLine(px.call(indexOf.call(located.pixelOffset())), 40, px.call(indexOf.call(located.pixelOffset())), h - 60)

        g.setStroke(new BasicStroke(2.2f))
        g.setColor(new Color(120, 190, 255))
        for (int i = 1; i < shifts.size(); i++) {
            g.drawLine(px.call(i - 1), py.call(observed[i - 1]), px.call(i), py.call(observed[i]))
        }

        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15))
        g.setColor(new Color(120, 190, 255))
        g.drawString('observed profile along the initial polynomial', 70, 30)
        g.setColor(new Color(230, 60, 60))
        g.drawString(String.format(Locale.US, 'detected line (%.2f A)',
                FE_5302.angstroms() - located.pixelOffset() * dispersion.angstromsPerPixel()),
                px.call(indexOf.call(0d)) + 6, h - 40)
        g.setColor(new Color(0, 210, 0))
        g.drawString(String.format(Locale.US, 'Fe 5302.30 A (%+.1f px)', located.pixelOffset()),
                px.call(indexOf.call(located.pixelOffset())) + 6, h - 20)
        g.dispose()
        return img
    }
}
