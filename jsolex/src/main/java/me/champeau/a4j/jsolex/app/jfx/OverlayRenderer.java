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
package me.champeau.a4j.jsolex.app.jfx;

import javafx.scene.image.Image;
import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;
import me.champeau.a4j.jsolex.processing.expr.impl.Loader;
import me.champeau.a4j.jsolex.processing.params.GlobeStyle;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.math.regression.Ellipse;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import static javafx.embed.swing.SwingFXUtils.toFXImage;

final class OverlayRenderer {
    private OverlayRenderer() {
    }

    static Image renderToFx(ImageWrapper source,
                            ImageOverlayState state,
                            GlobeStyle globeStyle,
                            ProcessParams processParams,
                            GeneratedImageKind kind,
                            boolean baseImageIsPCorrected) {
        if (source == null || state == null || !hasAnyOverlay(state)) {
            return WritableImageSupport.asWritable(source);
        }
        var bi = renderToBuffered(source, state, globeStyle, processParams, kind, baseImageIsPCorrected);
        return toFXImage(bi, null);
    }

    static ImageWrapper apply(ImageWrapper source,
                              ImageOverlayState state,
                              GlobeStyle globeStyle,
                              ProcessParams processParams,
                              GeneratedImageKind kind,
                              boolean baseImageIsPCorrected) {
        if (source == null || state == null || !hasAnyOverlay(state)) {
            return source;
        }
        var bi = renderToBuffered(source, state, globeStyle, processParams, kind, baseImageIsPCorrected);
        return Loader.toImageWrapper(bi, source.unwrapToMemory().metadata());
    }

    private static BufferedImage renderToBuffered(ImageWrapper source,
                                                  ImageOverlayState state,
                                                  GlobeStyle globeStyle,
                                                  ProcessParams processParams,
                                                  GeneratedImageKind kind,
                                                  boolean baseImageIsPCorrected) {
        var prepared = prepareForColor(source.unwrapToMemory(), state);
        var bi = ImageDraw.toBufferedImage(prepared);
        var g = bi.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(204, 204, 204));
            var ctx = new HashMap<Class<?>, Object>();
            ctx.put(ProcessParams.class, processParams);
            var draw = new ImageDraw(ctx, Broadcaster.NO_OP);
            var ellipse = prepared.findMetadata(Ellipse.class).orElse(null);
            var solarParams = prepared.findMetadata(SolarParameters.class).orElse(null);
            var effectiveStyle = globeStyle != null ? globeStyle : GlobeStyle.EQUATORIAL_COORDS;

