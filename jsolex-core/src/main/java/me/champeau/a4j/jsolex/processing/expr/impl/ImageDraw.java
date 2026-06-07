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

import me.champeau.a4j.jsolex.expr.BuiltinFunction;
import me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator;
import me.champeau.a4j.jsolex.processing.params.GlobeStyle;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.detection.ActiveRegion;
import me.champeau.a4j.jsolex.processing.sun.detection.ActiveRegions;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.GeoCoordinates;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.NOAAActiveRegion;
import me.champeau.a4j.jsolex.processing.util.NOAARegions;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.jsolex.processing.util.VersionUtil;
import me.champeau.a4j.math.regression.Ellipse;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static java.lang.Math.*;
import static me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator.computeDispersion;
import static me.champeau.a4j.jsolex.processing.expr.AbstractImageExpressionEvaluator.round2digits;

public class ImageDraw extends AbstractFunctionImpl {
    private static final Pattern HEXA_COLOR = Pattern.compile("[0-9a-fA-F]{6}");
    private static final int DIVISIONS = 18;
    private static final double SUN_DIAMETER_KM = 1_391_400;
    private static final double SUN_RADIUS_KM = SUN_DIAMETER_KM / 2;
    private static final double EARTH_DIAMETER_KM = 12_742;
    public static final int PROMINENCE_SCALE_STEP_KM = 50000;
    public static final int PROMS_CIRCLES = 5;
    public static final float DEFAULT_LINE_THICKNESS = 1.0f;
    public static final String DEFAULT_SIGNATURE_FONT = "SansSerif";
    public static final int DEFAULT_SIGNATURE_SIZE = 24;

    public static final String DEFAULT_OBS_DETAILS_TEMPLATE = """
            <b>%OBSERVER%
            %DATETIME%
            %INSTRUMENT% - %RAY%
            %SHIFT_LINE%
            %TELESCOPE%
            %FOCAL_LEN_LINE%
            %APERTURE_LINE%
            %ERF_LINE%
            %CAMERA%
            %COORDINATES_LINE%
            %VERSION_LINE%""";

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

