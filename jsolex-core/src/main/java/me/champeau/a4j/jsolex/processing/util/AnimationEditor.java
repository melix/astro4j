/*
 * Copyright 2026-2026 the original author or authors.
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
package me.champeau.a4j.jsolex.processing.util;

import me.champeau.a4j.jsolex.processing.expr.impl.FfmegEncoder;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.api.specific.AVCMP4Adaptor;
import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.containers.mp4.MP4TrackType;
import org.jcodec.containers.mp4.boxes.NodeBox;
import org.jcodec.containers.mp4.boxes.SampleSizesBox;
import org.jcodec.containers.mp4.boxes.TrakBox;
import org.jcodec.containers.mp4.demuxer.CodecMP4DemuxerTrack;
import org.jcodec.containers.mp4.demuxer.MP4Demuxer;
import org.jcodec.scale.ColorUtil;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Applies geometric operations (rotations, flips, crops) to every frame of an
 * existing animation file, without ever holding more than a handful of frames in memory.
 */
public final class AnimationEditor {
    private static final int REORDER_DEPTH = 8;

    private AnimationEditor() {
    }

    /**
     * A geometric operation applied to each frame of an animation.
     */
    public sealed interface Operation permits Rotate, Flip, Crop {
        /**
         * Applies this operation to an image.
         *
         * @param image the source image
         * @return the transformed image
         */
        BufferedImage apply(BufferedImage image);
    }