            float gridThickness = state.lineThickness() != null ? state.lineThickness() : ImageDraw.DEFAULT_LINE_THICKNESS;
            float promThickness = state.promScaleLineThickness() != null ? state.promScaleLineThickness() : ImageDraw.DEFAULT_LINE_THICKNESS;
            if (state.drawGlobe() && ellipse != null && solarParams != null) {
                boolean correctP = effectivePCorrected(state, kind, baseImageIsPCorrected);
                var gridColor = parseColor(state.gridColor());
                draw.plotGlobeGrid(g, ellipse, solarParams.p(), solarParams.b0(), gridColor, correctP, gridThickness);
                draw.drawGlobeAdornmentsOn(g, prepared, ellipse, solarParams.p(), solarParams.b0(),
                        gridColor, false, correctP, effectiveStyle, false, gridThickness);
            }
            if (state.drawProminenceScale() && ellipse != null) {
                var promColor = parseColor(state.promScaleColor());
                int circles = state.promCircles() != null ? state.promCircles() : ImageDraw.PROMS_CIRCLES;
                int stepKm = state.promStepKm() != null ? state.promStepKm() : ImageDraw.PROMINENCE_SCALE_STEP_KM;
                draw.drawProminenceScaleOn(g, ellipse, promColor, circles, stepKm, promThickness);
            }
            if (state.drawActiveRegions() && ellipse != null && solarParams != null) {
                boolean correctP = effectivePCorrected(state, kind, baseImageIsPCorrected);
                var arColor = parseColor(state.activeRegionsColor());
                draw.drawActiveRegionLabelsOn(g, prepared, ellipse, solarParams, correctP, arColor, state.activeRegionsBoxes());
            }
        } finally {
            g.dispose();
        }
        return bi;
    }

    private static boolean hasAnyOverlay(ImageOverlayState state) {
        return state.drawGlobe()
                || state.drawProminenceScale()
                || state.drawActiveRegions();
    }

    private static boolean anyColorSet(ImageOverlayState state) {
        return state.gridColor() != null
                || state.obsDetailsColor() != null
                || state.solarParamsColor() != null
                || state.promScaleColor() != null
                || state.activeRegionsColor() != null
                || state.signatureColor() != null;
    }

    private static ImageWrapper prepareForColor(ImageWrapper source, ImageOverlayState state) {
        if (anyColorSet(state) && source instanceof ImageWrapper32 mono) {
            return RGBImage.toRGB(mono);
        }
        return source;
    }

    private static boolean effectivePCorrected(ImageOverlayState state, GeneratedImageKind kind, boolean baseImageIsPCorrected) {
        if (kind == GeneratedImageKind.IMAGE_MATH) {
            return state.pCorrected();
        }
        return baseImageIsPCorrected;
    }

    static ImageWrapper bakeEarth(ImageWrapper source, int x, int y, ProcessParams processParams) {
        var ctx = new HashMap<Class<?>, Object>();
        ctx.put(ProcessParams.class, processParams);
        var draw = new ImageDraw(ctx, Broadcaster.NO_OP);
        return (ImageWrapper) draw.drawEarth(Map.of("img", source.unwrapToMemory(), "x", x, "y", y));
    }

    static ImageWrapper bakeSignature(ImageWrapper source, ImageOverlayState state, int x, int y, ProcessParams processParams) {
        if (source == null || state == null || !state.drawSignature() || state.signatureText() == null) {
            return source;
        }
        var prepared = prepareForColorAt(source, state.signatureColor());
        var family = state.signatureFontFamily() != null ? state.signatureFontFamily() : ImageDraw.DEFAULT_SIGNATURE_FONT;
        int size = state.signatureFontSize() != null ? state.signatureFontSize() : ImageDraw.DEFAULT_SIGNATURE_SIZE;
        var draw = newDraw(processParams);
        var bi = ImageDraw.drawOnImageAsBuffered(prepared,
                (g, image) -> draw.drawSignatureOn(g, image, state.signatureText(), family, size, state.signatureColor(), x, y));
        return Loader.toImageWrapper(bi, prepared.metadata());
    }

    static ImageWrapper bakeObsDetails(ImageWrapper source, ImageOverlayState state, int x, int y, ProcessParams processParams) {
        if (source == null || state == null || !state.drawObservationDetails()) {
            return source;
        }
        var prepared = prepareForColorAt(source, state.obsDetailsColor());
        var draw = newDraw(processParams);
        var bi = ImageDraw.drawOnImageAsBuffered(prepared,
                (g, image) -> draw.drawObservationDetailsOn(g, image, x, y, -1, state.obsDetailsColor(), state.obsDetailsTemplate()));
        return Loader.toImageWrapper(bi, prepared.metadata());
    }

    static ImageWrapper bakeSolarParameters(ImageWrapper source, ImageOverlayState state, int x, int y, ProcessParams processParams) {
        if (source == null || state == null || !state.drawSolarParameters()) {
            return source;
        }
        var prepared = prepareForColorAt(source, state.solarParamsColor());
        var draw = newDraw(processParams);
        var bi = ImageDraw.drawOnImageAsBuffered(prepared,
                (g, image) -> draw.drawSolarParametersOn(g, image, x, y, state.solarParamsColor()));
        return Loader.toImageWrapper(bi, prepared.metadata());
    }

    private static ImageDraw newDraw(ProcessParams processParams) {
        var ctx = new HashMap<Class<?>, Object>();
        ctx.put(ProcessParams.class, processParams);
        return new ImageDraw(ctx, Broadcaster.NO_OP);
    }

    private static ImageWrapper prepareForColorAt(ImageWrapper source, String color) {
        if (color != null && source.unwrapToMemory() instanceof ImageWrapper32 mono) {
            return RGBImage.toRGB(mono);
        }
        return source.unwrapToMemory();
    }

    private static Color parseColor(String hex) {
        if (hex == null || hex.length() != 6) {
            return null;
        }
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new Color(r, g, b);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
