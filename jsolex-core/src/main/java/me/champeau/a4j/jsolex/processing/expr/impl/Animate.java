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

import me.champeau.a4j.jsolex.processing.event.ProgressEvent;
import me.champeau.a4j.jsolex.processing.expr.FileOutput;
import me.champeau.a4j.jsolex.processing.sun.Broadcaster;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import me.champeau.a4j.jsolex.processing.util.RGBImage;
import me.champeau.a4j.ser.EightBitConversionSupport;
import org.jcodec.api.SequenceEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.common.model.Rational;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static me.champeau.a4j.ser.EightBitConversionSupport.to8BitImage;

public class Animate extends AbstractFunctionImpl {
    private static final Logger LOGGER = LoggerFactory.getLogger(Animate.class);

    private static final int DEFAULT_DELAY = 250;

    public Animate(Map<Class<?>, Object> context, Broadcaster broadcaster) {
        super(context, broadcaster);
    }

    public Object createAnimation(List<Object> arguments) {
        if (arguments.size() > 2) {
            throw new IllegalArgumentException("anim takes 1 or 2 arguments (images, delay)");
        }
        var images = arguments.getFirst();
        if (!(images instanceof List<?> listOfImages)) {
            throw new IllegalArgumentException("anim must use a list of images as first argument");
        }
        if (listOfImages.isEmpty()) {
            throw new IllegalArgumentException("anim must use a non-empty list of images as first argument");
        }
        if (listOfImages.getFirst() instanceof List) {
            return expandToImageList("anim", arguments, this::createAnimation);
        }
        var delay = arguments.size() == 1 ? DEFAULT_DELAY : doubleArg(arguments, 1);
        try {
            var tempFile = Files.createTempFile("video_jsolex", ".mp4");
            var frames = (List) arguments.get(0);
            if (FfmegEncoder.isAvailable()) {
                encodeWithFfmpeg(frames, tempFile, (int) delay);
            }
            var encoder = SequenceEncoder.createWithFps(NIOUtils.writableChannel(tempFile.toFile()),
                    new Rational((int) (1000 / delay), 1));
            double progress = 0;
            for (Object argument : frames) {
                broadcaster.broadcast(ProgressEvent.of(progress/frames.size(), "Encoding frame " + (int) progress + "/" + frames.size()));
                if (argument instanceof FileBackedImage fileBackedImage) {
                    argument = fileBackedImage.unwrapToMemory();
                }
                if (argument instanceof ImageWrapper32 image) {
                    addMonoFrame(encoder, image);
                }
                progress++;
            }
            encoder.finish();
            broadcaster.broadcast(ProgressEvent.of(1, "Encoding finished"));
            return new FileOutput(tempFile);
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    private static void encodeWithFfmpeg(List frames, Path tempFile, int delay) {
        try {
            FfmegEncoder.encode(frames, tempFile.toFile(), delay);
        } catch (IOException e) {
            // fallback to jcodec
            LOGGER.info("Failed to encode video with ffmpeg, falling back to jcodec", e);
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

    private static void addColorFrame(SequenceEncoder encoder, ImageWrapper image, Stream<float[]> colorChannelsStream, int width, int height) {
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
}