    /**
     * Rotation by a multiple of 90 degrees.
     *
     * @param quarterTurns the number of clockwise quarter turns, negative for counter-clockwise
     */
    public record Rotate(int quarterTurns) implements Operation {
        @Override
        public BufferedImage apply(BufferedImage image) {
            var turns = Math.floorMod(quarterTurns, 4);
            if (turns == 0) {
                return image;
            }
            var width = image.getWidth();
            var height = image.getHeight();
            var source = pixels(image);
            var swapped = turns % 2 == 1;
            var outWidth = swapped ? height : width;
            var outHeight = swapped ? width : height;
            var target = new int[outWidth * outHeight];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int tx;
                    int ty;
                    switch (turns) {
                        case 1 -> {
                            tx = height - 1 - y;
                            ty = x;
                        }
                        case 2 -> {
                            tx = width - 1 - x;
                            ty = height - 1 - y;
                        }
                        default -> {
                            tx = y;
                            ty = width - 1 - x;
                        }
                    }
                    target[ty * outWidth + tx] = source[y * width + x];
                }
            }
            return fromPixels(target, outWidth, outHeight);
        }
    }

    /**
     * Mirror along one axis.
     *
     * @param vertical true to flip top and bottom, false to flip left and right
     */
    public record Flip(boolean vertical) implements Operation {
        @Override
        public BufferedImage apply(BufferedImage image) {
            var width = image.getWidth();
            var height = image.getHeight();
            var source = pixels(image);
            var target = new int[width * height];
            for (int y = 0; y < height; y++) {
                var sy = vertical ? height - 1 - y : y;
                for (int x = 0; x < width; x++) {
                    var sx = vertical ? x : width - 1 - x;
                    target[y * width + x] = source[sy * width + sx];
                }
            }
            return fromPixels(target, width, height);
        }
    }

    /**
     * Crop to a rectangle, clamped to the image bounds.
     *
     * @param x the left coordinate
     * @param y the top coordinate
     * @param width the width
     * @param height the height
     */
    public record Crop(int x, int y, int width, int height) implements Operation {
        @Override
        public BufferedImage apply(BufferedImage image) {
            var left = Math.clamp(x, 0, image.getWidth() - 1);
            var top = Math.clamp(y, 0, image.getHeight() - 1);
            var w = Math.clamp(width, 1, image.getWidth() - left);
            var h = Math.clamp(height, 1, image.getHeight() - top);
            var target = new int[w * h];
            image.getRGB(left, top, w, h, target, 0, w);
            return fromPixels(target, w, h);
        }
    }

    /**
     * Applies a sequence of operations to an image.
     *
     * @param image the source image
     * @param operations the operations, applied in order
     * @return the transformed image
     */
    public static BufferedImage apply(BufferedImage image, List<? extends Operation> operations) {
        var result = image;
        for (var operation : operations) {
            result = operation.apply(result);
        }
        return result;
    }

    /**
     * Reads a single frame of an animation.
     *
     * @param file the animation file (MP4 or GIF)
     * @param seconds the position in the animation; the nearest frame is returned
     * @return the frame
     * @throws IOException if the file cannot be read
     */
    public static BufferedImage readFrame(Path file, double seconds) throws IOException {
        if (isGif(file)) {
            try (var source = new GifFrameSource(file)) {
                var index = Math.clamp((long) (seconds * 1000 / source.delayMs()), 0, source.frameCount() - 1);
                return source.reader.read((int) index);
            }
        }
        try (var source = new Mp4FrameSource(file)) {
            var frame = source.frameAt(seconds);
            if (frame == null) {
                throw new IOException("Unable to read a frame from " + file);
            }
            return frame;
        }
    }

    /**
     * Applies operations to every frame of an animation and writes the result
     * in the same format as the source.
     *
     * @param source the source animation (MP4 or GIF)
     * @param targetBase the target path, without extension
     * @param operations the operations to apply, in order
     * @param progress receives progress between 0 and 1
     * @return the file which was written
     * @throws IOException if reading or writing fails
     */
    public static Path transform(Path source, Path targetBase, List<? extends Operation> operations, Consumer<Double> progress) throws IOException {
        var format = isGif(source) ? AnimationFormat.GIF : AnimationFormat.MP4;
        try (var frames = isGif(source) ? new GifFrameSource(source) : new Mp4FrameSource(source)) {
            if (format == AnimationFormat.MP4 && FfmegEncoder.isAvailable()) {
                var result = transformWithFfmpeg(frames, targetBase, operations, progress);
                if (result != null) {
                    return result;
                }
            }
            var files = VideoEncoder.encodeToMultipleFormats(
                    targetBase.toString(),
                    Set.of(format),
                    frames.frameCount(),
                    frames.fps(),
                    frames.delayMs(),
                    i -> {
                        try {
                            var frame = frames.next();
                            return frame == null ? null : apply(frame, operations);
                        } catch (IOException e) {
                            throw new ProcessingException(e);
                        }
                    },
                    progress
            );
            if (files.isEmpty()) {
                throw new IOException("No frame found in " + source);
            }
            return files.getFirst().toPath();
        }
    }

    private static Path transformWithFfmpeg(FrameSource frames, Path targetBase, List<? extends Operation> operations, Consumer<Double> progress) throws IOException {
        var tempDir = TemporaryFolder.newTempDir("jsolex-anim-edit-");
        try {
            var count = 0;
            BufferedImage frame;
            while ((frame = frames.next()) != null) {
                var file = tempDir.resolve(String.format("frame-%04d.png", count++));
                ImageIO.write(apply(frame, operations), "png", file.toFile());
                if (progress != null) {
                    progress.accept(0.5 * count / Math.max(1, frames.frameCount()));
                }
            }
            if (count == 0) {
                return null;
            }
            var output = new File(targetBase + AnimationFormat.MP4.extension());
            var total = count;
            var success = FfmegEncoder.encodeFrameDirectory(tempDir, output, frames.delayMs(), encoded -> {
                if (progress != null) {
                    progress.accept(0.5 + 0.5 * Math.min(encoded, total) / total);
                }
            });
            if (progress != null) {
                progress.accept(1.0);
            }
            return success ? output.toPath() : null;
        } finally {
            try (var files = Files.list(tempDir)) {
                for (var file : files.toList()) {
                    Files.deleteIfExists(file);
                }
            }
            Files.deleteIfExists(tempDir);
        }
    }

    private static boolean isGif(Path file) {
        return file.getFileName().toString().toLowerCase(Locale.US).endsWith(".gif");
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static BufferedImage fromPixels(int[] pixels, int width, int height) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    private static BufferedImage toBufferedImage(Picture picture, boolean fullRange) {
        var source = picture;
        if (fullRange && picture.getColor() == ColorSpace.YUV420) {
            source = Picture.createPicture(picture.getWidth(), picture.getHeight(), picture.getData(), ColorSpace.YUV420J);
            source.setCrop(picture.getCrop());
        }
        var rgb = source;
        if (source.getColor() != ColorSpace.RGB) {
            rgb = Picture.create(source.getWidth(), source.getHeight(), ColorSpace.RGB);
            rgb.setCrop(source.getCrop());
            ColorUtil.getTransform(source.getColor(), ColorSpace.RGB).transform(source, rgb);
        }
        var width = rgb.getCroppedWidth();
        var height = rgb.getCroppedHeight();
        var stride = rgb.getWidth();
        var startX = rgb.getStartX();
        var startY = rgb.getStartY();
        var data = rgb.getPlaneData(0);
        var pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            var row = (y + startY) * stride;
            for (int x = 0; x < width; x++) {
                var idx = 3 * (row + x + startX);
                var r = data[idx] + 128;
                var g = data[idx + 1] + 128;
                var b = data[idx + 2] + 128;
                pixels[y * width + x] = (r << 16) | (g << 8) | b;
            }
        }
        return fromPixels(pixels, width, height);
    }

    /**
     * H.264 streams without VUI parameters are treated as full range, which is what
     * the jcodec encoder produces; streams with VUI parameters honor the range flag.
     */
    private static boolean isFullRange(ByteBuffer codecPrivate) {
        if (codecPrivate == null) {
            return true;
        }
        for (var nal : H264Utils.splitFrame(codecPrivate.duplicate())) {
            if ((nal.get(nal.position()) & 0x1F) == 7) {
                nal.position(nal.position() + 1);
                var vui = H264Utils.readSPS(nal).vuiParams;
                return vui == null || vui.videoFullRangeFlag;
            }
        }
        return true;
    }

    private interface FrameSource extends AutoCloseable {
        int frameCount();

        int fps();

        int delayMs();

        BufferedImage next() throws IOException;

        @Override
        void close() throws IOException;
    }

    private static final class GifFrameSource implements FrameSource {
        private final ImageInputStream input;
        private final ImageReader reader;
        private final int frameCount;
        private final int delayMs;
        private int index;

        GifFrameSource(Path file) throws IOException {
            input = ImageIO.createImageInputStream(file.toFile());
            reader = ImageIO.getImageReadersByFormatName("gif").next();
            reader.setInput(input, false);
            frameCount = reader.getNumImages(true);
            delayMs = readDelayMs();
        }

        private int readDelayMs() throws IOException {
            var metadata = reader.getImageMetadata(0);
            var root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());
            var nodes = root.getElementsByTagName("GraphicControlExtension");
            if (nodes.getLength() > 0) {
                var delay = ((IIOMetadataNode) nodes.item(0)).getAttribute("delayTime");
                if (!delay.isBlank()) {
                    return Math.max(10, 10 * Integer.parseInt(delay));
                }
            }
            return 100;
        }

        @Override
        public int frameCount() {
            return frameCount;
        }

        @Override
        public int fps() {
            return Math.max(1, 1000 / delayMs);
        }

        @Override
        public int delayMs() {
            return delayMs;
        }

        @Override
        public BufferedImage next() throws IOException {
            if (index >= frameCount) {
                return null;
            }
            return reader.read(index++);
        }

        @Override
        public void close() throws IOException {
            reader.dispose();
            input.close();
        }
    }

    private static final class Mp4FrameSource implements FrameSource {
        private final SeekableByteChannel channel;
        private final FrameGrab grab;
        private final int frameCount;
        private final int fps;
        private final double duration;
        private final boolean fullRange;
        private final PriorityQueue<DecodedFrame> reorderBuffer = new PriorityQueue<>(Comparator.comparingDouble(DecodedFrame::timestamp).thenComparingLong(DecodedFrame::sequence));
        private long sequence;
        private boolean exhausted;

        Mp4FrameSource(Path file) throws IOException {
            channel = NIOUtils.readableChannel(file.toFile());
            try {
                var movie = MP4Demuxer.createMP4Demuxer(channel).getMovie();
                var trak = Arrays.stream(movie.getTracks())
                        .filter(t -> MP4Demuxer.getTrackType(t) == MP4TrackType.VIDEO)
                        .findFirst()
                        .orElseThrow(() -> new IOException("No video track found in " + file));
                expandConstantSampleSizes(trak);
                var track = new CodecMP4DemuxerTrack(movie, trak, channel);
                var meta = track.getMeta();
                grab = new FrameGrab(track, new AVCMP4Adaptor(meta));
                frameCount = meta.getTotalFrames();
                duration = meta.getTotalDuration();
                fps = duration > 0 ? Math.max(1, (int) Math.round(frameCount / duration)) : 25;
                fullRange = isFullRange(meta.getCodecPrivate());
            } catch (IOException | RuntimeException e) {
                channel.close();
                throw e;
            }
        }

        /**
         * jcodec cannot demux video tracks whose sample table declares a single constant
         * sample size (which happens for single-frame files), so the table is rewritten
         * with one explicit entry per sample.
         */
        private static void expandConstantSampleSizes(TrakBox trak) {
            var stsz = trak.getStsz();
            if (stsz.getDefaultSize() != 0) {
                var sizes = new int[stsz.getCount()];
                Arrays.fill(sizes, stsz.getDefaultSize());
                var stbl = NodeBox.findFirstPath(trak, NodeBox.class, new String[]{"mdia", "minf", "stbl"});
                stbl.replace("stsz", SampleSizesBox.createSampleSizesBox2(sizes));
            }
        }

        /**
         * Returns the frame whose presentation time is the closest to the given position.
         * Seeking lands on the preceding key frame and frames are then read in presentation
         * order, because the demuxer positions itself by decoding order, which differs from
         * presentation order when the stream contains B-frames.
         */
        BufferedImage frameAt(double seconds) throws IOException {
            var target = Math.clamp(seconds, 0, Math.max(0, duration - 1e-3));
            try {
                grab.seekToSecondSloppy(target);
            } catch (JCodecException e) {
                throw new IOException(e);
            }
            reorderBuffer.clear();
            exhausted = false;
            var halfFrame = 0.5 / fps;
            DecodedFrame last = null;
            DecodedFrame frame;
            while ((frame = nextDecoded()) != null) {
                last = frame;
                if (frame.timestamp() + halfFrame >= target) {
                    break;
                }
            }
            return last == null ? null : last.image();
        }

        @Override
        public int frameCount() {
            return frameCount;
        }

        @Override
        public int fps() {
            return fps;
        }

        @Override
        public int delayMs() {
            return 1000 / fps;
        }

        @Override
        public BufferedImage next() throws IOException {
            var frame = nextDecoded();
            return frame == null ? null : frame.image();
        }

        private DecodedFrame nextDecoded() throws IOException {
            while (!exhausted && reorderBuffer.size() < REORDER_DEPTH) {
                var frame = grab.getNativeFrameWithMetadata();
                if (frame == null) {
                    exhausted = true;
                } else {
                    reorderBuffer.add(new DecodedFrame(frame.getTimestamp(), sequence++, toBufferedImage(frame.getPicture(), fullRange)));
                }
            }
            return reorderBuffer.poll();
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }

        private record DecodedFrame(double timestamp, long sequence, BufferedImage image) {
        }
    }
}
