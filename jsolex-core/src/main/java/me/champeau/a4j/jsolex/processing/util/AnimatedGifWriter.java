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
package me.champeau.a4j.jsolex.processing.util;

import javax.imageio.IIOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Writer for creating animated GIF files.
 */
public class AnimatedGifWriter implements AutoCloseable {
    private final ImageWriter writer;
    private final ImageWriteParam params;
    private final IIOMetadata metadata;
    private final ImageOutputStream output;

    /**
     * Creates a new animated GIF writer.
     *
     * @param out the output stream to write to
     * @param imageType the image type (BufferedImage type constant)
     * @param delay the delay between frames in milliseconds
     * @param loop whether to loop the animation
     * @throws IOException if an I/O error occurs
     */
    public AnimatedGifWriter(ImageOutputStream out, int imageType, int delay, boolean loop) throws IOException {
        writer = newWriter();
        params = writer.getDefaultWriteParam();

        var imageTypeSpecifier = ImageTypeSpecifier.createFromBufferedImageType(imageType);
        metadata = writer.getDefaultImageMetadata(imageTypeSpecifier, params);

        var metaFormatName = metadata.getNativeMetadataFormatName();
        var root = (IIOMetadataNode) metadata.getAsTree(metaFormatName);

        var graphicsControlExtensionNode = getNode(root, "GraphicControlExtension");
        graphicsControlExtensionNode.setAttribute("disposalMethod", "none");
        graphicsControlExtensionNode.setAttribute("userInputFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
        graphicsControlExtensionNode.setAttribute("delayTime", Integer.toString(delay / 10));
        graphicsControlExtensionNode.setAttribute("transparentColorIndex", "0");

        var appExtensionsNode = getNode(root, "ApplicationExtensions");
        var child = new IIOMetadataNode("ApplicationExtension");
        child.setAttribute("applicationID", "NETSCAPE");
        child.setAttribute("authenticationCode", "2.0");

        var loopCount = loop ? 0 : 1;
        child.setUserObject(new byte[]{0x1, (byte) loopCount, 0});
        appExtensionsNode.appendChild(child);

        metadata.setFromTree(metaFormatName, root);

        output = out;
        writer.setOutput(out);
        writer.prepareWriteSequence(null);
    }

    /**
     * Writes an image frame to the animation sequence.
     *
     * @param img the image to write
     * @throws IOException if an I/O error occurs
     */
    public void writeToSequence(BufferedImage img) throws IOException {
        writer.writeToSequence(new IIOImage(img, null, metadata), params);
    }

    @Override
    public void close() throws IOException {
        writer.endWriteSequence();
        output.close();
    }

    private static ImageWriter newWriter() throws IIOException {
        var iter = ImageIO.getImageWritersBySuffix("gif");
        if (!iter.hasNext()) {
            throw new IIOException("No GIF Image Writers Exist");
        }
        return iter.next();
    }

    private static IIOMetadataNode getNode(IIOMetadataNode rootNode, String nodeName) {
        var nNodes = rootNode.getLength();
        for (int i = 0; i < nNodes; i++) {
            if (rootNode.item(i).getNodeName().equalsIgnoreCase(nodeName)) {
                return (IIOMetadataNode) rootNode.item(i);
            }
        }
        var node = new IIOMetadataNode(nodeName);
        rootNode.appendChild(node);
        return node;
    }
}
