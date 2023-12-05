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
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;

import java.io.File;
import java.util.List;
import java.util.Map;

public class StackingWorkflow {
    private final Broadcaster broadcaster;
    private final FileNamingStrategy namingStrategy;
    private final Crop crop;
    private final Scaling scaling;
    private final EllipseFit ellipseFit;
    private final Stacking stacking;
    private final GeometryCorrection geometryCorrector;
    private final MosaicComposition mosaicComposition;

    public StackingWorkflow(ForkJoinContext forkJoinContext, Broadcaster broadcaster, FileNamingStrategy namingStrategy) {
        this.broadcaster = broadcaster;
        this.namingStrategy = namingStrategy;
        var context = Map.<Class<?>, Object>of(Broadcaster.class, broadcaster);
        this.crop = new Crop(forkJoinContext, context);
        this.ellipseFit = new EllipseFit(forkJoinContext, context);
        this.geometryCorrector = new GeometryCorrection(forkJoinContext, context, ellipseFit);
        this.scaling = new Scaling(forkJoinContext, context, crop);
        this.stacking = new Stacking(forkJoinContext, context, scaling, crop, broadcaster);
        this.mosaicComposition = new MosaicComposition(forkJoinContext, context, broadcaster, stacking, ellipseFit, scaling);
    }

    public void execute(Parameters parameters, List<Panel> panels, File outputDirectory) {
        var stackedImages = performStacking(parameters, panels);
        exportStackedImages(outputDirectory, stackedImages);
        if (stackedImages.size() > 1 && parameters.createMosaic()) {
            performStitching(parameters, outputDirectory, stackedImages);
        }
    }

    private List<ImageWrapper32> performStacking(Parameters parameters, List<Panel> panels) {
        return panels.stream()
            .map(p -> stackPanel(p, parameters))
            .toList();
    }

    private void performStitching(Parameters parameters, File outputDirectory, List<ImageWrapper32> stackedImages) {
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
        }
    }

    private void exportStackedImages(File outputDirectory, List<ImageWrapper32> stackedImages) {
        for (int i = 0; i < stackedImages.size(); i++) {
            var label = String.format("%02d_stacked", i);
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

    private ImageWrapper32 stackPanel(Panel panel, Parameters parameters) {
        var images = panel.asImages()
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
        boolean createMosaic,
        int mosaicTileSize,
        float mosaicOverlap
    ) {

    }

    public record Panel(
        List<File> files
    ) {
        public List<ImageWrapper32> asImages() {
            return files.stream()
                .parallel()
                .map(Loader::loadImage)
                .filter(ImageWrapper32.class::isInstance)
                .map(ImageWrapper32.class::cast)
                .toList();
        }
    }
}