    public Object drawObservationDetails(Map<String, Object> arguments) {
        BuiltinFunction.DRAW_OBS_DETAILS.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("draw_obs_details", "img", arguments, this::drawObservationDetails);
        }
        var x = getArgument(Number.class, arguments, "x").map(Number::intValue).orElse(50);
        var y = getArgument(Number.class, arguments, "y").map(Number::intValue).orElse(50);
        var fontSize = getArgument(Number.class, arguments, "fs").map(Number::intValue).orElse(-1);
        if (arg instanceof ImageWrapper img) {
            return drawObservationDetails(img, x, y, fontSize, null);
        }
        throw new IllegalArgumentException("Unexpected image type: " + arg);
    }

    public ImageWrapper drawObservationDetails(ImageWrapper img, int x, int y, int fontSize, String color) {
        if (img instanceof FileBackedImage fileBacked) {
            return drawObservationDetails(fileBacked.unwrapToMemory(), x, y, fontSize, color);
        }
        if (color != null && img instanceof ImageWrapper32 mono) {
            var rgb = new RGBImage(mono.width(), mono.height(), mono.data(), mono.data(), mono.data(), new HashMap<>(mono.metadata()));
            return drawObservationDetails(rgb, x, y, fontSize, color);
        }
        return drawOnImage(img, (g, image) -> drawObservationDetailsOn(g, image, x, y, fontSize, color));
    }

    public void drawObservationDetailsOn(Graphics2D g, ImageWrapper image, int x, int y, int fontSize, String color) {
        drawObservationDetailsOn(g, image, x, y, fontSize, color, null);
    }

    public String computeObservationDetailsContent(ImageWrapper image, String template) {
        var processParams = findProcessParams(image);
        if (processParams.isEmpty()) {
            return "";
        }
        var raw = (template == null || template.isBlank()) ? DEFAULT_OBS_DETAILS_TEMPLATE : template;
        var sb = new StringBuilder();
        for (var rawLine : raw.split("\n", -1)) {
            if (rawLine.isBlank()) {
                sb.append(rawLine).append('\n');
                continue;
            }
            var substituted = performSubstitutions(rawLine, image);
            var trimmed = substituted.startsWith("<b>") ? substituted.substring(3) : substituted;
            if (!trimmed.trim().isBlank()) {
                sb.append(substituted).append('\n');
            }
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    public void drawObservationDetailsOn(Graphics2D g, ImageWrapper image, int x, int y, int fontSize, String color, String template) {
        var processParams = findProcessParams(image);
        if (processParams.isEmpty()) {
            return;
        }
        var savedFont = g.getFont();
        var savedColor = g.getColor();
        try {
            if (fontSize == -1) {
                image.findMetadata(Ellipse.class).ifPresentOrElse(ellipse -> {
                    var semiAxis = ellipse.semiAxis();
                    var radius = (semiAxis.a() + semiAxis.b()) / 2;
                    autoScaleFont(g, 1.2d, radius);
                }, () -> autoScaleFont(g, 1.2d, image.width() / 2d));
            } else {
                g.setFont(g.getFont().deriveFont((float) fontSize));
            }
            configureColor(color, g);
            var sb = new StringBuilder(computeObservationDetailsContent(image, template));
            if (sb.length() > 0) {
                sb.append('\n');
            }
            writeMultiline(g, sb, x, y);
        } finally {
            g.setFont(savedFont);
            g.setColor(savedColor);
        }
    }

    public Object drawText(Map<String, Object> arguments) {
        BuiltinFunction.DRAW_TEXT.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("draw_text", "img", arguments, this::drawText);
        }
        var x = getArgument(Number.class, arguments, "x").map(Number::intValue).orElseThrow();
        var y = getArgument(Number.class, arguments, "y").map(Number::intValue).orElseThrow();
        var text = getArgument(String.class, arguments, "text").orElseThrow();
        var fontSize = getArgument(Number.class, arguments, "fs").map(Number::intValue).orElse(-1);
        var color = getArgument(String.class, arguments, "color").orElse(null);
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
            message = performSubstitutions(message, img);
            var lines = message.split("\n");
            double lineHeight = g.getFontMetrics().getHeight();
            double curY = y;
            for (var line : lines) {
                g.drawString(line, x, (int) curY);
                curY += lineHeight;
            }
        });
    }

    private String performSubstitutions(String message, ImageWrapper image) {
        if (!message.contains("%")) {
            // fast exit path
            return message;
        }
        var params = findProcessParams(image).orElse(null);
        if (params == null) {
            return message;
        }
        if (message.contains("%WAVELEN%")) {
            var wl = AbstractImageExpressionEvaluator.determineWavelengthOf(context, image);
            message = message.replace("%WAVELEN%", String.format(Locale.US, "%.2f", wl));
        }
        if (message.contains("%PIXELSHIFT%")) {
            var ps = image.findMetadata(PixelShift.class);
            if (ps.isPresent()) {
                message = message.replace("%PIXELSHIFT%", String.format(Locale.US, "%.2f", ps.get().pixelShift()));
            }
        }
        if (message.contains("%SHIFT%")) {
            var ps = image.findMetadata(PixelShift.class);
            if (ps.isPresent()) {
                var lambda0 = params.spectrumParams().ray().wavelength();
                var dispersion = computeDispersion(params, lambda0);
                double v = ps.get().pixelShift() * dispersion.angstromsPerPixel();
                message = message.replace("%SHIFT%", String.format(Locale.US, "%s%.2f", v > 0 ? "+" : "", round2digits(v)));
            }
        }
        if (message.contains("%RAY%")) {
            message = message.replace("%RAY%", params.spectrumParams().ray().label());
        }
        if (message.contains("%OBSERVER%")) {
            var details = params.observationDetails();
            message = message.replace("%OBSERVER%", emptyIfNull(details.observer()));
        }
        if (message.contains("%INSTRUMENT%")) {
            var details = params.observationDetails();
            message = message.replace("%INSTRUMENT%", emptyIfNull(details.instrument().label()));
        }
        if (message.contains("%TELESCOPE%")) {
            var details = params.observationDetails();
            message = message.replace("%TELESCOPE%", emptyIfNull(details.telescope()));
        }
        if (message.contains("%DATE%")) {
            var details = params.observationDetails();
            message = message.replace("%DATE%", details.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        }
        if (message.contains("%TIME%")) {
            var details = params.observationDetails();
            message = message.replace("%TIME%", details.date().format(DateTimeFormatter.ofPattern("HH:mm:ss 'UTC'")));
        }
        if (message.contains("%DATETIME%")) {
            var details = params.observationDetails();
            message = message.replace("%DATETIME%", details.date().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")));
        }
        if (message.contains("%CAMERA%")) {
            var details = params.observationDetails();
            message = message.replace("%CAMERA%", emptyIfNull(details.camera()));
        }
        if (message.contains("%FOCAL_LEN%")) {
            var details = params.observationDetails();
            var focalLength = details.focalLength();
            message = message.replace("%FOCAL_LEN%", focalLength != null ? focalLength + "mm" : "");
        }
        if (message.contains("%APERTURE%")) {
            var details = params.observationDetails();
            var aperture = details.aperture();
            message = message.replace("%APERTURE%", aperture != null ? aperture + "mm" : "");
        }
        if (message.contains("%MOUNT%")) {
            var details = params.observationDetails();
            message = message.replace("%MOUNT%", emptyIfNull(details.mount()));
        }
        if (message.contains("%ERF%")) {
            var details = params.observationDetails();
            message = message.replace("%ERF%", emptyIfNull(details.energyRejectionFilter()));
        }
        if (message.contains("%P_ANGLE%")) {
            var solarParams = findSolarParams(image);
            message = message.replace("%P_ANGLE%", solarParams.map(sp -> String.format(Locale.US, "%.2f°", Math.toDegrees(sp.p()))).orElse(""));
        }
        if (message.contains("%B0_ANGLE%")) {
            var solarParams = findSolarParams(image);
            message = message.replace("%B0_ANGLE%", solarParams.map(sp -> String.format(Locale.US, "%.2f°", Math.toDegrees(sp.b0()))).orElse(""));
        }
        if (message.contains("%L0_ANGLE%")) {
            var solarParams = findSolarParams(image);
            message = message.replace("%L0_ANGLE%", solarParams.map(sp -> String.format(Locale.US, "%.2f°", Math.toDegrees(sp.l0()))).orElse(""));
        }
        if (message.contains("%CARRINGTON_ROT%")) {
            var solarParams = findSolarParams(image);
            message = message.replace("%CARRINGTON_ROT%", solarParams.map(sp -> String.valueOf(sp.carringtonRotation())).orElse(""));
        }
        if (message.contains("%FILENAME%")) {
            var sourceInfo = image.findMetadata(SourceInfo.class);
            message = message.replace("%FILENAME%", sourceInfo.map(SourceInfo::serFileName).orElse(""));
        }
        if (message.contains("%VERSION%")) {
            message = message.replace("%VERSION%", VersionUtil.getVersion());
        }
        if (message.contains("%VERSION_LINE%")) {
            message = message.replace("%VERSION_LINE%", "JSol'Ex " + VersionUtil.getVersion());
        }
        if (message.contains("%SHIFT_LINE%")) {
            var ps = image.findMetadata(PixelShift.class);
            String value = "";
            if (ps.isPresent() && ps.get().pixelShift() != 0) {
                var pixels = ps.get().pixelShift();
                var dispersion = computeDispersion(params, params.spectrumParams().ray().wavelength());
                var shift = dispersion.angstromsPerPixel() * pixels;
                value = String.format(Locale.US, "Shift %.2f Å (%.2f px)", shift, pixels);
            }
            message = message.replace("%SHIFT_LINE%", value);
        }
        if (message.contains("%FOCAL_LEN_LINE%")) {
            var focalLength = params.observationDetails().focalLength();
            message = message.replace("%FOCAL_LEN_LINE%", focalLength != null ? "Focal length " + focalLength + "mm" : "");
        }
        if (message.contains("%APERTURE_LINE%")) {
            var details = params.observationDetails();
            String value = "";
            if (details.aperture() != null) {
                value = "Aperture " + details.aperture() + "mm";
                if (details.stop() != null) {
                    value += " (stopped at " + details.stop() + "mm)";
                }
            } else if (details.stop() != null) {
                value = "Diaphragm " + details.stop() + "mm";
            }
            message = message.replace("%APERTURE_LINE%", value);
        }
        if (message.contains("%ERF_LINE%")) {
            var erf = params.observationDetails().energyRejectionFilter();
            message = message.replace("%ERF_LINE%", !isNullOrEmpty(erf) ? "ERF " + erf : "");
        }
        if (message.contains("%COORDINATES_LINE%")) {
            var details = params.observationDetails();
            String value = "";
            if (details.showCoordinatesInDetails() && details.coordinates() != null) {
                var geoCoordinates = new GeoCoordinates(details.coordinates().a(), details.coordinates().b());
                value = geoCoordinates.toString();
            }
            message = message.replace("%COORDINATES_LINE%", value);
        }
        return message;
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

    public Object drawArrow(Map<String, Object> arguments) {
        BuiltinFunction.DRAW_ARROW.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("draw_arrow", "img", arguments, this::drawArrow);
        }
        var x1 = getArgument(Number.class, arguments, "x1").map(Number::intValue).orElseThrow();
        var y1 = getArgument(Number.class, arguments, "y1").map(Number::intValue).orElseThrow();
        var x2 = getArgument(Number.class, arguments, "x2").map(Number::intValue).orElseThrow();
        var y2 = getArgument(Number.class, arguments, "y2").map(Number::intValue).orElseThrow();
        var thickness = getArgument(Number.class, arguments, "thickness").map(Number::intValue).orElse(1);
        var color = getArgument(String.class, arguments, "color").orElse(null);
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

    public Object drawCircle(Map<String, Object> arguments) {
        BuiltinFunction.DRAW_CIRCLE.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("draw_circle", "img", arguments, this::drawCircle);
        }
        var cx = getArgument(Number.class, arguments, "cx").map(Number::intValue).orElseThrow();
        var cy = getArgument(Number.class, arguments, "cy").map(Number::intValue).orElseThrow();
        var radius = getArgument(Number.class, arguments, "radius").map(Number::intValue).orElseThrow();
        var thickness = getArgument(Number.class, arguments, "thickness").map(Number::intValue).orElse(1);
        var color = getArgument(String.class, arguments, "color").orElse(null);
        if (arg instanceof ImageWrapper img) {
            return drawCircle(img, cx, cy, radius, thickness, color);
        } else {
            throw new IllegalArgumentException("Unexpected image type: " + arg);
        }
    }

    public Object drawRectangle(Map<String, Object> arguments) {
        BuiltinFunction.DRAW_RECT.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("draw_rect", "img", arguments, this::drawRectangle);
        }
        var x1 = getArgument(Number.class, arguments, "left").map(Number::intValue).orElseThrow();
        var y1 = getArgument(Number.class, arguments, "top").map(Number::intValue).orElseThrow();
        var width = getArgument(Number.class, arguments, "width").map(Number::intValue).orElseThrow();
        var height = getArgument(Number.class, arguments, "height").map(Number::intValue).orElseThrow();
        var thickness = getArgument(Number.class, arguments, "thickness").map(Number::intValue).orElse(1);
        var color = getArgument(String.class, arguments, "color").orElse(null);
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


    public Object drawSolarParameters(Map<String, Object> arguments) {
        BuiltinFunction.DRAW_SOLAR_PARAMS.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("draw_solar_params", "img", arguments, this::drawSolarParameters);
        }
        if (arg instanceof ImageWrapper img) {
            var x = getArgument(Number.class, arguments, "x").map(Number::intValue).orElse(-1);
            var y = getArgument(Number.class, arguments, "y").map(Number::intValue).orElse(50);
            return drawSolarParameters(img, x, y, null);
        }
        throw new IllegalArgumentException("Unexpected image type: " + arg);
    }

    public ImageWrapper drawSolarParameters(ImageWrapper img, int x, int y, String color) {
        if (img instanceof FileBackedImage fileBacked) {
            return drawSolarParameters(fileBacked.unwrapToMemory(), x, y, color);
        }
        if (color != null && img instanceof ImageWrapper32 mono) {
            var rgb = new RGBImage(mono.width(), mono.height(), mono.data(), mono.data(), mono.data(), new HashMap<>(mono.metadata()));
            return drawSolarParameters(rgb, x, y, color);
        }
        return drawOnImage(img, (g, image) -> drawSolarParametersOn(g, image, x, y, color));
    }

    /**
     * Draws solar parameters on an existing Graphics2D. Saves/restores
     * graphics state so this can be chained with other overlays.
     */
    public String computeSolarParametersContent(ImageWrapper image) {
        var solarParamsOpt = findSolarParams(image);
        if (solarParamsOpt.isEmpty()) {
            return "";
        }
        var solarParams = solarParamsOpt.get();
        var sb = new StringBuilder("<b>");
        sb.append("Solar parameters").append('\n');
        sb.append("P ").append(toDegrees(solarParams.p())).append('\n');
        sb.append("B0 ").append(toDegrees(solarParams.b0())).append('\n');
        sb.append("L0 ").append(toDegrees(solarParams.l0())).append('\n');
        sb.append("Carrington rot. ").append(solarParams.carringtonRotation());
        return sb.toString();
    }

    public void drawSolarParametersOn(Graphics2D g, ImageWrapper image, int x, int y, String color) {
        var savedFont = g.getFont();
        var savedColor = g.getColor();
        try {
            image.findMetadata(Ellipse.class).ifPresent(ellipse -> {
                var semiAxis = ellipse.semiAxis();
                var radius = (semiAxis.a() + semiAxis.b()) / 2;
                autoScaleFont(g, 1.2d, radius);
            });
            configureColor(color, g);
            findSolarParams(image).ifPresent(solarParams -> {
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
        } finally {
            g.setFont(savedFont);
            g.setColor(savedColor);
        }
    }

    public void drawSignatureOn(Graphics2D g, ImageWrapper image, String text, String fontFamily, int fontSize, String color) {
        drawSignatureOn(g, image, text, fontFamily, fontSize, color, -1, -1, null);
    }

    public void drawSignatureOn(Graphics2D g, ImageWrapper image, String text, String fontFamily, int fontSize, String color, int anchorX, int anchorY) {
        drawSignatureOn(g, image, text, fontFamily, fontSize, color, anchorX, anchorY, null);
    }

    public void drawSignatureOn(Graphics2D g, ImageWrapper image, String text, String fontFamily, int fontSize, String color, int anchorX, int anchorY, String fontWeight) {
        if (text == null || text.isBlank()) {
            return;
        }
        var savedFont = g.getFont();
        var savedColor = g.getColor();
        try {
            var family = fontFamily != null && !fontFamily.isBlank() ? fontFamily : DEFAULT_SIGNATURE_FONT;
            int size = fontSize > 0 ? fontSize : DEFAULT_SIGNATURE_SIZE;
            g.setFont(weightedFont(family, size, fontWeight));
            configureColor(color, g);
            var metrics = g.getFontMetrics();
            var lines = text.split("\n", -1);
            int lineHeight = metrics.getHeight();
            int x;
            int yStart;
            if (anchorX >= 0 && anchorY >= 0) {
                x = anchorX;
                yStart = anchorY + metrics.getAscent();
            } else {
                int marginX = (int) Math.max(40, image.width() * 0.03);
                int marginY = (int) Math.max(40, image.height() * 0.03);
                x = marginX;
                yStart = image.height() - marginY - metrics.getDescent() - lineHeight * (lines.length - 1);
            }
            for (int i = 0; i < lines.length; i++) {
                g.drawString(lines[i], x, yStart + i * lineHeight);
            }
        } finally {
            g.setFont(savedFont);
            g.setColor(savedColor);
        }
    }

    private static Font weightedFont(String family, int size, String fontWeight) {
        var attributes = new HashMap<TextAttribute, Object>();
        attributes.put(TextAttribute.FAMILY, family);
        attributes.put(TextAttribute.SIZE, (float) size);
        attributes.put(TextAttribute.WEIGHT, awtWeight(fontWeight));
        return new Font(attributes);
    }

    private static Float awtWeight(String fontWeight) {
        if (fontWeight == null) {
            return TextAttribute.WEIGHT_REGULAR;
        }
        return switch (fontWeight) {
            case "THIN" -> TextAttribute.WEIGHT_EXTRA_LIGHT;
            case "EXTRA_LIGHT" -> TextAttribute.WEIGHT_LIGHT;
            case "LIGHT" -> TextAttribute.WEIGHT_DEMILIGHT;
            case "MEDIUM" -> TextAttribute.WEIGHT_MEDIUM;
            case "SEMI_BOLD" -> TextAttribute.WEIGHT_SEMIBOLD;
            case "BOLD" -> TextAttribute.WEIGHT_BOLD;
            case "EXTRA_BOLD" -> TextAttribute.WEIGHT_EXTRABOLD;
            case "BLACK" -> TextAttribute.WEIGHT_ULTRABOLD;
            default -> TextAttribute.WEIGHT_REGULAR;
        };
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


    public Object drawGlobe(Map<String, Object> arguments) {
        BuiltinFunction.DRAW_GLOBE.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("draw_globe", "img", arguments, this::drawGlobe);
        }
        var img = arguments.get("img");
        if (img instanceof ImageWrapper image) {
            var angleP = getArgument(Number.class, arguments, "angleP").map(Number::doubleValue).or(() -> findSolarParams(image).map(SolarParameters::p)).orElse(0d);
            var b0 = getArgument(Number.class, arguments, "b0").map(Number::doubleValue).or(() -> findSolarParams(image).map(SolarParameters::b0)).orElse(0d);
            var ellipse = getEllipse(arguments, "ellipse").orElseThrow(() -> new IllegalArgumentException("Ellipse not defined"));
            var style = getArgument(String.class, arguments, "style").orElse(null);
            if (style == null) {
                var processParams = findProcessParams(image);
                style = processParams.map(params -> params.extraParams().globeStyle().name()).orElseGet(GlobeStyle.EQUATORIAL_COORDS::name);
            }
            var correctAngleP = getArgument(Number.class, arguments, "correctAngleP").map(Number::intValue).orElse(0) != 0;
            var drawPromScale = getArgument(Number.class, arguments, "drawPromScale").map(Number::intValue).orElse(0) != 0;
            return doDrawGlobe(image, ellipse, angleP, b0, null, true, correctAngleP, GlobeStyle.valueOf(style.toUpperCase(Locale.US)), drawPromScale);
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

    private void drawProminenceDistanceScale(Graphics2D g,
                                             double centerX,
                                             double centerY,
                                             double radius,
                                             double labelAngleOffset,
                                             int circles,
                                             int stepKm,
                                             float lineThickness) {
        var originalFont = g.getFont();
        var originalColor = g.getColor();
        var originalStroke = g.getStroke();
        var originalTransform = g.getTransform();

        g.setFont(originalFont);
        autoScaleFont(g, 0.8d, radius);
        g.setStroke(new BasicStroke(
                lineThickness,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_BEVEL,
                0,
                new float[]{5},
                0
        ));

        var pixelsPerKm = radius / SUN_RADIUS_KM;

        for (int i = 1; i <= circles; i++) {
            var distanceKm = i * stepKm;
            var circleRadius = radius + (distanceKm * pixelsPerKm);

            var circleX = (int) (centerX - circleRadius);
            var circleY = (int) (centerY - circleRadius);
            var circleDiameter = (int) (2 * circleRadius);
            g.drawOval(circleX, circleY, circleDiameter, circleDiameter);

            for (int j = 0; j < 4; j++) {
                var labelAngle = j * Math.PI / 2 + labelAngleOffset;
                var distanceText = String.format("%,d km", distanceKm).replace(",", " ");
                var labelX = centerX + Math.cos(labelAngle) * circleRadius;
                var labelY = centerY + Math.sin(labelAngle) * circleRadius;

                var tangentAngle = labelAngle + Math.PI / 2;

                var saved = g.getTransform();
                var at = new AffineTransform();
                at.translate(labelX, labelY);
                at.rotate(tangentAngle);
                g.setTransform(at);

                g.drawString(distanceText, 0, 0);

                g.setTransform(saved);
            }
        }

        g.setFont(originalFont);
        g.setColor(originalColor);
        g.setStroke(originalStroke);
        g.setTransform(originalTransform);
    }

    public ImageWrapper doDrawGlobe(ImageWrapper wrapper,
                                    Ellipse ellipse,
                                    double angleP,
                                    double b0,
                                    Color color,
                                    boolean maybeDrawSunspots,
                                    boolean correctAngleP,
                                    GlobeStyle style,
                                    boolean drawProminenceScale) {
        return drawOnImage(wrapper, (g, image) -> drawGlobeOn(g, image, ellipse, angleP, b0, color,
                maybeDrawSunspots, correctAngleP, style, drawProminenceScale));
    }

    public void drawGlobeOn(Graphics2D g,
                            ImageWrapper image,
                            Ellipse ellipse,
                            double angleP,
                            double b0,
                            Color color,
                            boolean maybeDrawSunspots,
                            boolean correctAngleP,
                            GlobeStyle style,
                            boolean drawProminenceScale) {
        plotGlobeGrid(g, ellipse, angleP, b0, color, correctAngleP);
        drawGlobeAdornmentsOn(g, image, ellipse, angleP, b0, color,
                maybeDrawSunspots, correctAngleP, style, drawProminenceScale);
    }

    private static void drawActiveRegionsLabels(
            List<ActiveRegion> detectedRegions,
            double angleP,
            double b0,
            Graphics2D g,
            double radius,
            List<NOAAActiveRegion> regions,
            double centerX,
            double centerY,
            boolean rotateLabels,
            Color overrideColor,
            boolean drawBoxes) {
        autoScaleFont(g, 2.0d, radius);
        // Same guard as the globe grid: when the image is already P-corrected the disk is upright,
        // so the active regions must be projected without applying the P angle. This only affects
        // the position of the regions; labels are always drawn upright.
        double effectiveP = rotateLabels ? 0.0 : angleP;
        var baseFont = g.getFont();
        var fm = g.getFontMetrics();
        int labelHeight = fm.getHeight();
        var pxPerDeg = radius * Math.PI / 180.0;

        var layouts = new ArrayList<ArLabelLayout>();
        for (var activeRegion : regions) {
            var regionId = activeRegion.id();
            double latitude = Math.toRadians(activeRegion.latitudeDeg()) + Math.PI / 2;
            double longitude = Math.toRadians(activeRegion.longitudeDeg());
            var regionCoords = ofSpherical(longitude, latitude, radius).rotateX(-b0).rotateZ(-effectiveP);
            if (regionCoords.z <= 0) {
                continue;
            }
            double anchorX = centerX + regionCoords.x;
            double anchorY = centerY + regionCoords.y;
            boolean detected = detectedRegions.stream()
                    .anyMatch(s -> {
                        var cx = s.topLeft().x() + s.width() / 2;
                        var cy = s.topLeft().y() + s.height() / 2;
                        // the label matches a detected region if the center is within 10% of the sun radius
                        return Math.hypot(cx - anchorX, cy - anchorY) < 0.1 * radius;
                    });
            double boxHalf = drawBoxes
                    ? Math.max(3, activeRegion.extentDeg() > 0 ? activeRegion.extentDeg() : 5) * pxPerDeg / 2
                    : 0;
            int textWidth = fm.stringWidth(regionId);
            double labelX;
            double labelY;
            if (drawBoxes) {
                labelX = anchorX;
                labelY = anchorY - boxHalf - PROMS_CIRCLES;
            } else {
                labelX = anchorX + PROMS_CIRCLES + textWidth / 2.0;
                labelY = anchorY;
            }
            layouts.add(new ArLabelLayout(anchorX, anchorY, boxHalf, detected, regionId, textWidth, labelX, labelY));
        }

        // Resolve label overlaps by nudging colliding labels vertically.
        for (int pass = 0; pass < 10; pass++) {
            boolean moved = false;
            for (int a = 0; a < layouts.size(); a++) {
                for (int b2 = a + 1; b2 < layouts.size(); b2++) {
                    var la = layouts.get(a);
                    var lb = layouts.get(b2);
                    boolean overlapX = Math.abs(la.labelX - lb.labelX) < (la.textWidth + lb.textWidth) / 2.0 + 4;
                    boolean overlapY = Math.abs(la.labelY - lb.labelY) < labelHeight;
                    if (overlapX && overlapY) {
                        if (la.labelY > lb.labelY) {
                            la.labelY = lb.labelY + labelHeight;
                        } else {
                            lb.labelY = la.labelY + labelHeight;
                        }
                        moved = true;
                    }
                }
            }
            if (!moved) {
                break;
            }
        }

        float boxStroke = Math.max(1f, (float) (radius / 400d));
        for (var l : layouts) {
            var color = l.detected
                    ? (overrideColor != null ? overrideColor : Color.BLUE)
                    : (overrideColor != null ? overrideColor : Color.RED);
            g.setColor(color);
            if (drawBoxes) {
                int boxHalf = (int) round(l.boxHalf);
                var savedStroke = g.getStroke();
                g.setStroke(new BasicStroke(boxStroke));
                g.drawRect((int) round(l.anchorX - boxHalf), (int) round(l.anchorY - boxHalf), 2 * boxHalf, 2 * boxHalf);
                double naturalLabelY = l.anchorY - l.boxHalf - PROMS_CIRCLES;
                if (Math.abs(l.labelY - naturalLabelY) > labelHeight) {
                    g.setStroke(new BasicStroke(Math.max(0.5f, boxStroke / 2f)));
                    g.drawLine((int) round(l.anchorX), (int) round(l.anchorY - l.boxHalf), (int) round(l.labelX), (int) round(l.labelY));
                }
                g.setStroke(savedStroke);
            }
            g.setFont(l.detected ? baseFont.deriveFont(Font.BOLD) : baseFont.deriveFont(Font.PLAIN));
            g.drawString(l.text, (int) round(l.labelX - l.textWidth / 2.0), (int) round(l.labelY));
        }
    }

    private static final class ArLabelLayout {
        final double anchorX;
        final double anchorY;
        final double boxHalf;
        final boolean detected;
        final String text;
        final int textWidth;
        double labelX;
        double labelY;

        ArLabelLayout(double anchorX, double anchorY, double boxHalf, boolean detected, String text, int textWidth, double labelX, double labelY) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.boxHalf = boxHalf;
            this.detected = detected;
            this.text = text;
            this.textWidth = textWidth;
            this.labelX = labelX;
            this.labelY = labelY;
        }
    }

    public void drawActiveRegionLabelsOn(Graphics2D g,
                                         ImageWrapper image,
                                         Ellipse ellipse,
                                         SolarParameters solarParams,
                                         boolean correctAngleP,
                                         Color color,
                                         boolean drawBoxes) {
        var processParams = findProcessParams(image).orElse(null);
        if (processParams == null) {
            return;
        }
        var date = processParams.observationDetails().date();
        var regions = NOAARegions.findActiveRegions(date, broadcaster);
        if (regions.isEmpty()) {
            return;
        }
        double centerX = ellipse.center().a();
        double centerY = ellipse.center().b();
        double radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2d;
        var detectedRegions = image.findMetadata(ActiveRegions.class).map(ActiveRegions::regionList).orElse(List.of());
        drawActiveRegionsLabels(detectedRegions, solarParams.p(), solarParams.b0(), g, radius, regions, centerX, centerY, correctAngleP, color, drawBoxes);
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

    private static void drawAngleLabels(double angleP,
                                        int geodesisInc,
                                        double radius,
                                        Graphics2D g,
                                        double centerX,
                                        double centerY,
                                        GlobeStyle style) {
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
                switch (style) {
                    case SOLAR_COORDS -> {
                        g.drawString("N", (int) (centerX - p[0]), (int) (centerY - p[1]));
                        g.drawString("S", (int) (centerX + p[0]), (int) (centerY + p[1]));
                        p = rotate(0, radius * 1.05, Math.PI / 2 - angleP);
                        g.drawString("W", (int) (centerX - p[0]), (int) (centerY - p[1]));
                        g.drawString("E", (int) (centerX + p[0]), (int) (centerY + p[1]));
                    }
                    case EQUATORIAL_COORDS -> {
                        g.drawString("P", (int) (centerX - p[0]), (int) (centerY - p[1]));
                        p = rotate(0, radius * 1.05, Math.PI / 2 - angleP);
                        g.drawString("0", (int) (centerX - p[0]), (int) (centerY - p[1]));
                        g.drawString("0", (int) (centerX + p[0]), (int) (centerY + p[1]));
                    }
                }
                g.setFont(font);
            }
        }
    }

    public static BufferedImage toBufferedImage(ImageWrapper wrapper) {
        if (wrapper.width() == 0 || wrapper.height() == 0) {
            return null;
        }
        if (wrapper instanceof FileBackedImage fileBacked) {
            wrapper = fileBacked.unwrapToMemory();
        }
        if (wrapper instanceof ImageWrapper32 mono) {
            var data = mono.data();
            var image = new BufferedImage(mono.width(), mono.height(), BufferedImage.TYPE_USHORT_GRAY);
            short[] converted = ((DataBufferUShort) image.getRaster().getDataBuffer()).getData();
            var width = mono.width();
            var height = mono.height();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    converted[y * width + x] = (short) round(data[y][x]);
                }
            }
            return image;
        } else if (wrapper instanceof RGBImage rgb) {
            return toBufferedImage(rgb.width(), rgb.height(), rgb.r(), rgb.g(), rgb.b());
        }
        return null;
    }

    public void plotGlobeGrid(Graphics2D g,
                              Ellipse ellipse,
                              double angleP,
                              double b0,
                              Color color,
                              boolean correctAngleP) {
        plotGlobeGrid(g, ellipse, angleP, b0, color, correctAngleP, DEFAULT_LINE_THICKNESS);
    }

    public void plotGlobeGrid(Graphics2D g,
                              Ellipse ellipse,
                              double angleP,
                              double b0,
                              Color color,
                              boolean correctAngleP,
                              float lineThickness) {
        double centerX = ellipse.center().a();
        double centerY = ellipse.center().b();
        double radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2d;
        double sinB0 = Math.sin(b0);
        double cosB0 = Math.cos(b0);
        double effectiveP = correctAngleP ? 0.0 : angleP;
        double sinP = Math.sin(effectiveP);
        double cosP = Math.cos(effectiveP);

        var savedColor = g.getColor();
        var savedStroke = g.getStroke();
        var savedAA = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        try {
            if (color != null) {
                g.setColor(color);
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            var gridPath = new Path2D.Double();
            var equatorPath = new Path2D.Double();
            int geodesisInc = 180 / DIVISIONS;
            double resolution = Math.PI / 180;

            for (int i = -180; i <= 180; i += geodesisInc) {
                double angle = toRadians(i);
                double sinA = Math.sin(angle);
                double cosA = Math.cos(angle);
                accumulateProjectedCurve(gridPath, true, sinA, cosA,
                        centerX, centerY, radius, sinB0, cosB0, sinP, cosP, resolution);
                accumulateProjectedCurve(i == 90 ? equatorPath : gridPath, false, sinA, cosA,
                        centerX, centerY, radius, sinB0, cosB0, sinP, cosP, resolution);
            }
            g.setStroke(new BasicStroke(lineThickness));
            g.draw(gridPath);
            g.setStroke(new BasicStroke(2.0f * lineThickness));
            g.draw(equatorPath);
        } finally {
            g.setColor(savedColor);
            g.setStroke(savedStroke);
            if (savedAA != null) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, savedAA);
            }
        }
    }

    private static void accumulateProjectedCurve(Path2D.Double path,
                                                 boolean firstIsAscension,
                                                 double sinFixed, double cosFixed,
                                                 double centerX, double centerY, double radius,
                                                 double sinB0, double cosB0,
                                                 double sinP, double cosP,
                                                 double resolution) {
        boolean penDown = false;
        for (double theta = -Math.PI; theta <= Math.PI + resolution * 0.5; theta += resolution) {
            double sinT = Math.sin(theta);
            double cosT = Math.cos(theta);
            double x, y, z;
            if (firstIsAscension) {
                x = sinFixed * sinT * radius;
                y = cosT * radius;
                z = cosFixed * sinT * radius;
            } else {
                x = sinT * sinFixed * radius;
                y = cosFixed * radius;
                z = cosT * sinFixed * radius;
            }
            double y1 = y * cosB0 + z * sinB0;
            double z1 = -y * sinB0 + z * cosB0;
            double x2 = x * cosP + y1 * sinP;
            double y2 = -x * sinP + y1 * cosP;
            if (z1 > 0) {
                double px = centerX + x2;
                double py = centerY + y2;
                if (penDown) {
                    path.lineTo(px, py);
                } else {
                    path.moveTo(px, py);
                    penDown = true;
                }
            } else {
                penDown = false;
            }
        }
    }

    public void drawProminenceScaleOn(Graphics2D g, Ellipse ellipse, Color color, int circles, int stepKm, float lineThickness) {
        var savedColor = g.getColor();
        var savedFont = g.getFont();
        var savedStroke = g.getStroke();
        var savedTransform = g.getTransform();
        try {
            if (color != null) {
                g.setColor(color);
            }
            double centerX = ellipse.center().a();
            double centerY = ellipse.center().b();
            double radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2d;
            drawProminenceDistanceScale(g, centerX, centerY, radius, Math.PI / 4, circles, stepKm, lineThickness);
        } finally {
            g.setColor(savedColor);
            g.setFont(savedFont);
            g.setStroke(savedStroke);
            g.setTransform(savedTransform);
        }
    }

    public void drawGlobeAdornmentsOn(Graphics2D g, ImageWrapper image, Ellipse ellipse,
                                      double angleP, double b0, Color color,
                                      boolean maybeDrawSunspots, boolean correctAngleP,
                                      GlobeStyle style, boolean drawProminenceScale) {
        drawGlobeAdornmentsOn(g, image, ellipse, angleP, b0, color, maybeDrawSunspots, correctAngleP, style, drawProminenceScale, DEFAULT_LINE_THICKNESS);
    }

    public void drawGlobeAdornmentsOn(Graphics2D g, ImageWrapper image, Ellipse ellipse,
                                      double angleP, double b0, Color color,
                                      boolean maybeDrawSunspots, boolean correctAngleP,
                                      GlobeStyle style, boolean drawProminenceScale,
                                      float lineThickness) {
        var savedColor = g.getColor();
        var savedFont = g.getFont();
        var savedStroke = g.getStroke();
        var savedTransform = g.getTransform();
        try {
            if (color != null) {
                g.setColor(color);
            }
            g.setStroke(new BasicStroke(lineThickness));
            var font = g.getFont();
            g.setFont(font.deriveFont(AffineTransform.getRotateInstance(-angleP)));
            double centerX = ellipse.center().a();
            double centerY = ellipse.center().b();
            if (correctAngleP) {
                g.setTransform(AffineTransform.getRotateInstance(angleP, centerX, centerY));
            }
            double radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2d;
            var diameter = (int) Math.round(2 * radius);
            autoScaleFont(g, 1.0d, radius);
            g.drawOval((int) (centerX - radius), (int) (centerY - radius), diameter, diameter);
            g.drawOval((int) (centerX - radius), (int) (centerY - radius), diameter - 1, diameter - 1);
            int geodesisInc = 180 / DIVISIONS;
            drawAngleLabels(angleP, geodesisInc, radius, g, centerX, centerY, style);
            drawRotationAxis(angleP, radius, g, centerX, centerY);
            if (style == GlobeStyle.SOLAR_COORDS) {
                drawRotationAxis(0, radius, g, centerX, centerY);
            } else if (style == GlobeStyle.EQUATORIAL_COORDS) {
                g.setStroke(new BasicStroke(2.0f * lineThickness));
                g.drawLine((int) centerX, (int) (centerY - 1.1 * radius), (int) centerX, (int) (centerY - 1.02 * radius));
                g.drawLine((int) centerX, (int) (centerY + 1.1 * radius), (int) centerX, (int) (centerY + 1.02 * radius));
                g.drawLine((int) (centerX - 1.1 * radius), (int) centerY, (int) (centerX - 1.02 * radius), (int) centerY);
                g.drawLine((int) (centerX + 1.1 * radius), (int) centerY, (int) (centerX + 1.02 * radius), (int) centerY);
                g.setFont(g.getFont().deriveFont(AffineTransform.getRotateInstance(0)));
                g.setFont(g.getFont().deriveFont(Font.BOLD));
                var fs = g.getFont().getSize2D() / 2d;
                g.drawString("N", (int) (centerX - fs), (int) (centerY - 1.12 * radius));
                g.drawString("S", (int) (centerX - fs), (int) (centerY + fs + 1.12 * radius));
                g.drawString("W", (int) (centerX + 1.12 * radius), (int) (centerY + fs));
                g.drawString("E", (int) (centerX - 1.12 * radius), (int) (centerY + fs));
            }
            g.setFont(font);
            var processParams = findProcessParams(image);
            var detectedRegions = image.findMetadata(ActiveRegions.class).map(ActiveRegions::regionList).orElse(List.of());
            if (maybeDrawSunspots && processParams.isPresent() && !detectedRegions.isEmpty()) {
                var date = processParams.get().observationDetails().date();
                var regions = NOAARegions.findActiveRegions(date, broadcaster);
                drawActiveRegionsLabels(detectedRegions, angleP, b0, g, radius, regions, centerX, centerY, correctAngleP, null, false);
            }
            if (drawProminenceScale) {
                drawProminenceDistanceScale(g, centerX, centerY, radius, Math.PI / 4, PROMS_CIRCLES, PROMINENCE_SCALE_STEP_KM, lineThickness);
            }
        } finally {
            g.setColor(savedColor);
            g.setFont(savedFont);
            g.setStroke(savedStroke);
            g.setTransform(savedTransform);
        }
    }

    public static ImageWrapper drawOnImage(ImageWrapper wrapper, BiConsumer<? super Graphics2D, ? super ImageWrapper> consumer) {
        var bi = drawOnImageAsBuffered(wrapper, consumer);
        if (bi == null) {
            return wrapper.width() == 0 || wrapper.height() == 0 ? wrapper : null;
        }
        var memory = wrapper instanceof FileBackedImage fb ? fb.unwrapToMemory() : wrapper;
        return Loader.toImageWrapper(bi, memory.metadata());
    }

    public static BufferedImage drawOnImageAsBuffered(ImageWrapper wrapper, BiConsumer<? super Graphics2D, ? super ImageWrapper> consumer) {
        if (wrapper.width() == 0 || wrapper.height() == 0) {
            return null;
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
                    converted[y * width + x] = (short) round(data[y][x]);
                }
            }
        } else if (wrapper instanceof RGBImage rgb) {
            image = toBufferedImage(rgb.width(), rgb.height(), rgb.r(), rgb.g(), rgb.b());
        }
        if (image != null) {
            var g = image.createGraphics();
            int greyValue = Math.clamp((80L * maxValue(wrapper)) / 100, 0, 255);
            g.setColor(new Color(greyValue, greyValue, greyValue));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            consumer.accept(g, wrapper);
        }
        return image;
    }

    private static BufferedImage toBufferedImage(int width, int height, float[][] r, float[][] g, float[][] b) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var rgbArray = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        for (int y = 0; y < height; y++) {
            var rRow = r[y];
            var gRow = g[y];
            var bRow = b[y];
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int rv = (round(rRow[x]) >> 8) & 0xFF;
                int gv = (round(gRow[x]) >> 8) & 0xFF;
                int bv = (round(bRow[x]) >> 8) & 0xFF;
                rgbArray[rowOffset + x] = (rv << 16) | (gv << 8) | bv;
            }
        }
        return image;
    }

    public static RGBImage drawOnImageInPlace(RGBImage rgb, BiConsumer<? super Graphics2D, ? super ImageWrapper> consumer) {
        if (rgb.width() == 0 || rgb.height() == 0) {
            return rgb;
        }
        int width = rgb.width();
        int height = rgb.height();
        var image = toBufferedImage(width, height, rgb.r(), rgb.g(), rgb.b());
        var g2 = image.createGraphics();
        int greyValue = Math.clamp((80L * maxValue(rgb)) / 100, 0, 255);
        g2.setColor(new Color(greyValue, greyValue, greyValue));
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        consumer.accept(g2, rgb);
        var rendered = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        var rArr = rgb.r();
        var gArr = rgb.g();
        var bArr = rgb.b();
        for (int y = 0; y < height; y++) {
            var rRow = rArr[y];
            var gRow = gArr[y];
            var bRow = bArr[y];
            int rowOffset = y * width;
            for (int x = 0; x < width; x++) {
                int pixel = rendered[rowOffset + x];
                rRow[x] = ((pixel >> 16) & 0xFF) << 8;
                gRow[x] = ((pixel >> 8) & 0xFF) << 8;
                bRow[x] = (pixel & 0xFF) << 8;
            }
        }
        return rgb;
    }

    private static Coordinates ofSpherical(double ascension, double declination, double radius) {
        return new Coordinates(sin(ascension) * sin(declination) * radius, cos(declination) * radius, cos(ascension) * sin(declination) * radius);
    }

    private static double[] rotate(double a, double b, double angle) {
        return new double[]{cos(angle) * a - sin(angle) * b, sin(angle) * a + cos(angle) * b};
    }

    public Object drawEarth(Map<String, Object> arguments) {
        BuiltinFunction.DRAW_EARTH.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("draw_earth", "img", arguments, this::drawEarth);
        }
        int x = intArg(arguments, "x", 0);
        int y = intArg(arguments, "y", 0);
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

    public Object activeRegionsOverlay(Map<String, Object> arguments) {
        BuiltinFunction.AR_OVERLAY.validateArgs(arguments);
        var arg = arguments.get("img");
        if (arg instanceof List<?>) {
            return expandToImageList("ar_overlay", "img", arguments, this::activeRegionsOverlay);
        }
        if (arg instanceof ImageWrapper wrapper) {
            var metadata = wrapper.findMetadata(ActiveRegions.class);
            if (metadata.isPresent()) {
                int displayParams = intArg(arguments, "labels", 1);
                boolean showLabels = displayParams > 0;
                boolean fillRegions = displayParams == 0 || displayParams == 1;
                var activeRegions = metadata.get();
                var img = wrapper.unwrapToMemory();
                if (img instanceof ImageWrapper32 mono) {
                    var rgb = RGBImage.toRGB(mono);
                    return drawActiveRegions(rgb, activeRegions, fillRegions, showLabels, broadcaster);
                } else if (img instanceof RGBImage rgb) {
                    return drawActiveRegions(rgb, activeRegions, fillRegions, showLabels, broadcaster);
                }
            }
        }
        return arg;
    }

    public static RGBImage drawActiveRegions(RGBImage rgb, ActiveRegions activeRegions, boolean fillRegions, boolean showLabels, Broadcaster broadcaster) {
        var width = rgb.width();
        var height = rgb.height();
        var r = rgb.r();
        var b = rgb.b();
        if (fillRegions) {
            drawActiveRegions(activeRegions, width, height, r, b);
        }
        var result = new AtomicReference<>(rgb);
        if (showLabels) {
            rgb.findMetadata(ProcessParams.class).ifPresent(processParams -> {
                var date = processParams.observationDetails().date();
                rgb.findMetadata(SolarParameters.class).ifPresent(solarParams -> {
                    var angleP = solarParams.p();
                    var b0 = solarParams.b0();
                    rgb.findMetadata(Ellipse.class).ifPresent(ellipse -> {
                        double centerX = ellipse.center().a();
                        double centerY = ellipse.center().b();
                        double radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2d;
                        var detectedActiveRegions = activeRegions.regionList();
                        result.set((RGBImage) drawOnImage(rgb, (g, image) -> {
                            drawActiveRegionsLabels(detectedActiveRegions, angleP, b0, g, radius, NOAARegions.findActiveRegions(date, broadcaster), centerX, centerY, processParams.geometryParams().isAutocorrectAngleP(), null, false);
                        }));
                    });
                });
            });
        }
        return result.get();
    }

    private static void drawActiveRegions(ActiveRegions activeRegions, int width, int height, float[][] r, float[][] b) {
        for (var activeRegion : activeRegions.regionList()) {
            var points = activeRegion.points();
            for (var point : points) {
                var x = (int) Math.round(point.x());
                var y = (int) Math.round(point.y());
                if (x >= 0 && x < width && y >= 0 && y < height) {
                    r[y][x] = Constants.MAX_PIXEL_VALUE;
                    b[y][x] = Constants.MAX_PIXEL_VALUE;
                    var next = points.stream().filter(p -> p.x() > point.x() && p.y() > point.y()).findFirst();
                    next.ifPresent(p -> {
                        var nx = (int) Math.ceil(p.x());
                        var ny = (int) Math.ceil(p.y());
                        // fill within box
                        for (int i = x; i < nx; i++) {
                            for (int j = y; j < ny; j++) {
                                if (i < width && j < height) {
                                    r[j][i] = Constants.MAX_PIXEL_VALUE;
                                    b[j][i] = Constants.MAX_PIXEL_VALUE;
                                }
                            }
                        }
                    });
                }
            }
        }
    }

    private static boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
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

    public static InputStream earthImageStream() {
        return ImageDraw.class.getResourceAsStream("earth_realistic.png");
    }

}
