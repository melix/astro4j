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

import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.math.regression.Ellipse;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import static java.lang.Math.cos;
import static java.lang.Math.round;
import static java.lang.Math.sin;
import static java.lang.Math.toRadians;

public class ImageDraw extends AbstractFunctionImpl {

    private static final int DIVISIONS = 18;

    public ImageDraw(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    private static StringBuilder appendLine(String element, StringBuilder sb) {
        if (element != null && !element.trim().isBlank()) {
            sb.append(element).append("\n");
        }
        return sb;
    }

    private static void writeMultiline(Graphics2D g, CharSequence seq, int x, int y) {
        var yb = y;
        var lineHeight = g.getFontMetrics().getHeight();
        var f = g.getFont();
        for (String s : seq.toString().split("\n")) {
            if (s.startsWith("<b>")) {
                g.setFont(f.deriveFont(Font.BOLD));
                s = s.substring(3);
            }
            g.drawString(s, x, yb);
            g.setFont(f);
            yb += lineHeight;
        }
    }

    public Object drawObservationDetails(List<Object> arguments) {
        assertExpectedArgCount(arguments, "draw_obs_details takes 1, 2, or 3 (image(s), [x], [y])", 1, 3);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("draw_obs_details", arguments, this::drawGlobe);
        }
        var x = getArgument(Number.class, arguments, 1).map(Number::intValue).orElse(50);
        var y = getArgument(Number.class, arguments, 2).map(Number::intValue).orElse(50);
        if (arg instanceof ImageWrapper img) {
            var processParams = findProcessParams(img);
            if (processParams.isPresent()) {
                return drawOnImage(img, (g, image) -> {
                    getEllipse(arguments, 3).ifPresent(ellipse -> {
                        var semiAxis = ellipse.semiAxis();
                        var radius = (semiAxis.a() + semiAxis.b()) / 2;
                        autoScaleFont(g, 1.2d, radius);
                    });
                    var sb = new StringBuilder("<b>");
                    var params = processParams.get();
                    var details = params.observationDetails();
                    appendLine(details.observer(), sb);
                    var date = details.date();
                    sb.append(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'"))).append("\n");
                    var ray = params.spectrumParams().ray();
                    String rayDetails = "";
                    if (ray.wavelength() > 0) {
                        rayDetails = " (" + String.format(Locale.US, "%.2fÅ", 10 * ray.wavelength()) + ")";
                    }
                    appendLine(details.instrument() + " - " + ray + rayDetails, sb);
                    appendLine(details.telescope(), sb);
                    if (details.focalLength() != null) {
                        appendLine("Focal length " + details.focalLength() + "mm", sb);
                    }
                    if (details.aperture() != null) {
                        appendLine("Aperture " + details.aperture() + "mm", sb);
                    }
                    appendLine(details.camera(), sb);
                    writeMultiline(g, sb, x, y);
                });
            } else {
                throw new IllegalStateException("Cannot determine process parameters");
            }
        }
        throw new IllegalArgumentException("Unexpected image type: " + arg);
    }

    public Object drawSolarParameters(List<Object> arguments) {
        assertExpectedArgCount(arguments, "draw_solar_params takes 1, 2, or 3 (image(s), [x], [y])", 1, 3);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("draw_solar_params", arguments, this::drawGlobe);
        }
        if (arg instanceof ImageWrapper img) {
            var x = getArgument(Number.class, arguments, 1).map(Number::intValue).orElse(-1);
            var y = getArgument(Number.class, arguments, 2).map(Number::intValue).orElse(50);
            var processParams = findProcessParams(img);
            if (processParams.isPresent()) {
                return drawOnImage(img, (g, image) -> {
                    getEllipse(arguments, 3).ifPresent(ellipse -> {
                        var semiAxis = ellipse.semiAxis();
                        var radius = (semiAxis.a() + semiAxis.b()) / 2;
                        autoScaleFont(g, 1.2d, radius);
                    });
                    findSolarParams(img).ifPresent(solarParams -> {
                        var sb = new StringBuilder("<b>");
                        appendLine("Solar parameters", sb);
                        appendLine("P " + toDegrees(solarParams.p()), sb);
                        appendLine("B0 " + toDegrees(solarParams.b0()), sb);
                        appendLine("L0 " + toDegrees(solarParams.l0()), sb);
                        appendLine("Carrington rot. " + solarParams.carringtonRotation(), sb);
                        var fx = x;
                        if (fx == -1) {
                            fx = image.width() - 20 * g.getFontMetrics().charWidth('X');
                        }
                        writeMultiline(g, sb, fx, y);
                    });
                });
            } else {
                throw new IllegalStateException("Cannot determine process parameters");
            }
        }
        throw new IllegalArgumentException("Unexpected image type: " + arg);
    }

