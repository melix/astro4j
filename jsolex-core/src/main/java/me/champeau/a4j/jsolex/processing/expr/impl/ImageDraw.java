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
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.GeoCoordinates;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.math.regression.Ellipse;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static java.lang.Math.cos;
import static java.lang.Math.round;
import static java.lang.Math.sin;
import static java.lang.Math.toRadians;

public class ImageDraw extends AbstractFunctionImpl {
    private static final Pattern HEXA_COLOR = Pattern.compile("[0-9a-fA-F]{6}");
    private static final int DIVISIONS = 18;
    private static final double SUN_DIAMETER_KM = 1_391_400;
    private static final double EARTH_DIAMETER_KM = 12_742;

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
            return expandToImageList("draw_obs_details", arguments, this::drawObservationDetails);
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
                    appendLine(details.instrument().label() + " - " + ray + rayDetails, sb);
                    appendLine(details.telescope(), sb);
                    if (details.focalLength() != null) {
                        appendLine("Focal length " + details.focalLength() + "mm", sb);
                    }
                    if (details.aperture() != null) {
                        appendLine("Aperture " + details.aperture() + "mm", sb);
                    }
                    appendLine(details.camera(), sb);
                    if (details.showCoordinatesInDetails() && details.coordinates() != null) {
                        var geoCoordinates = new GeoCoordinates(details.coordinates().a(), details.coordinates().b());
                        appendLine(geoCoordinates.toString(), sb);
                    }
                    writeMultiline(g, sb, x, y);
                });
            } else {
                throw new IllegalStateException("Cannot determine process parameters");
            }
        }
        throw new IllegalArgumentException("Unexpected image type: " + arg);
    }

    public Object drawText(List<Object> arguments) {
        assertExpectedArgCount(arguments, "draw_text takes 4 to 6 arguments (image(s), x, y, text, [font size], [color])", 4, 6);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("draw_text", arguments, this::drawText);
        }
        var x = getArgument(Number.class, arguments, 1).map(Number::intValue).orElseThrow();
        var y = getArgument(Number.class, arguments, 2).map(Number::intValue).orElseThrow();
        var text = getArgument(String.class, arguments, 3).orElseThrow();
        var fontSize = getArgument(Number.class, arguments, 4).map(Number::intValue).orElse(-1);
        var color = getArgument(String.class, arguments, 5).orElse(null);
        if (arg instanceof ImageWrapper img) {
            return drawText(img, text, x, y, color, fontSize);
        } else {
            throw new IllegalArgumentException("Unexpected image type: " + arg);
        }
    }

    public Object drawText(ImageWrapper img, String text, int x, int y, String color, int fontSize) {
        if (img instanceof FileBackedImage fileBacked) {
            return drawText(fileBacked.unwrapToMemory(), text, x, y, color, fontSize);
        }
        if (color != null && img instanceof ImageWrapper32 mono) {
            // first convert to RGB
            var rgb = new RGBImage(mono.width(), mono.height(), mono.data(), mono.data(), mono.data(), new HashMap<>(mono.metadata()));
            return drawText(rgb, text, x, y, color, fontSize);
        }
        return drawOnImage(img, (g, image) -> {
            if (fontSize == -1) {
                img.findMetadata(Ellipse.class).ifPresentOrElse(ellipse -> {
                    var semiAxis = ellipse.semiAxis();
                    var radius = (semiAxis.a() + semiAxis.b()) / 2;
                    autoScaleFont(g, 1.2d, radius);
                }, () -> autoScaleFont(g, 1.2d, image.width() / 2d));
            } else {
                g.setFont(g.getFont().deriveFont((float) fontSize));
            }
            configureColor(color, g);
            var message = text;
            if (text.startsWith("*") && text.endsWith("*")) {
                message = text.substring(1, text.length() - 1);
                g.setFont(g.getFont().deriveFont(Font.BOLD));
            } else if (text.startsWith("_") && text.endsWith("_")) {
                message = text.substring(1, text.length() - 1);
                g.setFont(g.getFont().deriveFont(Font.ITALIC));
            }
            g.drawString(message, x, y);
        });
    }

    private static void configureColor(String color, Graphics2D g) {
        if (color != null) {
            if (HEXA_COLOR.matcher(color).matches()) {
                int resultRed = Integer.valueOf(color.substring(0, 2), 16);
                int resultGreen = Integer.valueOf(color.substring(2, 4), 16);
                int resultBlue = Integer.valueOf(color.substring(4, 6), 16);
                g.setColor(new Color(resultRed, resultGreen, resultBlue));
            } else {
                g.setColor(Color.getColor(color));
            }
        }
    }

    public Object drawArrow(List<Object> arguments) {
        assertExpectedArgCount(arguments, "draw_arrow takes 5 to 7 arguments (image(s), x1, y1, x2, y2, [thickness], [color])", 5, 7);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("draw_arrow", arguments, this::drawArrow);
        }
        var x1 = getArgument(Number.class, arguments, 1).map(Number::intValue).orElseThrow();
        var y1 = getArgument(Number.class, arguments, 2).map(Number::intValue).orElseThrow();
        var x2 = getArgument(Number.class, arguments, 3).map(Number::intValue).orElseThrow();
        var y2 = getArgument(Number.class, arguments, 4).map(Number::intValue).orElseThrow();
        var thickness = getArgument(Number.class, arguments, 5).map(Number::intValue).orElse(1);
        var color = getArgument(String.class, arguments, 6).orElse(null);
        if (arg instanceof ImageWrapper img) {
            return drawArrow(img, x1, y1, x2, y2, color, thickness);
        } else {
            throw new IllegalArgumentException("Unexpected image type: " + arg);
        }
    }

    private Object drawArrow(ImageWrapper img, int x1, int y1, int x2, int y2, String color, int thickness) {
        if (img instanceof FileBackedImage fileBacked) {
            return drawArrow(fileBacked.unwrapToMemory(), x1, y1, x2, y2, color, thickness);
        }
        if (color != null && img instanceof ImageWrapper32 mono) {
            // first convert to RGB
            var rgb = new RGBImage(mono.width(), mono.height(), mono.data(), mono.data(), mono.data(), new HashMap<>(mono.metadata()));
            return drawArrow(rgb, x1, y1, x2, y2, color, thickness);
        }
        return drawOnImage(img, (g, image) -> {
            if (thickness >= 1) {
                g.setStroke(new BasicStroke(thickness));
            }
            configureColor(color, g);
            int arrowSize = Math.max(10, thickness * 3);
            // adjust line end to avoid arrowhead being clipped
            var xx2 = x2 - arrowSize * Math.cos(Math.atan2(y2 - y1, x2 - x1));
            var yy2 = y2 - arrowSize * Math.sin(Math.atan2(y2 - y1, x2 - x1));
            g.drawLine(x1, y1, (int) xx2, (int) yy2);

            // draw line with arrowhead filled
            double angle = Math.atan2(y2 - y1, x2 - x1);

            var x3 = x2 - (int) (arrowSize * Math.cos(angle - Math.PI / 6));
            var y3 = y2 - (int) (arrowSize * Math.sin(angle - Math.PI / 6));
            var x4 = x2 - (int) (arrowSize * Math.cos(angle + Math.PI / 6));
            var y4 = y2 - (int) (arrowSize * Math.sin(angle + Math.PI / 6));

            // Create and fill the arrow head
            g.fillPolygon(new int[]{x2, x3, x4}, new int[]{y2, y3, y4}, 3);

        });
    }

    public Object drawCircle(ImageWrapper img, int centerX, int centerY, int radius, int thickness, String color) {
        if (img instanceof FileBackedImage fileBacked) {
            return drawCircle(fileBacked.unwrapToMemory(), centerX, centerY, radius, thickness, color);
        }
        if (color != null && img instanceof ImageWrapper32 mono) {
            // first convert to RGB
            var rgb = new RGBImage(mono.width(), mono.height(), mono.data(), mono.data(), mono.data(), new HashMap<>(mono.metadata()));
            return drawCircle(rgb, centerX, centerY, radius, thickness, color);
        }
        return drawOnImage(img, (g, image) -> {
            g.setStroke(new BasicStroke(thickness));
            configureColor(color, g);
            g.drawOval(centerX - radius, centerY - radius, 2 * radius, 2 * radius);
        });
    }

    public Object drawCircle(List<Object> arguments) {
        assertExpectedArgCount(arguments, "draw_circle takes 4 to 6 arguments (image(s), cx, cy, radius, [color], [thickness])", 4, 6);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("draw_circle", arguments, this::drawCircle);
        }
        var cx = getArgument(Number.class, arguments, 1).map(Number::intValue).orElseThrow();
        var cy = getArgument(Number.class, arguments, 2).map(Number::intValue).orElseThrow();
        var radius = getArgument(Number.class, arguments, 3).map(Number::intValue).orElseThrow();
        var thickness = getArgument(Number.class, arguments, 4).map(Number::intValue).orElse(1);
        var color = getArgument(String.class, arguments, 5).orElse(null);
        if (arg instanceof ImageWrapper img) {
            return drawCircle(img, cx, cy, radius, thickness, color);
        } else {
            throw new IllegalArgumentException("Unexpected image type: " + arg);
        }
    }

    public Object drawRectangle(List<Object> arguments) {
        assertExpectedArgCount(arguments, "draw_rect takes 5 to 7 arguments (image(s), x1, y1, width, height, [thickness], [color])", 5, 7);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("draw_rect", arguments, this::drawRectangle);
        }
        var x1 = getArgument(Number.class, arguments, 1).map(Number::intValue).orElseThrow();
        var y1 = getArgument(Number.class, arguments, 2).map(Number::intValue).orElseThrow();
        var width = getArgument(Number.class, arguments, 3).map(Number::intValue).orElseThrow();
        var height = getArgument(Number.class, arguments, 4).map(Number::intValue).orElseThrow();
        var thickness = getArgument(Number.class, arguments, 5).map(Number::intValue).orElse(1);
        var color = getArgument(String.class, arguments, 6).orElse(null);
        if (arg instanceof ImageWrapper img) {
            return drawRectangle(img, x1, y1, width, height, color, thickness);
        } else {
            throw new IllegalArgumentException("Unexpected image type: " + arg);
        }
    }

    public Object drawRectangle(ImageWrapper img, int x1, int y1, int width, int height, String color, int thickness) {
        if (img instanceof FileBackedImage fileBacked) {
            return drawRectangle(fileBacked.unwrapToMemory(), x1, y1, width, height, color, thickness);
        }
        if (color != null && img instanceof ImageWrapper32 mono) {
            // first convert to RGB
            var rgb = new RGBImage(mono.width(), mono.height(), mono.data(), mono.data(), mono.data(), new HashMap<>(mono.metadata()));
            return drawRectangle(rgb, x1, y1, width, height, color, thickness);
        }
        return drawOnImage(img, (g, image) -> {
            g.setStroke(new BasicStroke(thickness));
            configureColor(color, g);
            g.drawRect(x1, y1, width, height);
        });
    }


    public Object drawSolarParameters(List<Object> arguments) {
        assertExpectedArgCount(arguments, "draw_solar_params takes 1, 2, or 3 (image(s), [x], [y])", 1, 3);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("draw_solar_params", arguments, this::drawSolarParameters);
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
            return expandToImageList("draw_globe", arguments, this::drawGlobe);
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
            for (float[] line : mono.data()) {
                for (float d : line) {
                    max = Math.max(max, d);
                }
            }
        } else if (wrapper instanceof RGBImage rgb) {
            for (float[] line : rgb.r()) {
                for (float d : line) {
                    max = Math.max(max, d);
                }
            }
            for (float[] line : rgb.g()) {
                for (float d : line) {
                    max = Math.max(max, d);
                }
            }
            for (float[] line : rgb.b()) {
                for (float d : line) {
                    max = Math.max(max, d);
                }
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
                    var lines = List.of(ofSpherical(angle, theta, radius).rotateX(-b0).rotateZ(-angleP), ofSpherical(theta, angle, radius).rotateX(-b0).rotateZ(-angleP));
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
            var width = mono.width();
            var height = mono.height();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    converted[y*width + x] = (short) round(data[y][x]);
                }
            }
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

    private static BufferedImage toBufferedImage(int width, int height, float[][] r, float[][] g, float[][] b) {
        BufferedImage image;
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rv = round(r[y][x]);
                int gv = round(g[y][x]);
                int bv = round(b[y][x]);
                rv = (rv >> 8) & 0xFF;
                gv = (gv >> 8) & 0xFF;
                bv = (bv >> 8) & 0xFF;
                image.setRGB(x, y, rv << 16 | gv << 8 | bv);
            }
        }
        return image;
    }

    private static Coordinates ofSpherical(double ascension, double declination, double radius) {
        return new Coordinates(sin(ascension) * sin(declination) * radius, cos(declination) * radius, cos(ascension) * sin(declination) * radius);
    }

    private static double[] rotate(double a, double b, double angle) {
        return new double[]{cos(angle) * a - sin(angle) * b, sin(angle) * a + cos(angle) * b};
    }

    public Object drawEarth(List<Object> arguments) {
        assertExpectedArgCount(arguments, "draw_earth takes 3 arguments (image(s), x, y)", 3, 3);
        var arg = arguments.get(0);
        if (arg instanceof List<?>) {
            return expandToImageList("draw_earth", arguments, this::drawEarth);
        }
        int x = intArg(arguments, 1);
        int y = intArg(arguments, 2);
        if (arg instanceof ImageWrapper wrapper) {
            return drawOnImage(wrapper, (g, image) -> {
                var ellipse = image.findMetadata(Ellipse.class);
                double scale = 1.0;
                double earthDiameterPixels = Earth.IMAGE.getWidth();
                double sunDiameterPixels = 0;
                if (ellipse.isPresent()) {
                    var semiAxis = ellipse.get().semiAxis();
                    sunDiameterPixels = (semiAxis.a() + semiAxis.b());
                    var resolution = sunDiameterPixels / SUN_DIAMETER_KM;
                    scale = resolution * EARTH_DIAMETER_KM / earthDiameterPixels;
                }

                int scaledWidth = (int) (earthDiameterPixels * scale);
                int scaledHeight = (int) (Earth.IMAGE.getHeight() * scale);
                g.drawImage(Earth.IMAGE, x, y, scaledWidth, scaledHeight, null);
            });
        }

        return arg;
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

    private static class Earth {
        private static final BufferedImage IMAGE;

        static {
            try {
                IMAGE = ImageIO.read(ImageDraw.class.getResourceAsStream("earth_realistic.png"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
