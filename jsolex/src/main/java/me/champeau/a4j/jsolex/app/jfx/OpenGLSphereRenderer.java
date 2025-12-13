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

import me.champeau.a4j.jsolex.processing.spectrum.SphericalTomographyData;
import me.champeau.a4j.jsolex.processing.util.FileBackedImage;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL12;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;

/**
 * OpenGL renderer for spherical tomography shells with proper transparency.
 */
public class OpenGLSphereRenderer implements SphereRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenGLSphereRenderer.class);

    private static final int SPHERE_DIVISIONS = 256;
    private static final float BASE_RADIUS = 0.8f;

    private final SphericalTomographyData data;
    private final List<ShellRenderData> shellRenderData = new ArrayList<>();
    private final Set<Double> hiddenShells = new HashSet<>();

    private float cameraDistance = 3.0f;
    private float rotationX = 0;
    private float rotationY = 0;  // Start facing the front hemisphere (Z+)
    private float radialExaggeration = 0.2f;
    private SphereRenderer.ColorMap colorMap = SphereRenderer.ColorMap.MONO;
    private volatile boolean needsTextureReload = false;
    private boolean showProminences = false;
    private volatile boolean contrastEnhanced = false;

    private boolean texturesLoaded = false;
    private boolean dataPreprocessed = false;
    private List<ImageWrapper> unwrappedImages;
    private List<float[][]> imageDataList;
    private List<float[][]> enhancedImageDataList;
    private double minRadius;
    private double radiusRange;
    private int prominenceTextureId = -1;
    private int maxTextureSize = -1;
    private int textureWidth;
    private int textureHeight;

    /**
     * Creates a new OpenGL sphere renderer for the given tomography data.
     *
     * @param data the spherical tomography data to render
     */
    public OpenGLSphereRenderer(SphericalTomographyData data) {
        this.data = data;
    }

    /**
     * Checks if textures need to be reloaded.
     *
     * @return true if textures need to be reloaded, false otherwise
     */
    public boolean needsTextureReload() {
        return needsTextureReload;
    }

    /**
     * Reloads all textures if needed.
     */
    public void reloadTextures() {
        if (!needsTextureReload || !dataPreprocessed) {
            return;
        }

        for (var shell : shellRenderData) {
            glDeleteTextures(shell.textureId);
        }
        if (prominenceTextureId != -1) {
            glDeleteTextures(prominenceTextureId);
            prominenceTextureId = -1;
        }
        shellRenderData.clear();

        createTextures();
        needsTextureReload = false;
    }

    /**
     * Loads all textures from the tomography data into OpenGL.
     */
    public void loadTextures() {
        if (texturesLoaded) {
            return;
        }

        maxTextureSize = glGetInteger(GL_MAX_TEXTURE_SIZE);
        LOGGER.debug("GPU max texture size: {}", maxTextureSize);

        preprocessData();
        createTextures();

        texturesLoaded = true;
    }

    private void preprocessData() {
        if (dataPreprocessed) {
            return;
        }

        var shellCount = data.shellCount();

        unwrappedImages = new ArrayList<>();
        imageDataList = new ArrayList<>();

        for (var i = 0; i < shellCount; i++) {
            var shellData = data.shells().get(i);
            var imageWrapper = unwrapImage(shellData.image());
            unwrappedImages.add(imageWrapper);

            var imgData = getImageData(imageWrapper);
            imageDataList.add(imgData);
        }

        var originalHeight = imageDataList.getFirst().length;
        var originalWidth = imageDataList.getFirst()[0].length;
        textureWidth = originalWidth;
        textureHeight = originalHeight;

        if (maxTextureSize > 0 && (originalWidth > maxTextureSize || originalHeight > maxTextureSize)) {
            var scale = Math.min((float) maxTextureSize / originalWidth, (float) maxTextureSize / originalHeight);
            textureWidth = (int) (originalWidth * scale);
            textureHeight = (int) (originalHeight * scale);
            LOGGER.debug("Downscaling textures from {}x{} to {}x{} (GPU limit: {})",
                    originalWidth, originalHeight, textureWidth, textureHeight, maxTextureSize);

            var downsampledList = new ArrayList<float[][]>();
            for (var imgData : imageDataList) {
                downsampledList.add(downsampleImage(imgData, textureWidth, textureHeight));
            }
            imageDataList = downsampledList;
        }

        prepareEnhancedImages();

        var rMin = Double.MAX_VALUE;
        var rMax = Double.MIN_VALUE;
        for (var i = 0; i < shellCount; i++) {
            var radius = data.shells().get(i).normalizedRadius();
            if (radius < rMin) {
                rMin = radius;
            }
            if (radius > rMax) {
                rMax = radius;
            }
        }
        minRadius = rMin;
        radiusRange = rMax - rMin;
        if (radiusRange < 0.0001) {
            radiusRange = 1.0;
        }

        dataPreprocessed = true;
    }

    private float[][] downsampleImage(float[][] source, int targetWidth, int targetHeight) {
        var sourceHeight = source.length;
        var sourceWidth = source[0].length;
        var result = new float[targetHeight][targetWidth];

        var scaleX = (float) sourceWidth / targetWidth;
        var scaleY = (float) sourceHeight / targetHeight;

        for (var y = 0; y < targetHeight; y++) {
            for (var x = 0; x < targetWidth; x++) {
                var srcX = x * scaleX;
                var srcY = y * scaleY;

                var x0 = (int) srcX;
                var y0 = (int) srcY;
                var x1 = Math.min(x0 + 1, sourceWidth - 1);
                var y1 = Math.min(y0 + 1, sourceHeight - 1);

                var fx = srcX - x0;
                var fy = srcY - y0;

                var v00 = source[y0][x0];
                var v10 = source[y0][x1];
                var v01 = source[y1][x0];
                var v11 = source[y1][x1];

                result[y][x] = (1 - fx) * (1 - fy) * v00 +
                               fx * (1 - fy) * v10 +
                               (1 - fx) * fy * v01 +
                               fx * fy * v11;
            }
        }

        return result;
    }

    private void prepareEnhancedImages() {
        if (!data.hasEnhancedShells()) {
            LOGGER.debug("No enhanced shells available");
            enhancedImageDataList = null;
            return;
        }

        enhancedImageDataList = new ArrayList<>();

        var enhancedShells = data.enhancedShells();
        LOGGER.debug("Preparing {} enhanced images", enhancedShells.size());
        for (var i = 0; i < enhancedShells.size(); i++) {
            var shellData = enhancedShells.get(i);
            var imageWrapper = unwrapImage(shellData.image());
            var imgData = getImageData(imageWrapper);

            // Apply same downsampling as raw images if needed
            if (textureWidth != imgData[0].length || textureHeight != imgData.length) {
                imgData = downsampleImage(imgData, textureWidth, textureHeight);
            }

            enhancedImageDataList.add(imgData);
        }
        LOGGER.debug("Enhanced image data list size: {}", enhancedImageDataList.size());
    }

    private List<float[][]> getCurrentImageDataList() {
        if (contrastEnhanced && enhancedImageDataList != null) {
            LOGGER.debug("Using enhanced image data list");
            return enhancedImageDataList;
        }
        LOGGER.debug("Using raw image data list (contrastEnhanced={}, enhancedImageDataList null? {})",
                contrastEnhanced, enhancedImageDataList == null);
        return imageDataList;
    }

    private void createTextures() {
        var shellCount = data.shellCount();
        var currentDataList = getCurrentImageDataList();
        var height = currentDataList.getFirst().length;
        var width = currentDataList.getFirst()[0].length;

        var layerMins = new float[shellCount];
        var layerMaxs = new float[shellCount];

        for (var i = 0; i < shellCount; i++) {
            var imgData = currentDataList.get(i);
            var min = Float.MAX_VALUE;
            var max = Float.MIN_VALUE;

            for (var y = 0; y < height; y++) {
                for (var x = 0; x < width; x++) {
                    var val = imgData[y][x];
                    if (val < min) {
                        min = val;
                    }
                    if (val > max) {
                        max = val;
                    }
                }
            }

            layerMins[i] = min;
            layerMaxs[i] = max;
        }

        int outermostIndex = 0;
        double maxRadius = Double.MIN_VALUE;
        for (var i = 0; i < shellCount; i++) {
            var radius = data.shells().get(i).normalizedRadius();
            if (radius > maxRadius) {
                maxRadius = radius;
                outermostIndex = i;
            }
        }

        for (var i = 0; i < shellCount; i++) {
            var imageWrapper = unwrappedImages.get(i);
            var imgData = currentDataList.get(i);
            var radius = data.shells().get(i).normalizedRadius();
            var isBase = Math.abs(radius - minRadius) < 0.0001;

            var colorPosition = (float) ((radius - minRadius) / radiusRange);

            var ellipse = imageWrapper.findMetadata(Ellipse.class).orElse(null);
            var textureResult = createShellTexture(imgData, isBase, colorPosition,
                    layerMins[i], layerMaxs[i] - layerMins[i], ellipse);

            if (i == outermostIndex) {
                prominenceTextureId = createProminenceTexture(imgData, layerMins[i], layerMaxs[i] - layerMins[i]);
            }

            shellRenderData.add(new ShellRenderData(
                    textureResult.textureId(),
                    radius,
                    data.shells().get(i).pixelShift(),
                    textureWidth,
                    textureHeight,
                    ellipse,
                    colorPosition,
                    textureResult.averageColor()
            ));
        }
        shellRenderData.sort(Comparator.comparingDouble(ShellRenderData::normalizedRadius));
    }

    private ImageWrapper unwrapImage(ImageWrapper image) {
        if (image instanceof FileBackedImage fileBackedImage) {
            return unwrapImage(fileBackedImage.unwrapToMemory());
        }
        return image;
    }

    /**
     * Result of texture creation containing the texture ID and average color.
     *
     * @param textureId the OpenGL texture ID
     * @param averageColor the average RGB color within the disk
     */
    private record TextureResult(int textureId, float[] averageColor) {}

    private TextureResult createShellTexture(float[][] imageData, boolean isBase, float colorPosition,
                                             float layerMin, float layerRange, Ellipse ellipse) {
        var textureId = glGenTextures();
        var error = glGetError();
        if (error != GL_NO_ERROR) {
            LOGGER.error("OpenGL error after glGenTextures: {}", error);
        }

        glBindTexture(GL_TEXTURE_2D, textureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        var height = imageData.length;
        var width = imageData[0].length;

        // Compute disk center and radius for average calculation
        float diskCenterX = width / 2.0f;
        float diskCenterY = height / 2.0f;
        float diskRadiusX = width / 2.0f;
        float diskRadiusY = height / 2.0f;
        if (ellipse != null) {
            diskCenterX = (float) ellipse.center().a();
            diskCenterY = (float) ellipse.center().b();
            diskRadiusX = (float) ellipse.semiAxis().a();
            diskRadiusY = (float) ellipse.semiAxis().b();
        }

        // Accumulators for average color within the disk
        double avgR = 0, avgG = 0, avgB = 0;
        int diskPixelCount = 0;

        var buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var rawValue = imageData[y][x];

                float r, g, b;
                int alpha;

                if (colorMap == SphereRenderer.ColorMap.MONO) {
                    // MONO mode: use PER-LAYER normalization (as in original)
                    var normalizedValue = layerRange > 0.001f ? (rawValue - layerMin) / layerRange : 0.5f;
                    normalizedValue = Math.min(1.0f, Math.max(0.0f, normalizedValue));

                    r = g = b = normalizedValue;
                    if (isBase) {
                        alpha = 255;
                    } else {
                        // Dark = opaque, bright = transparent
                        alpha = 255 - (int) (normalizedValue * 255);
                    }
                } else {
                    // Colorized mode: apply a colormap to each layer
                    // Each pixel gets a color based on its intensity within the layer
                    var normalizedValue = layerRange > 0.001f ? (rawValue - layerMin) / layerRange : 0.5f;
                    normalizedValue = Math.min(1.0f, Math.max(0.0f, normalizedValue));

                    // Map intensity to color using full saturation
                    var pixelColor = computeColorForIntensity(normalizedValue, colorPosition, colorMap);
                    r = pixelColor[0];
                    g = pixelColor[1];
                    b = pixelColor[2];

                    // Alpha: use a threshold approach
                    // Only show layer where it has dark features, otherwise transparent
                    if (isBase) {
                        alpha = 255;
                    } else {
                        // Threshold varies by layer - middle layers (green/yellow) get higher threshold
                        // to be more visible. colorPosition 0.5 = middle layer
                        var middleness = 1.0f - 2.0f * Math.abs(colorPosition - 0.5f); // 0 at edges, 1 at middle
                        var threshold = 0.5f + middleness * 0.2f; // 0.5 at edges, 0.7 at middle

                        if (normalizedValue < threshold) {
                            // Dark feature - make opaque, scaled by how dark
                            var darkness = (threshold - normalizedValue) / threshold;
                            alpha = (int) (darkness * 255);
                        } else {
                            // Bright area - transparent
                            alpha = 0;
                        }
                    }
                }

                // Check if this pixel is inside the disk for average calculation
                var dx = (x - diskCenterX) / diskRadiusX;
                var dy = (y - diskCenterY) / diskRadiusY;
                if (dx * dx + dy * dy <= 1.0f) {
                    avgR += r;
                    avgG += g;
                    avgB += b;
                    diskPixelCount++;
                }

                buffer.put((byte) (r * 255));
                buffer.put((byte) (g * 255));
                buffer.put((byte) (b * 255));
                buffer.put((byte) alpha);
            }
        }
        buffer.flip();

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        error = glGetError();
        if (error != GL_NO_ERROR) {
            LOGGER.error("OpenGL error after glTexImage2D: {}", error);
        }

        // Compute final average color
        float[] averageColor;
        if (diskPixelCount > 0) {
            averageColor = new float[] {
                    (float) (avgR / diskPixelCount),
                    (float) (avgG / diskPixelCount),
                    (float) (avgB / diskPixelCount)
            };
        } else {
            averageColor = new float[] {0.5f, 0.5f, 0.5f};
        }

        return new TextureResult(textureId, averageColor);
    }

    private int createProminenceTexture(float[][] imageData, float layerMin, float layerRange) {
        var textureId = glGenTextures();
        var error = glGetError();
        if (error != GL_NO_ERROR) {
            LOGGER.error("OpenGL error after glGenTextures for prominence: {}", error);
        }

        glBindTexture(GL_TEXTURE_2D, textureId);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        var height = imageData.length;
        var width = imageData[0].length;

        var buffer = BufferUtils.createByteBuffer(width * height * 4);
        for (float[] line : imageData) {
            for (var x = 0; x < width; x++) {
                var rawValue = line[x];

                // Always grayscale for prominences
                var normalizedValue = layerRange > 0.001f ? (rawValue - layerMin) / layerRange : 0.5f;
                normalizedValue = Math.min(1.0f, Math.max(0.0f, normalizedValue));

                var gray = (int) (normalizedValue * 255);
                buffer.put((byte) gray);
                buffer.put((byte) gray);
                buffer.put((byte) gray);
                buffer.put((byte) 255); // Full opacity
            }
        }
        buffer.flip();

        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);

        error = glGetError();
        if (error != GL_NO_ERROR) {
            LOGGER.error("OpenGL error after glTexImage2D for prominence: {}", error);
        }

        return textureId;
    }

    private float[] computeColorForIntensity(float intensity, float layerPosition, SphereRenderer.ColorMap cmap) {
        // Create a colorized image for this layer
        // intensity: 0 = dark (features), 1 = bright (quiet sun)
        // layerPosition: 0 = innermost (wing), 1 = outermost (core)

        if (cmap == SphereRenderer.ColorMap.BLUE_TO_RED) {
            layerPosition = 1.0f - layerPosition;
        }

        // Base hue from layer position (red=0 to blue=240 degrees)
        var baseHue = layerPosition * 240.0f / 360.0f;

        // High saturation throughout for vivid colors
        var saturation = 0.9f;

        // Lightness: 0.3 (dark features) to 0.7 (bright areas)
        // Keeping it in mid-range preserves color saturation
        var lightness = 0.3f + intensity * 0.4f;

        // Convert HSL to RGB
        return hslToRgb(baseHue, saturation, lightness);
    }

    private float[] hslToRgb(float h, float s, float l) {
        if (s == 0.0f) {
            return new float[]{l, l, l};
        }

        var q = l < 0.5f ? l * (1.0f + s) : l + s - l * s;
        var p = 2.0f * l - q;

        return new float[]{
                hueToRgb(p, q, h + 1.0f / 3.0f),
                hueToRgb(p, q, h),
                hueToRgb(p, q, h - 1.0f / 3.0f)
        };
    }

    private float hueToRgb(float p, float q, float t) {
        if (t < 0.0f) {
            t += 1.0f;
        }
        if (t > 1.0f) {
            t -= 1.0f;
        }
        if (t < 1.0f / 6.0f) {
            return p + (q - p) * 6.0f * t;
        }
        if (t < 1.0f / 2.0f) {
            return q;
        }
        if (t < 2.0f / 3.0f) {
            return p + (q - p) * (2.0f / 3.0f - t) * 6.0f;
        }
        return p;
    }

    private float[][] getImageData(ImageWrapper image) {
        if (image instanceof ImageWrapper32 iw32) {
            return iw32.data();
        }
        throw new IllegalArgumentException("Unsupported image type: " + image.getClass());
    }

    /**
     * Renders the spherical tomography visualization.
     *
     * @param viewWidth the width of the viewport
     * @param viewHeight the height of the viewport
     */
    public void render(int viewWidth, int viewHeight) {
        if (!texturesLoaded) {
            LOGGER.warn("render() called but textures not loaded");
            return;
        }

        // Clear to black background
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        setupProjection(viewWidth, viewHeight);
        setupModelView();

        // Enable depth test for proper occlusion
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        // Filter to only visible shells
        var visibleShells = shellRenderData.stream()
                .filter(this::isShellVisible)
                .toList();

        // Enable texturing
        glEnable(GL_TEXTURE_2D);
        glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);

        // Shells are sorted by radius (innermost first)
        // Render innermost first (opaque base), then outer shells on top with alpha blending
        if (!visibleShells.isEmpty()) {
            glDisable(GL_BLEND);
            glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            renderShell(visibleShells.getFirst());
        }

        if (visibleShells.size() > 1) {
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glDepthMask(false);

            for (var i = 1; i < visibleShells.size(); i++) {
                glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
                renderShell(visibleShells.get(i));
            }

            glDepthMask(true);
        }

        // Render prominence band using MAX blending (if enabled)
        // MAX blending ensures prominences only show where they're brighter than the shells
        // This prevents the ring artifact from showing through the disk
        if (showProminences && !visibleShells.isEmpty()) {
            var outermostShell = visibleShells.getLast();
            var outermostRadius = getShellRadius(outermostShell);
            glEnable(GL_BLEND);
            // MAX blending: result = max(src, dst) for each color component
            glBlendEquation(GL_MAX);
            renderProminenceBand(outermostRadius, outermostShell);
            // Reset to normal blending equation
            glBlendEquation(GL_FUNC_ADD);
            glDisable(GL_BLEND);
        }

        glDisable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
    }

    private void setupProjection(int width, int height) {
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        var aspect = (float) width / height;
        var fov = 45.0f;
        var near = 0.1f;
        var far = 100.0f;

        var top = (float) (near * Math.tan(Math.toRadians(fov / 2)));
        var bottom = -top;
        var right = top * aspect;
        var left = -right;

        glFrustum(left, right, bottom, top, near, far);
    }

    private void setupModelView() {
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        // Camera translation
        glTranslatef(0, 0, -cameraDistance);

        // Apply rotations
        glRotatef(rotationX, 1, 0, 0);
        glRotatef(rotationY, 0, 1, 0);
    }

    private float getShellRadius(ShellRenderData shell) {
        return BASE_RADIUS * (1.0f + ((float) shell.normalizedRadius - 1.0f) * radialExaggeration);
    }

    private void renderShell(ShellRenderData shell) {
        var radius = getShellRadius(shell);

        glBindTexture(GL_TEXTURE_2D, shell.textureId);

        // White color - grayscale texture only, no tinting with multiply blend
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        // Render textured front hemisphere
        renderHemisphere(radius, shell);
    }

    private void renderHemisphere(float radius, ShellRenderData shell) {
        // Calculate UV mapping based on ellipse if available
        var diskCenterU = 0.5f;
        var diskCenterV = 0.5f;
        var diskRadiusU = 0.5f;
        var diskRadiusV = 0.5f;

        if (shell.ellipse != null) {
            var center = shell.ellipse.center();
            var semiAxis = shell.ellipse.semiAxis();
            diskCenterU = (float) (center.a() / shell.imageWidth);
            diskCenterV = (float) (center.b() / shell.imageHeight);
            diskRadiusU = (float) (semiAxis.a() / shell.imageWidth);
            diskRadiusV = (float) (semiAxis.b() / shell.imageHeight);
        }

        var divisions = SPHERE_DIVISIONS;

        glBegin(GL_TRIANGLES);

        for (var i = 0; i < divisions; i++) {
            for (var j = 0; j < divisions; j++) {
                var nx1 = -1.0f + 2.0f * i / divisions;
                var ny1 = -1.0f + 2.0f * j / divisions;
                var nx2 = -1.0f + 2.0f * (i + 1) / divisions;
                var ny2 = -1.0f + 2.0f * (j + 1) / divisions;

                var r1sq = nx1 * nx1 + ny1 * ny1;
                var r2sq = nx2 * nx2 + ny1 * ny1;
                var r3sq = nx2 * nx2 + ny2 * ny2;
                var r4sq = nx1 * nx1 + ny2 * ny2;

                // Skip cells entirely outside the unit disk
                if (r1sq > 1.0f && r2sq > 1.0f && r3sq > 1.0f && r4sq > 1.0f) {
                    continue;
                }

                // Clamp vertices to sphere surface if outside
                float z1, z2, z3, z4;
                float px1 = nx1, py1 = ny1, px2 = nx2, py2 = ny1, px3 = nx2, py3 = ny2, px4 = nx1, py4 = ny2;

                if (r1sq >= 1.0f) {
                    var scale = 1.0f / (float) Math.sqrt(r1sq);
                    px1 = nx1 * scale;
                    py1 = ny1 * scale;
                    z1 = 0;
                } else {
                    z1 = (float) Math.sqrt(1.0f - r1sq);
                }

                if (r2sq >= 1.0f) {
                    var scale = 1.0f / (float) Math.sqrt(r2sq);
                    px2 = nx2 * scale;
                    py2 = ny1 * scale;
                    z2 = 0;
                } else {
                    z2 = (float) Math.sqrt(1.0f - r2sq);
                }

                if (r3sq >= 1.0f) {
                    var scale = 1.0f / (float) Math.sqrt(r3sq);
                    px3 = nx2 * scale;
                    py3 = ny2 * scale;
                    z3 = 0;
                } else {
                    z3 = (float) Math.sqrt(1.0f - r3sq);
                }

                if (r4sq >= 1.0f) {
                    var scale = 1.0f / (float) Math.sqrt(r4sq);
                    px4 = nx1 * scale;
                    py4 = ny2 * scale;
                    z4 = 0;
                } else {
                    z4 = (float) Math.sqrt(1.0f - r4sq);
                }

                var u1 = diskCenterU + nx1 * diskRadiusU;
                var v1 = diskCenterV - ny1 * diskRadiusV;
                var u2 = diskCenterU + nx2 * diskRadiusU;
                var v2 = diskCenterV - ny1 * diskRadiusV;
                var u3 = diskCenterU + nx2 * diskRadiusU;
                var v3 = diskCenterV - ny2 * diskRadiusV;
                var u4 = diskCenterU + nx1 * diskRadiusU;
                var v4 = diskCenterV - ny2 * diskRadiusV;

                glTexCoord2f(u1, v1);
                glVertex3f(px1 * radius, py1 * radius, z1 * radius);
                glTexCoord2f(u2, v2);
                glVertex3f(px2 * radius, py2 * radius, z2 * radius);
                glTexCoord2f(u3, v3);
                glVertex3f(px3 * radius, py3 * radius, z3 * radius);

                glTexCoord2f(u1, v1);
                glVertex3f(px1 * radius, py1 * radius, z1 * radius);
                glTexCoord2f(u3, v3);
                glVertex3f(px3 * radius, py3 * radius, z3 * radius);
                glTexCoord2f(u4, v4);
                glVertex3f(px4 * radius, py4 * radius, z4 * radius);
            }
        }

        glEnd();
    }

    private void renderProminenceBand(float radius, ShellRenderData shell) {
        if (shell.ellipse == null || prominenceTextureId == -1) {
            return;
        }

        glBindTexture(GL_TEXTURE_2D, prominenceTextureId);

        var center = shell.ellipse.center();
        var semiAxis = shell.ellipse.semiAxis();
        var diskCenterU = (float) (center.a() / shell.imageWidth);
        var diskCenterV = (float) (center.b() / shell.imageHeight);
        var diskRadiusU = (float) (semiAxis.a() / shell.imageWidth);
        var diskRadiusV = (float) (semiAxis.b() / shell.imageHeight);

        // Render prominences as a flat ring at the limb plane (z slightly positive)
        // extending radially outward from the outermost shell
        var angularDivisions = SPHERE_DIVISIONS * 2;
        var radialSteps = 8;

        var maxImageExtent = 1.25f;

        // The prominence ring sits just in front of the limb plane
        // Use a z value that's always in front of all shells
        var zOffset = 0.01f * radius;

        glBegin(GL_TRIANGLES);

        for (var i = 0; i < angularDivisions; i++) {
            var angle1 = (float) (2.0 * Math.PI * i / angularDivisions);
            var angle2 = (float) (2.0 * Math.PI * (i + 1) / angularDivisions);

            var cosA1 = (float) Math.cos(angle1);
            var sinA1 = (float) Math.sin(angle1);
            var cosA2 = (float) Math.cos(angle2);
            var sinA2 = (float) Math.sin(angle2);

            for (var j = 0; j < radialSteps; j++) {
                var t1 = (float) j / radialSteps;
                var t2 = (float) (j + 1) / radialSteps;

                // Image sampling goes from r=1.0 (disk edge) to r=1.25 (outside)
                var imgR1 = 1.0f + t1 * (maxImageExtent - 1.0f);
                var imgR2 = 1.0f + t2 * (maxImageExtent - 1.0f);

                // UV coordinates - sample outside the disk
                var u1 = diskCenterU + cosA1 * imgR1 * diskRadiusU;
                var v1 = diskCenterV - sinA1 * imgR1 * diskRadiusV;
                var u2 = diskCenterU + cosA2 * imgR1 * diskRadiusU;
                var v2 = diskCenterV - sinA2 * imgR1 * diskRadiusV;
                var u3 = diskCenterU + cosA2 * imgR2 * diskRadiusU;
                var v3 = diskCenterV - sinA2 * imgR2 * diskRadiusV;
                var u4 = diskCenterU + cosA1 * imgR2 * diskRadiusU;
                var v4 = diskCenterV - sinA1 * imgR2 * diskRadiusV;

                if (u1 < 0 || u1 > 1 || v1 < 0 || v1 > 1 ||
                    u2 < 0 || u2 > 1 || v2 < 0 || v2 > 1 ||
                    u3 < 0 || u3 > 1 || v3 < 0 || v3 > 1 ||
                    u4 < 0 || u4 > 1 || v4 < 0 || v4 > 1) {
                    continue;
                }

                // 3D positions: flat ring extending from radius outward
                // The ring starts at the outermost shell's limb (x²+y² = radius²)
                // and extends outward to radius * maxImageExtent
                var r1 = radius * imgR1;
                var r2 = radius * imgR2;

                float x1 = cosA1 * r1, y1 = sinA1 * r1;
                float x2 = cosA2 * r1, y2 = sinA2 * r1;
                float x3 = cosA2 * r2, y3 = sinA2 * r2;
                float x4 = cosA1 * r2, y4 = sinA1 * r2;

                // Render at constant z (flat ring in front of limb)
                glTexCoord2f(u1, v1);
                glVertex3f(x1, y1, zOffset);
                glTexCoord2f(u2, v2);
                glVertex3f(x2, y2, zOffset);
                glTexCoord2f(u3, v3);
                glVertex3f(x3, y3, zOffset);

                glTexCoord2f(u1, v1);
                glVertex3f(x1, y1, zOffset);
                glTexCoord2f(u3, v3);
                glVertex3f(x3, y3, zOffset);
                glTexCoord2f(u4, v4);
                glVertex3f(x4, y4, zOffset);
            }
        }

        glEnd();

        // Restore the original shell texture
        glBindTexture(GL_TEXTURE_2D, shell.textureId);
    }

    /**
     * Sets the camera distance from the sphere.
     *
     * @param distance the distance, clamped between 1.5 and 10.0
     */
    public void setCameraDistance(float distance) {
        this.cameraDistance = Math.max(1.5f, Math.min(10.0f, distance));
    }

    /**
     * Gets the current camera distance.
     *
     * @return the camera distance
     */
    public float getCameraDistance() {
        return cameraDistance;
    }

    /**
     * Sets the rotation angles for the sphere.
     *
     * @param x rotation around the X axis in degrees
     * @param y rotation around the Y axis in degrees
     */
    public void setRotation(float x, float y) {
        this.rotationX = x;
        this.rotationY = y;
    }

    /**
     * Gets the rotation around the X axis.
     *
     * @return the X rotation in degrees
     */
    public float getRotationX() {
        return rotationX;
    }

    /**
     * Gets the rotation around the Y axis.
     *
     * @return the Y rotation in degrees
     */
    public float getRotationY() {
        return rotationY;
    }

    /**
     * Sets the radial exaggeration factor to emphasize shell separation.
     *
     * @param exaggeration the exaggeration factor
     */
    public void setRadialExaggeration(float exaggeration) {
        this.radialExaggeration = exaggeration;
    }

    /**
     * Gets the current radial exaggeration factor.
     *
     * @return the radial exaggeration factor
     */
    public float getRadialExaggeration() {
        return radialExaggeration;
    }

    /**
     * Sets the color map for rendering shells.
     *
     * @param colorMap the color map to use
     */
    public void setColorMap(SphereRenderer.ColorMap colorMap) {
        if (this.colorMap != colorMap) {
            this.colorMap = colorMap;
            this.needsTextureReload = true;
        }
    }

    /**
     * Gets the current color map.
     *
     * @return the current color map
     */
    public SphereRenderer.ColorMap getColorMap() {
        return colorMap;
    }

    /**
     * Sets whether to show prominences at the solar limb.
     *
     * @param show true to show prominences, false to hide them
     */
    public void setShowProminences(boolean show) {
        this.showProminences = show;
    }

    /**
     * Checks if prominences are being shown.
     *
     * @return true if prominences are shown, false otherwise
     */
    public boolean isShowProminences() {
        return showProminences;
    }

    /**
     * Sets whether to use contrast-enhanced images.
     *
     * @param enhanced true to use enhanced images, false to use raw images
     */
    public void setContrastEnhanced(boolean enhanced) {
        LOGGER.debug("setContrastEnhanced: {} -> {}, enhancedImageDataList null? {}",
                this.contrastEnhanced, enhanced, enhancedImageDataList == null);
        if (this.contrastEnhanced != enhanced) {
            this.contrastEnhanced = enhanced;
            this.needsTextureReload = true;
        }
    }

    @Override
    public boolean isContrastEnhanced() {
        return contrastEnhanced;
    }

    @Override
    public boolean hasContrastEnhancement() {
        return data.hasEnhancedShells();
    }

    @Override
    public void setShellVisible(double pixelShift, boolean visible) {
        if (visible) {
            hiddenShells.remove(pixelShift);
        } else {
            hiddenShells.add(pixelShift);
        }
    }

    @Override
    public boolean isShellVisible(double pixelShift) {
        return !hiddenShells.contains(pixelShift);
    }

    private boolean isShellVisible(ShellRenderData shell) {
        return !hiddenShells.contains(shell.pixelShift);
    }

    /**
     * Disposes all OpenGL resources used by this renderer.
     */
    @Override
    public void dispose() {
        for (var shell : shellRenderData) {
            glDeleteTextures(shell.textureId);
        }
        if (prominenceTextureId != -1) {
            glDeleteTextures(prominenceTextureId);
            prominenceTextureId = -1;
        }
        shellRenderData.clear();
        texturesLoaded = false;
    }

    /**
     * Data associated with a single shell for rendering.
     *
     * @param textureId the OpenGL texture ID
     * @param normalizedRadius the normalized radius of this shell
     * @param pixelShift the pixel shift value for this shell
     * @param imageWidth the width of the texture image
     * @param imageHeight the height of the texture image
     * @param ellipse the ellipse fit for the solar disk, if available
     * @param colorPosition the position in the color map (0 to 1)
     * @param averageColor the average RGB color within the disk
     */
    private record ShellRenderData(
            int textureId,
            double normalizedRadius,
            double pixelShift,
            int imageWidth,
            int imageHeight,
            Ellipse ellipse,
            float colorPosition,
            float[] averageColor
    ) {}
}