    private Optional<ProcessParams> findProcessParams(ImageWrapper img) {
        return img.findMetadata(ProcessParams.class).or(() -> getFromContext(ProcessParams.class));
    }

    private static String toDegrees(double radians) {
        return String.format(Locale.US, "%.2f°", Math.toDegrees(radians));
    }

    private static String toArcMin(double radians) {
        var degrees = Math.toDegrees(radians);
        var arcMinutes = (int) (degrees * 60d);
        var remainder = (degrees * 60d - arcMinutes) * 60d;
        var arcSeconds = (int) remainder;

        return String.format(Locale.US, "%d'%d\"", arcMinutes, arcSeconds);
    }


    public Object drawGlobe(List<Object> arguments) {
        assertExpectedArgCount(arguments, "draw_globe takes 1, 2, 3 or 4 arguments (image(s), [angleP], [b0], [ellipse])", 1, 4);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("draw_solar_params", arguments, this::drawGlobe);
        }
        var img = arguments.get(0);
        if (img instanceof ImageWrapper image) {
            var angleP = getArgument(Number.class, arguments, 1).map(Number::doubleValue).or(() -> findSolarParams(image).map(SolarParameters::p)).orElse(0d);
            var b0 = getArgument(Number.class, arguments, 2).map(Number::doubleValue).or(() -> findSolarParams(image).map(SolarParameters::b0)).orElse(0d);
            var ellipse = getEllipse(arguments, 3).orElseThrow(() -> new IllegalArgumentException("Ellipse not defined"));
            return doDrawGlobe(image, ellipse, angleP, b0);
        }
        throw new IllegalArgumentException("Unexpected image type: " + img);
    }

    private Optional<SolarParameters> findSolarParams(ImageWrapper image) {
        return image.findMetadata(SolarParameters.class).or(() -> getFromContext(SolarParameters.class));
    }

    private static int maxValue(ImageWrapper wrapper) {
        float max = Integer.MIN_VALUE;
        if (wrapper instanceof ImageWrapper32 mono) {
            for (float d : mono.data()) {
                max = Math.max(max, d);
            }
        } else if (wrapper instanceof ColorizedImageWrapper colorized) {
            for (float d : colorized.mono().data()) {
                max = Math.max(max, d);
            }
        } else if (wrapper instanceof RGBImage rgb) {
            for (float d : rgb.r()) {
                max = Math.max(max, d);
            }
            for (float d : rgb.g()) {
                max = Math.max(max, d);
            }
            for (float d : rgb.b()) {
                max = Math.max(max, d);
            }
        } else {
            max = Constants.MAX_PIXEL_VALUE;
        }
        return (int) max >> 8;
    }

    private ImageWrapper doDrawGlobe(ImageWrapper wrapper, Ellipse ellipse, double angleP, double b0) {
        return drawOnImage(wrapper, (g, image) -> {
            var font = g.getFont();
            g.setFont(font.deriveFont(AffineTransform.getRotateInstance(-angleP)));
            double centerX = ellipse.center().a();
            double centerY = ellipse.center().b();
            double radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2d;
            double resolution = 0.04d;
            var diameter = (int) Math.round(2 * radius);
            autoScaleFont(g, 1.0d, radius);
            g.drawOval((int) (centerX - radius), (int) (centerY - radius), diameter, diameter);
            g.drawOval((int) (centerX - radius), (int) (centerY - radius), diameter - 1, diameter - 1);
            int geodesisInc = 180 / DIVISIONS;
            for (int i = -180; i <= 180; i += geodesisInc) {
                var angle = toRadians(i);
                for (double theta = 0; theta < 360; theta += resolution) {
                    var lines = List.of(
                            ofSpherical(angle, theta, radius).rotateX(-b0).rotateZ(-angleP),
                            ofSpherical(theta, angle, radius).rotateX(-b0).rotateZ(-angleP)
                    );
                    for (int j = 0; j < lines.size(); j++) {
                        Coordinates c = lines.get(j);
                        if (c.z > 0) {
                            var equator = i == 90 && j == 1;
                            g.fillRoundRect((int) round(centerX + c.x), (int) round(centerY + c.y), equator ? 4 : 1, equator ? 4 : 1, 1, 1);
                        }
                    }
                }
            }
            drawAngleLabels(angleP, geodesisInc, radius, g, centerX, centerY);
            drawRotationAxis(angleP, radius, g, centerX, centerY);
            drawRotationAxis(0, radius, g, centerX, centerY);
            g.setFont(font);
        });
    }

    private static void autoScaleFont(Graphics2D g, double radius, double factor) {
        g.setFont(g.getFont().deriveFont((float) (factor * radius / 48)));
    }

    private static void drawRotationAxis(double angle, double radius, Graphics2D g, double centerX, double centerY) {
        // draw rotation axis
        var x0 = 0;
        var y0 = -(radius * 1.1);
        var p0 = rotate(x0, y0, -angle);
        var x1 = 0;
        var y1 = (radius * 1.1);
        var p1 = rotate(x1, y1, -angle);
        g.drawLine((int) (centerX + p0[0]), (int) (centerY + p0[1]), (int) (centerX + p1[0]), (int) (centerY + p1[1]));
    }

    private static void drawAngleLabels(double angleP, int geodesisInc, double radius, Graphics2D g, double centerX, double centerY) {
        for (int i = 0; i < 90; i += geodesisInc) {
            var angle = toRadians(i);
            if ((i % 90) != 0) {
                var latitude = 90 - i;
                var p = rotate(0, radius * 1.05, angle - angleP);
                g.drawString("" + latitude, (int) (centerX - p[0]), (int) (centerY - p[1]));
                g.drawString("-" + latitude, (int) (centerX + p[0]), (int) (centerY + p[1]));
                p = rotate(0, radius * 1.05, -angle - angleP);
                g.drawString("" + latitude, (int) (centerX - p[0]), (int) (centerY - p[1]));
                g.drawString("-" + latitude, (int) (centerX + p[0]), (int) (centerY + p[1]));
            } else {
                var p = rotate(0, radius * 1.05, -angleP);
                var font = g.getFont();
                g.setFont(font.deriveFont(Font.BOLD));
                g.drawString("N", (int) (centerX - p[0]), (int) (centerY - p[1]));
                g.drawString("S", (int) (centerX + p[0]), (int) (centerY + p[1]));
                p = rotate(0, radius * 1.05, Math.PI / 2 - angleP);
                g.drawString("W", (int) (centerX - p[0]), (int) (centerY - p[1]));
                g.drawString("E", (int) (centerX + p[0]), (int) (centerY + p[1]));
                g.setFont(font);
            }
        }
    }

    public ImageWrapper drawOnImage(ImageWrapper wrapper, BiConsumer<? super Graphics2D, ? super ImageWrapper> consumer) {
        if (wrapper.width() == 0 || wrapper.height() == 0) {
            return wrapper;
        }
        BufferedImage image = null;
        if (wrapper instanceof FileBackedImage fileBacked) {
            wrapper = fileBacked.unwrapToMemory();
        }
        if (wrapper instanceof ImageWrapper32 mono) {
            var data = mono.data();
            image = new BufferedImage(mono.width(), mono.height(), BufferedImage.TYPE_USHORT_GRAY);
            short[] converted = ((DataBufferUShort) image.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < converted.length; i++) {
                converted[i] = (short) round(data[i]);
            }
        } else if (wrapper instanceof ColorizedImageWrapper colorized) {
            var data = colorized.mono().data();
            var rgb = colorized.converter().apply(data);
            var r = rgb[0];
            var g = rgb[1];
            var b = rgb[2];
            var width = colorized.width();
            var height = colorized.height();
            image = toBufferedImage(width, height, r, g, b);
        } else if (wrapper instanceof RGBImage rgb) {
            image = toBufferedImage(rgb.width(), rgb.height(), rgb.r(), rgb.g(), rgb.b());
        }
        if (image != null) {
            var g = image.createGraphics();
            int greyValue = 80 * maxValue(wrapper) / 100;
            g.setColor(new Color(greyValue, greyValue, greyValue));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            consumer.accept(g, wrapper);
            return Loader.toImageWrapper(image, wrapper.metadata());
        }
        return null;
    }

    private static BufferedImage toBufferedImage(int width, int height, float[] r, float[] g, float[] b) {
        BufferedImage image;
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rv = round(r[y * width + x]);
                int gv = round(g[y * width + x]);
                int bv = round(b[y * width + x]);
                rv = (rv >> 8) & 0xFF;
                gv = (gv >> 8) & 0xFF;
                bv = (bv >> 8) & 0xFF;
                image.setRGB(x, y, rv << 16 | gv << 8 | bv);
            }
        }
        return image;
    }

    private static Coordinates ofSpherical(double ascension, double declination, double radius) {
        return new Coordinates(
                sin(ascension) * sin(declination) * radius,
                cos(declination) * radius,
                cos(ascension) * sin(declination) * radius
        );
    }

    private static double[] rotate(double a, double b, double angle) {
        return new double[]{
                cos(angle) * a - sin(angle) * b,
                sin(angle) * a + cos(angle) * b
        };
    }

    private record Coordinates(double x, double y, double z) {

        public Coordinates rotateX(double angle) {
            var rot = rotate(y, z, angle);
            return new Coordinates(x, rot[0], rot[1]);
        }

        public Coordinates rotateY(double angle) {
            var rot = rotate(x, z, angle);
            return new Coordinates(rot[0], y, rot[1]);
        }

        public Coordinates rotateZ(double angle) {
            var rot = rotate(x, y, angle);
            return new Coordinates(rot[0], rot[1], z);
        }
    }
}
