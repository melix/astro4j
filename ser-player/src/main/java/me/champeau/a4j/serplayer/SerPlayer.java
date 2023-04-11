/*
 * Copyright 2023 the original author or authors.
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
package me.champeau.a4j.serplayer;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import me.champeau.a4j.ser.ColorMode;
import me.champeau.a4j.ser.Frame;
import me.champeau.a4j.ser.Header;
import me.champeau.a4j.ser.ImageGeometry;
import me.champeau.a4j.ser.SerFileReader;
import me.champeau.a4j.ser.bayer.BayerMatrixSupport;
import me.champeau.a4j.ser.bayer.BilinearDemosaicingStrategy;
import me.champeau.a4j.ser.bayer.DemosaicingStrategy;
import me.champeau.a4j.serplayer.config.Configuration;
import me.champeau.a4j.serplayer.controls.FrameMetadataControl;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

public class SerPlayer extends Application implements BayerMatrixSupport {

    private static final int INITIAL_WIDTH = 800;
    private static final int INITIAL_MAX_IMAGE_HEIGHT = 400;
    private static final int INITIAL_HEIGHT = 600;
    private static final String DEBAYER_AUTO = "AUTO";
    private static final String DEBAYER_OFF = "OFF";
    private static final String BAYER_FORCE_PREFIX = "FORCE_";
    private static final String DEBAYER_FORCE_RGGB = "FORCE_RGGB";
    private static final String DEBAYER_FORCE_BGGR = "FORCE_BGGR";
    private static final String DEBAYER_FORCE_GBRG = "FORCE_GBRG";
    private static final String DEBAYER_FORCE_GRBG = "FORCE_GRBG";
    private static final String BAYER_PREFIX = "BAYER_";
    // Default FPS to display in case we can't compute from the ser file
    private static final double DEFAULT_FPS = 25d;

    private final Configuration config = new Configuration();
    private ImageView imageView;
    private ToggleGroup debayerToggleGroup;
    private VideoAnimationTimer videoAnimationTimer;
    private FrameMetadataControl fileMetadataControl;
    private volatile File currentSelectedFile;

    @Override
    public void start(Stage stage) {
        var pane = new BorderPane();
        fileMetadataControl = new FrameMetadataControl();
        imageView = createImageView(stage);
        pane.setCenter(imageView);
        pane.setRight(fileMetadataControl);
        var menuBar = createMenuBar();
        var vbox = new VBox();
        var scene = new Scene(vbox, INITIAL_WIDTH, INITIAL_HEIGHT);
        vbox.getChildren().addAll(menuBar, pane);
        stage.setScene(scene);
        stage.setTitle("SER Player");
        stage.onCloseRequestProperty().set(e -> closeApp());
        stage.show();
    }

    private ImageView createImageView(Stage stage) {
        var view = new ImageView();
        view.setPreserveRatio(true);
        view.fitWidthProperty().bind(
                stage.widthProperty().add(fileMetadataControl.widthProperty().negate())
        );
        view.setOnScroll(event -> {
            double deltaY = event.getDeltaY();
            if (deltaY != 0) {
                double zoomFactor = 0.95;
                double scaleX = view.getScaleX();
                double scaleY = view.getScaleY();
                if (deltaY < 0) {
                    view.setScaleX(scaleX * zoomFactor);
                    view.setScaleY(scaleY * zoomFactor);
                } else {
                    view.setScaleX(scaleX / zoomFactor);
                    view.setScaleY(scaleY / zoomFactor);
                }
            }
            event.consume();

        });
        return view;
    }

    private MenuBar createMenuBar() {
        var menuBar = new MenuBar();
        var fileMenu = createFileMenu();
        var optionsMenu = createOptionsMenu();
        var helpMenu = createHelpMenu();
        menuBar.getMenus().addAll(fileMenu, optionsMenu, helpMenu);
        return menuBar;
    }

    private static Menu createHelpMenu() {
        return new Menu("Help");
    }

    private Menu createFileMenu() {
        var fileMenu = new Menu("File");
        var openItem = new MenuItem("Open");
        var recentMenu = new Menu("Reopen recent");
        openItem.setOnAction(e -> {
            var fileChooser = new FileChooser();
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SER files", "*.ser"));
            var selectedFile = fileChooser.showOpenDialog(null);
            if (selectedFile != null && !selectedFile.equals(currentSelectedFile)) {
                loadSerFile(selectedFile);
                refreshRecentItemsMenu(recentMenu);
            }
        });
        refreshRecentItemsMenu(recentMenu);
        var exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> closeApp());
        fileMenu.getItems().addAll(openItem, recentMenu, exitItem);
        return fileMenu;
    }

    private void refreshRecentItemsMenu(Menu recentMenu) {
        recentMenu.getItems().clear();
        for (Path recentFile : config.getRecentFiles()) {
            var recent = new MenuItem(recentFile.toAbsolutePath().toString());
            recent.setOnAction(e -> loadSerFile(recentFile.toFile()));
            recentMenu.getItems().add(recent);
        }
    }

    private Menu createOptionsMenu() {
        var optionsMenu = new Menu("Options");
        var demosaic = new Menu("Debayer");
        var toggleGroup = new ToggleGroup();
        var autoDebayer = new RadioMenuItem("Auto");
        autoDebayer.setUserData(DEBAYER_AUTO);
        var offDebayer = new RadioMenuItem("Off");
        offDebayer.setUserData(DEBAYER_OFF);
        var forceRggb = new RadioMenuItem("Force RGGB");
        forceRggb.setUserData(DEBAYER_FORCE_RGGB);
        var forceBggr = new RadioMenuItem("Force BGGR");
        forceBggr.setUserData(DEBAYER_FORCE_BGGR);
        var forceGbrg = new RadioMenuItem("Force GBRG");
        forceGbrg.setUserData(DEBAYER_FORCE_GBRG);
        var forceGrbg = new RadioMenuItem("Force GRBG");
        forceGrbg.setUserData(DEBAYER_FORCE_GRBG);
        autoDebayer.setToggleGroup(toggleGroup);
        forceRggb.setToggleGroup(toggleGroup);
        forceBggr.setToggleGroup(toggleGroup);
        forceGbrg.setToggleGroup(toggleGroup);
        forceGrbg.setToggleGroup(toggleGroup);
        offDebayer.setToggleGroup(toggleGroup);
        toggleGroup.selectToggle(autoDebayer);
        demosaic.getItems().addAll(autoDebayer, forceRggb, forceBggr, forceGbrg, forceGrbg, offDebayer);
        optionsMenu.getItems().add(demosaic);
        this.debayerToggleGroup = toggleGroup;
        return optionsMenu;
    }

    private synchronized void closeApp() {
        if (videoAnimationTimer != null) {
            try {
                videoAnimationTimer.stop();
            } catch (Exception e) {
                // ignore
            }
        }
        System.exit(0);
    }

    private synchronized void loadSerFile(File selectedFile) {
        if (selectedFile.equals(currentSelectedFile)) {
            return;
        }
        config.loaded(selectedFile.toPath());
        if (videoAnimationTimer != null) {
            videoAnimationTimer.stop();
        }
        currentSelectedFile = selectedFile;
        SerFileReader reader;
        try {
            reader = SerFileReader.of(selectedFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        videoAnimationTimer = new VideoAnimationTimer(reader);
        videoAnimationTimer.start();

    }

    private static byte[] createBuffer(ImageGeometry geometry) {
        int size = geometry.height() * geometry.width();
        return new byte[3 * size];
    }

    private void convertToRgb(ByteBuffer frameData, ImageGeometry geometry, byte[] outputData) {
        int width = geometry.width();
        int height = geometry.height();
        int bytesPerPixel = geometry.getBytesPerPixel();
        int k = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                byte value = readByteColorValue(frameData, geometry, bytesPerPixel);
                if (geometry.colorMode() == ColorMode.MONO || geometry.colorMode().isBayer()) {
                    outputData[k + RED] = value;
                    outputData[k + GREEN] = value;
                    outputData[k + BLUE] = value;
                } else if (geometry.colorMode() == ColorMode.BGR) {
                    byte g = readByteColorValue(frameData, geometry, bytesPerPixel);
                    byte r = readByteColorValue(frameData, geometry, bytesPerPixel);
                    outputData[k + RED] = r;
                    outputData[k + GREEN] = g;
                    outputData[k + BLUE] = value;
                } else if (geometry.colorMode() == ColorMode.RGB) {
                    byte g = readByteColorValue(frameData, geometry, bytesPerPixel);
                    byte b = readByteColorValue(frameData, geometry, bytesPerPixel);
                    outputData[k + RED] = value;
                    outputData[k + GREEN] = g;
                    outputData[k + BLUE] = b;
                }
                k += 3;
            }
        }
        String debayerMode = debayerToggleGroup.getSelectedToggle().getUserData().toString();
        boolean forceDebayer = debayerMode.startsWith(BAYER_FORCE_PREFIX);
        if (forceDebayer || geometry.colorMode().isBayer() && debayerMode.equals(DEBAYER_AUTO)) {
            ColorMode mode = forceDebayer ? ColorMode.valueOf(BAYER_PREFIX + debayerMode.substring(BAYER_FORCE_PREFIX.length())) : geometry.colorMode();
            DemosaicingStrategy strategy = new BilinearDemosaicingStrategy(outputData, mode, geometry);
            strategy.demosaic();
        }
    }

    private static byte readByteColorValue(ByteBuffer frameData, ImageGeometry geometry, int bytesPerPixel) {
        byte next;
        int bitsToDiscard = geometry.pixelDepthPerPlane() - 8;
        if (bytesPerPixel == 1) {
            // Data of between 1 and 8 bits should be stored aligned with the most significant bit
            next = (byte) (frameData.get() >> bitsToDiscard);
        } else {
            // Data between 9 and 16 bits should be stored aligned with the least significant bit
            short sixteenBit = frameData.getShort();
            next = (byte) sixteenBit;
        }
        return next;
    }

    public static void main(String[] args) {
        launch();
    }

    /**
     * Handles display of the video stream by making sure we select
     * the appropriate frame according to the computed frame rate of
     * the video. The frame rate is computed from the timestamps in
     * if they are available. If not, we fall back to a default frame
     * rate of 25 frames per second.
     */
    private class VideoAnimationTimer extends AnimationTimer {
        private final SerFileReader reader;
        private byte[] imageData;
        private WritableImage image;
        private double fps;
        private long last = -1L;
        private int currentFrameNb = 0;

        private VideoAnimationTimer(SerFileReader reader) {
            this.reader = reader;
        }

        @Override
        public void handle(long now) {
            if (last == -1L) {
                showFrame();
                last = now;
                return;
            }
            double elapsedMillis = Duration.ofNanos(now - last).toMillis();
            int framesToSkip = (int) (elapsedMillis / 1000 * fps);
            if (framesToSkip == 0) {
                return;
            }
            last = now;
            currentFrameNb = (currentFrameNb + framesToSkip) % reader.header().frameCount();
            showFrame();
        }

        private void showFrame() {
            reader.seekFrame(currentFrameNb);
            Header header = reader.header();
            ImageGeometry geometry = header.geometry();
            int width = geometry.width();
            int height = geometry.height();
            Frame frame = reader.currentFrame();
            convertToRgb(frame.data(), geometry, imageData);
            PixelFormat<ByteBuffer> pixelFormat = PixelFormat.getByteRgbInstance();
            reader.nextFrame();
            image.getPixelWriter()
                    .setPixels(0, 0, width, height, pixelFormat, imageData, 0, PIXEL * width);
            imageView.setImage(image);
            fileMetadataControl.setFilename(currentSelectedFile.getName());
            fileMetadataControl.setColorMode(header.geometry().colorMode().toString());
            fileMetadataControl.setFrame((currentFrameNb + 1) + "/" + header.frameCount());
            fileMetadataControl.setFps(String.format("%.1f fps", fps));
        }

        private void prepare() {
            Header header = reader.header();
            fps = computeFps(header).orElse(DEFAULT_FPS);
            ImageGeometry geometry = header.geometry();
            int width = geometry.width();
            int height = geometry.height();
            image = new WritableImage(
                    width,
                    height
            );
            imageData = createBuffer(geometry);
        }

        private Optional<Double> computeFps(Header header) {
            Optional<Double> value = Optional.empty();
            if (header.metadata().hasTimestamps()) {
                reader.seekLast();
                ZonedDateTime lastFrameTimestamp = reader.currentFrame().timestamp().orElseThrow();
                reader.seekFirst();
                ZonedDateTime firstFrameTimestamp = reader.currentFrame().timestamp().orElseThrow();
                Duration sequenceDuration = Duration.between(firstFrameTimestamp, lastFrameTimestamp);
                long seconds = sequenceDuration.getSeconds();
                value = Optional.ofNullable(seconds > 0 ? (double) header.frameCount() / seconds : null);
            }
            return value;
        }

        @Override
        public void start() {
            prepare();
            super.start();
        }

        @Override
        public void stop() {
            try {
                reader.close();
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                super.stop();
            }
        }
    }
}
