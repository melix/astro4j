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
package me.champeau.a4j.jsolex.processing.sun.workflow;

import me.champeau.a4j.jsolex.processing.event.GeneratedImage;
import me.champeau.a4j.jsolex.processing.event.ImageGeneratedEvent;
import me.champeau.a4j.jsolex.processing.event.Notification;
import me.champeau.a4j.jsolex.processing.event.NotificationEvent;
import me.champeau.a4j.jsolex.processing.expr.DefaultImageScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptResult;
import me.champeau.a4j.jsolex.processing.expr.impl.Crop;
import me.champeau.a4j.jsolex.processing.expr.impl.EllipseFit;
import me.champeau.a4j.jsolex.processing.expr.impl.GeometryCorrection;
import me.champeau.a4j.jsolex.processing.expr.impl.Loader;
import me.champeau.a4j.jsolex.processing.expr.impl.MosaicComposition;
import me.champeau.a4j.jsolex.processing.expr.impl.Scaling;
import me.champeau.a4j.jsolex.processing.expr.impl.Stacking;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ForkJoinContext;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.math.regression.Ellipse;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static me.champeau.a4j.jsolex.processing.util.Constants.message;

public class StackingWorkflow {
    private final ForkJoinContext forkJoinContext;
    private final Broadcaster broadcaster;
    private final FileNamingStrategy namingStrategy;
    private final Crop crop;
    private final Scaling scaling;
    private final EllipseFit ellipseFit;
    private final Stacking stacking;
    private final GeometryCorrection geometryCorrector;
    private final MosaicComposition mosaicComposition;
    private final Map<Class<?>, Object> context;

    public StackingWorkflow(ForkJoinContext forkJoinContext, Broadcaster broadcaster, FileNamingStrategy namingStrategy) {
        this.forkJoinContext = forkJoinContext;
        this.broadcaster = broadcaster;
        this.namingStrategy = namingStrategy;
        this.context = Map.of(Broadcaster.class, broadcaster);
        this.crop = new Crop(forkJoinContext, context);
        this.ellipseFit = new EllipseFit(forkJoinContext, context);
        this.geometryCorrector = new GeometryCorrection(forkJoinContext, context, ellipseFit);
        this.scaling = new Scaling(forkJoinContext, context, crop);
        this.stacking = new Stacking(forkJoinContext, context, scaling, crop, broadcaster);
        this.mosaicComposition = new MosaicComposition(forkJoinContext, context, broadcaster, stacking, ellipseFit, scaling);
    }

    public void execute(Parameters parameters, List<Panel> panels, File outputDirectory) {
        var stackedImages = performStacking(parameters, panels);
        exportImages("stacked", outputDirectory, stackedImages);
        if (parameters.stackPostProcessingScriptFile() != null) {
            executeScript(parameters.stackPostProcessingScriptFile(), outputDirectory, stackedImages);
        }
        if (stackedImages.size() > 1 && parameters.createMosaic()) {
            var mosaic = performStitching(parameters, outputDirectory, stackedImages);
            if (parameters.mosaicPostProcessingScriptFile() != null) {
                executeScript(parameters.mosaicPostProcessingScriptFile(), outputDirectory, mosaic);
            }
        }
    }

