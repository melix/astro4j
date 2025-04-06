package me.champeau.a4j.jsolex.processing.ser;

import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.bayer.ImageConverter;

import java.nio.ByteBuffer;

/**
 * A fast image converter dedicated to the ideal case in JSol'Ex where we have
 * a mono SER file. This does the equivalent of:
 * <pre>
 *     new FloatPrecisionImageConverter(
 *                 new ChannelExtractingConverter(
 *                         new DemosaicingRGBImageConverter(
 *                                 new BilinearDemosaicingStrategy(),
 *                                 colorMode
 *                         ),
 *                         GREEN
 *                 )
 *         );
 * </pre>
 * in a single pass.
 */
public class FastImageConverter implements ImageConverter<float[][]> {
    private final boolean vflip;

    public FastImageConverter(boolean vflip) {
        this.vflip = vflip;
    }

    @Override
    public float[][] createBuffer(ImageGeometry geometry) {
        return new float[geometry.height()][geometry.width()];
    }

    @Override
    public void convert(int frameId, ByteBuffer frameData, ImageGeometry geometry, float[][] outputData) {
        var height = geometry.height();
        var width = geometry.width();
        int bytesPerPixel = geometry.getBytesPerPixel();
        int bitsToDiscard = bytesPerPixel == 1 ? 8 - geometry.pixelDepthPerPlane() : 16 - geometry.pixelDepthPerPlane();
        if (bytesPerPixel == 1) {
            convertBPP1(frameData, outputData, height, width, bitsToDiscard);
        } else {
            convertBPP2(frameData, outputData, height, width, bitsToDiscard);
        }
    }

    private void convertBPP1(ByteBuffer frameData, float[][] outputData, int height, int width, int bitsToDiscard) {
        for (int y = 0; y < height; y++) {
            var lineIdx = vflip ? height - 1 - y : y;
            var line = outputData[lineIdx];
            for (int x = 0; x < width; x++) {
                line[x] = readColorBPP1(frameData, bitsToDiscard);
            }
        }
    }

    private void convertBPP2(ByteBuffer frameData, float[][] outputData, int height, int width, int bitsToDiscard) {
        for (int y = 0; y < height; y++) {
            var lineIdx = vflip ? height - 1 - y : y;
            var line = outputData[lineIdx];
            for (int x = 0; x < width; x++) {
                line[x] = readColorBPP2(frameData, bitsToDiscard);
            }
        }
    }

    private int readColorBPP1(ByteBuffer frameData, int bitsToDiscard) {
        // Data of between 1 and 8 bits should be stored aligned with the most significant bit
        int v = frameData.get() >> bitsToDiscard;
        var next = (short) ((v & 0xFF) << 8);
        return Short.toUnsignedInt(next);
    }

    private int readColorBPP2(ByteBuffer frameData, int bitsToDiscard) {
        // Data between 9 and 16 bits should be stored aligned with the least significant bit
        var v = Short.reverseBytes(frameData.getShort()) & 0xFFFF;
        var next = (short) (v << bitsToDiscard);
        return Short.toUnsignedInt(next);
    }
}
