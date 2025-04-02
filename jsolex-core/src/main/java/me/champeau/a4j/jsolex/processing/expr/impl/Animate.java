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
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.FileOutput;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.sun.workflow.SourceInfo;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.jsolex.processing.util.TemporaryFolder;
import me.champeau.a4j.ser.EightBitConversionSupport;
import org.jcodec.api.SequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static me.champeau.a4j.ser.EightBitConversionSupport.to8BitImage;

public class Animate extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(Animate.class);

    private static final int DEFAULT_DELAY = 250;

    public Animate(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object createAnimation(Map<String ,Object> arguments) {
        BuiltinFunction.ANIM.validateArgs(arguments);
        var images = arguments.get("images");
        if (!(images instanceof List<?> listOfImages)) {
            throw new IllegalArgumentException("anim must use a list of images as first argument");
        }
        if (listOfImages.isEmpty()) {
            throw new IllegalArgumentException("anim must use a non-empty list of images as first argument");
        }
        if (listOfImages.getFirst() instanceof List) {
            return expandToImageList("anim", "images", arguments, this::createAnimation);
        }
        var delay = doubleArg(arguments, "delay", DEFAULT_DELAY);
        try {
            var tempFile = TemporaryFolder.newTempFile("video_jsolex", ".mp4");
            var frames = (List) images;
            if (FfmegEncoder.isAvailable()) {
                if (encodeWithFfmpeg(broadcaster, newOperation(), frames, tempFile, (int) delay)) {
                    return new FileOutput(tempFile);
                }
            }
            var encoder = SequenceEncoder.createWithFps(NIOUtils.writableChannel(tempFile.toFile()),
                    new Rational((int) (1000 / delay), 1));
            double progress = 0;
            var progressOperation = newOperation().createChild("Encoding frame");
            for (Object argument : frames) {
                broadcaster.broadcast(progressOperation.update(progress/frames.size(), "Encoding frame " + (int) progress + "/" + frames.size()));
                if (argument instanceof FileBackedImage fileBackedImage) {
                    argument = fileBackedImage.unwrapToMemory();
                }
                if (argument instanceof ImageWrapper32 image) {
                    addMonoFrame(encoder, image);
                } else if (argument instanceof RGBImage rgb) {
                    addColorFrame(encoder, rgb);
                }
                progress++;
            }
            encoder.finish();
            broadcaster.broadcast(progressOperation.complete());
            return new FileOutput(tempFile);
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    private static boolean encodeWithFfmpeg(Broadcaster broadcaster, ProgressOperation operation, List frames, Path tempFile, int delay) {
        try {
            return FfmegEncoder.encode(broadcaster, operation, frames, tempFile.toFile(), delay);
        } catch (IOException e) {
            // fallback to jcodec
            LOGGER.info("Failed to encode video with ffmpeg, falling back to jcodec", e);
            return false;
        }
    }

    private static void addMonoFrame(SequenceEncoder encoder, ImageWrapper32 image) {
        int width = image.width();
        int height = image.height();
        if (width % 2 == 1) {
            width--;
        }
        if (height % 2 == 1) {
            height--;
        }
        var bytes = to8BitImage(image.data());
        if (width != image.width() || height != image.height()) {
            var data = new byte[width * height];
            for (int y = 0; y < height; y++) {
                System.arraycopy(bytes, y * image.width(), data, y * width, width);
            }
            bytes = data;
        }
        var rgb = new byte[3 * bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            var v = (byte) (bytes[i] - 128);
            rgb[3 * i] = v;
            rgb[3 * i + 1] = v;
            rgb[3 * i + 2] = v;
        }
        try {
            var pic = new Picture(width, height, new byte[][]{rgb}, null, ColorSpace.RGB, 0, null);
            encoder.encodeNativeFrame(pic);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void addColorFrame(SequenceEncoder encoder, RGBImage image) {
        int width = image.width();
        int height = image.height();
        if (width % 2 == 1) {
            width--;
        }
        if (height % 2 == 1) {
            height--;
        }
        var colorChannelsStream = Stream.of(image.r(), image.g(), image.b());
        addColorFrame(encoder, image, colorChannelsStream, width, height);
    }

    private static void addColorFrame(SequenceEncoder encoder, ImageWrapper image, Stream<float[][]> colorChannelsStream, int width, int height) {
        var bytes = colorChannelsStream.map(EightBitConversionSupport::to8BitImage).toArray(byte[][]::new);
        if (width != image.width() || height != image.height()) {
            for (int channel = 0; channel < 3; channel++) {
                var data = new byte[width * height];
                for (int y = 0; y < height; y++) {
                    System.arraycopy(bytes[channel], y * image.width(), data, y * width, width);
                }
                bytes[channel] = data;
            }
        }
        var rgb = new byte[3 * width * height];
        for (int channel = 0; channel < 3; channel++) {
            var data = bytes[channel];
            for (int i = 0; i < data.length; i++) {
                var v = (byte) (data[i] - 128);
                rgb[3 * i + channel] = v;
            }
        }
        try {
            var pic = new Picture(width, height, new byte[][]{rgb}, null, ColorSpace.RGB, 0, null);
            encoder.encodeNativeFrame(pic);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This function interpolates between two images by blending 2 subsequent images together
     * using a number of steps and a specific transition type.
     *
     * @param arguments
     * @return
     */
    public Object transition(Map<String ,Object> arguments) {
        BuiltinFunction.TRANSITION.validateArgs(arguments);
        var images = arguments.get("images");
        if (!(images instanceof List<?> listOfImages)) {
            throw new IllegalArgumentException("transition must use a list of images as first argument");
        }
        var steps = intArg(arguments, "steps", 10);
        if (steps < 1) {
            throw new IllegalArgumentException("transition must use a positive number of steps as second argument");
        }
        var transitionType = Transition.valueOf(stringArg(arguments, "type", "linear").toUpperCase(Locale.US));
        var unit = stringArg(arguments, "unit", null);
        boolean requiresTimestamps = unit != null;
        if (listOfImages.size() < 2) {
            return listOfImages;
        }
        var output = new ArrayList<ImageWrapper>();
        for (int i = 0; i < listOfImages.size() - 1; i++) {
            var first = ((ImageWrapper) listOfImages.get(i)).unwrapToMemory();
            var second = ((ImageWrapper) listOfImages.get(i + 1)).unwrapToMemory();
            if (first.width() != second.width() || first.height() != second.height()) {
                throw new IllegalArgumentException("transition must use images of the same size");
            }
            if (!(first instanceof ImageWrapper32 mono1) || !(second instanceof ImageWrapper32 mono2)) {
                throw new IllegalArgumentException("transition only supports mono images");
            }
            var firstData = mono1.data();
            var secondData = mono2.data();
            int scaledSteps = steps;
            if (requiresTimestamps) {
                var ts1 = mono1.findMetadata(SourceInfo.class).map(SourceInfo::dateTime).orElse(null);
                var ts2 = mono2.findMetadata(SourceInfo.class).map(SourceInfo::dateTime).orElse(null);
                if (ts1 == null || ts2 == null) {
                    throw new IllegalArgumentException("transition requires timestamps to be set on both images");
                }
                var secondsBetweenImages = ChronoUnit.SECONDS.between(ts1, ts2);
                // if steps = 10 and unit = "ips", then it means we want 10 images per second
                scaledSteps = Math.max(1, switch (unit) {
                    case "ips" -> Math.round(secondsBetweenImages * steps);
                    case "ipm" -> Math.round(secondsBetweenImages * steps / 60);
                    case "iph" -> Math.round(secondsBetweenImages * steps / 3600);
                    default -> throw new IllegalArgumentException("Unsupported unit '" + unit + "'");
                });
            }
            int finalSteps = scaledSteps;
            output.addAll(
                    IntStream.range(0, finalSteps)
                            .parallel()
                            .mapToObj(step -> {
                                var data = new float[first.height()][first.width()];
                                var coef = transitionType.coef(step, finalSteps);
                                for (int y = 0; y < first.height(); y++) {
                                    for (int x = 0; x < first.width(); x++) {
                                        data[y][x] = (float) (firstData[y][x] * (1 - coef) + secondData[y][x] * coef);
                                    }
                                }
                                return FileBackedImage.wrap(new ImageWrapper32(first.width(), first.height(), data, new HashMap<>(first.metadata())));
                            })
                            .toList()
            );
        }
        return Collections.unmodifiableList(output);
    }

    enum Transition {
        LINEAR,
        EASE_IN,
        EASE_OUT,
        EASE_IN_OUT;

        double coef(int step, int totalSteps) {
            double t = ((double) step) / totalSteps;
            switch (this) {
                case LINEAR -> {
                    return t;
                }
                case EASE_IN -> {
                    return t * t;
                }
                case EASE_OUT -> {
                    return 1 - Math.pow(1 - t, 2);
                }
                case EASE_IN_OUT -> {
                    if (t < 0.5) {
                        return 2 * t * t;
                    } else {
                        return 1 - Math.pow(-2 * t + 2, 2) / 2;
                    }
                }
            }
            throw new IllegalStateException("Unexpected value: " + this);
        }
    }
}