    private void executeScript(File scriptFile, File outputDirectory, Object images) {
        Map<Class, Object> ctx = new HashMap<>(context);
        var evaluator = new DefaultImageScriptExecutor(
            forkJoinContext,
            d -> ImageWrapper32.createEmpty(),
            ctx,
            broadcaster
        );
        try {
            evaluator.putVariable("image", images);
            var result = evaluator.execute(scriptFile.toPath(), ImageMathScriptExecutor.SectionKind.SINGLE);
            maybeRenderErrors(result);
            result.imagesByLabel().forEach((label, img) -> exportImages(label, outputDirectory, List.of(img)));
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    private void maybeRenderErrors(ImageMathScriptResult result) {
        var invalidExpressions = result.invalidExpressions();
        var errorCount = invalidExpressions.size();
        if (errorCount > 0) {
            String message = invalidExpressions.stream()
                .map(invalidExpression -> "Expression '" + invalidExpression.label() + "' (" + invalidExpression.expression() + ") : " + invalidExpression.error().getMessage())
                .collect(Collectors.joining(System.lineSeparator()));
            broadcaster.broadcast(new NotificationEvent(new Notification(
                Notification.AlertType.ERROR,
                message("error.processing.script"),
                message("script.errors." + (errorCount == 1 ? "single" : "many")),
                message
            )));
        }
    }

    private List<ImageWrapper32> performStacking(Parameters parameters, List<Panel> panels) {
        return panels.stream()
            .map(Panel::asImages)
            .filter(l -> !l.isEmpty())
            .map(p -> stackPanel(p, parameters))
            .toList();
    }

    private ImageWrapper32 performStitching(Parameters parameters, File outputDirectory, List<ImageWrapper32> stackedImages) {
        var cropped = crop.autocrop2(List.of(stackedImages));
        if (cropped instanceof List<?> list) {
            var croppedImages = list.stream()
                .filter(ImageWrapper32.class::isInstance)
                .map(ImageWrapper32.class::cast)
                .toList();
            var fileName = namingStrategy.render(0, Constants.TYPE_PROCESSED, "mosaic", "standalone");
            var mosaic = mosaicComposition.mosaic(croppedImages, parameters.mosaicTileSize(), parameters.mosaicOverlap());
            broadcaster.broadcast(new ImageGeneratedEvent(
                new GeneratedImage(
                    GeneratedImageKind.COMPOSITION,
                    "mosaic",
                    outputDirectory.toPath().resolve(fileName),
                    mosaic
                )
            ));
            return mosaic;
        } else if (cropped instanceof ImageWrapper32 image) {
            return image;
        }
        throw new ProcessingException("Unexpected result from mosaicing: " + cropped.getClass().getName());
    }

    private void exportImages(String name, File outputDirectory, List<? extends ImageWrapper> stackedImages) {
        for (int i = 0; i < stackedImages.size(); i++) {
            var label = String.format("%02d_%s", i, name);
            var fileName = namingStrategy.render(i, Constants.TYPE_PROCESSED, label, "standalone");
            var stackedImage = stackedImages.get(i);
            broadcaster.broadcast(new ImageGeneratedEvent(
                new GeneratedImage(
                    GeneratedImageKind.COMPOSITION,
                    label,
                    outputDirectory.toPath().resolve(fileName),
                    stackedImage
                )
            ));
        }
    }

    private ImageWrapper32 stackPanel(List<ImageWrapper32> panel, Parameters parameters) {
        var images = panel
            .stream()
            .parallel()
            .map(img -> {
                if (parameters.forceEllipseFit() || img.findMetadata(Ellipse.class).isEmpty()) {
                    return ellipseFit.performEllipseFitting(img);
                }
                return img;
            })
            .toList();
        if (parameters.fixGeometry()) {
            images = images.stream()
                .parallel()
                .map(geometryCorrector::fixGeometry)
                .toList();
        }
        return stacking.stack(images, parameters.stackingTileSize(), parameters.stackingOverlap());
    }

    public record Parameters(
        int stackingTileSize,
        float stackingOverlap,
        boolean forceEllipseFit,
        boolean fixGeometry,
        File stackPostProcessingScriptFile,
        boolean createMosaic,
        int mosaicTileSize,
        float mosaicOverlap,
        File mosaicPostProcessingScriptFile
        ) {

    }

    public record Panel(
        List<File> files
    ) {
        public List<ImageWrapper32> asImages() {
            var perKind = files.stream()
                .parallel()
                .map(Loader::loadImage)
                .collect(groupingBy(ImageWrapper::getClass));
            var monoImages = perKind.get(ImageWrapper32.class);
            var unexpectedKinds = new HashSet<>(perKind.keySet());
            unexpectedKinds.remove(ImageWrapper32.class);
            if (!unexpectedKinds.isEmpty()) {
                throw new ProcessingException(message("error.mosaic.non.mono"));
            }
            if (monoImages == null) {
                return List.of();
            }
            return monoImages.stream()
                .map(ImageWrapper32.class::cast)
                .toList();
        }
    }
}
