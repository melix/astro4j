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

import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.converter.DoubleStringConverter;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.color.ColorCurve;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.params.SpectralRayIO;
import me.champeau.a4j.jsolex.processing.stretching.LinearStrechingStrategy;
import me.champeau.a4j.jsolex.processing.sun.ImageUtils;
import me.champeau.a4j.jsolex.processing.util.ColorizedImageWrapper;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.jsolex.processing.util.MutableMap;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class SpectralRayEditor {
    private static final ImageWrapper32 MONO_SUN_IMAGE = SunDiskColorPreview.getMono();

    @FXML
    public ListView<SpectralRay> elements;
    @FXML
    private CheckBox curveCheckbox;
    @FXML
    private ImageView sunPreview;
    @FXML
    private Slider bIn;
    @FXML
    private Slider bOut;
    @FXML
    private Slider gIn;
    @FXML
    private Slider gOut;
    @FXML
    private TextField label;
    @FXML
    private Slider rIn;
    @FXML
    private Slider rOut;
    @FXML
    private TextField wavelength;

    private Stage stage;
    private SpectralRay selectedRay;

    public static void openEditor(Stage stage, Consumer<? super SpectralRayEditor> onCloseRequest) {
        var fxmlLoader = I18N.fxmlLoader(JSolEx.class, "spectral-ray-editor");
        try {
            var node = (Parent) fxmlLoader.load();
            var controller = (SpectralRayEditor) fxmlLoader.getController();
            controller.setup(stage);
            Scene scene = new Scene(node);
            var currentScene = stage.getScene();
            stage.setTitle(I18N.string(JSolEx.class, "spectral-ray-editor", "frame.title"));
            stage.setScene(scene);
            stage.show();
            stage.setOnCloseRequest(e -> {
                if (stage.getScene() == scene) {
                    stage.setScene(currentScene);
                    e.consume();
                }
                onCloseRequest.accept(controller);
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static double toDoubleValue(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Double.parseDouble(text);
    }

    public void setup(Stage stage) {
        var rays = SpectralRayIO.loadDefaults();
        this.stage = stage;
        wavelength.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        var items = elements.getItems();
        items.addAll(rays);
        var selectionModel = elements.getSelectionModel();
        var updating = new AtomicBoolean();
        selectionModel.selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (items.isEmpty()) {
                return;
            }
            if (updating.compareAndSet(false, true)) {
                var index = newValue.intValue();
                var item = items.get(index);
                label.setText(item.label());
                wavelength.setText(Double.toString(item.wavelength()));
                var curve = item.colorCurve();
                if (curve == null) {
                    curveCheckbox.setSelected(false);
                    rIn.setValue(127);
                    rOut.setValue(127);
                    gIn.setValue(127);
                    gOut.setValue(127);
                    bIn.setValue(127);
                    bOut.setValue(127);
                } else {
                    curveCheckbox.setSelected(true);
                    rIn.setValue(curve.rIn());
                    rOut.setValue(curve.rOut());
                    gIn.setValue(curve.gIn());
                    gOut.setValue(curve.gOut());
                    bIn.setValue(curve.bIn());
                    bOut.setValue(curve.bOut());
                }
                updateEditableInOut(curveCheckbox.isSelected());
                updating.set(false);
                updateSunDiskPreview(item);
            }
        });
        ChangeListener<Object> updateValueListener = (obs, oldValue, newValue) -> {
            if (updating.compareAndSet(false, true)) {
                var newLabel = label.getText();
                var newWavelen = toDoubleValue(wavelength.getText());
                var hasColor = curveCheckbox.isSelected();
                var rInValue = rIn.valueProperty().intValue();
                var rOutValue = rOut.valueProperty().intValue();
                var gInValue = gIn.valueProperty().intValue();
                var gOutValue = gOut.valueProperty().intValue();
                var bInValue = bIn.valueProperty().intValue();
                var bOutValue = bOut.valueProperty().intValue();
                var newRay = new SpectralRay(
                    newLabel,
                    hasColor ? new ColorCurve(newLabel, rInValue, rOutValue, gInValue, gOutValue, bInValue, bOutValue) : null,
                    newWavelen
                );
                updateEditableInOut(hasColor);
                items.set(selectionModel.getSelectedIndex(), newRay);
                updating.set(false);
                updateSunDiskPreview(newRay);
            }
        };
        label.textProperty().addListener(updateValueListener);
        wavelength.textProperty().addListener(updateValueListener);
        curveCheckbox.selectedProperty().addListener(updateValueListener);
        rIn.valueProperty().addListener(updateValueListener);
        rOut.valueProperty().addListener(updateValueListener);
        gIn.valueProperty().addListener(updateValueListener);
        gOut.valueProperty().addListener(updateValueListener);
        bIn.valueProperty().addListener(updateValueListener);
        bOut.valueProperty().addListener(updateValueListener);
        if (!rays.isEmpty()) {
            selectionModel.select(0);
        }
    }

    private void updateEditableInOut(boolean enabled) {
        rIn.setDisable(!enabled);
        rOut.setDisable(!enabled);
        gIn.setDisable(!enabled);
        gOut.setDisable(!enabled);
        bIn.setDisable(!enabled);
        bOut.setDisable(!enabled);
    }

    private void updateSunDiskPreview(SpectralRay newRay) {
        var curve = newRay.colorCurve();
        var colorImage = new ColorizedImageWrapper(MONO_SUN_IMAGE, mono -> {
            if (curve != null) {
                return ImageUtils.convertToRGB(curve, mono);
            } else {
                var rgbColor = newRay.toRGB();
                if (rgbColor[0] == 0 && rgbColor[1] == 0 && rgbColor[2] == 0) {
                    return new float[][]{mono, mono, mono};
                }
                var r = new float[mono.length];
                var g = new float[mono.length];
                var b = new float[mono.length];
                for (int i = 0; i < mono.length; i++) {
                    var gray = mono[i];
                    r[i] = gray * rgbColor[0] / 255f;
                    g[i] = gray * rgbColor[1] / 255f;
                    b[i] = gray * rgbColor[2] / 255f;
                }
                return new float[][]{r, g, b};
            }
        }, MutableMap.of());
        var colorized = colorImage.converter().apply(MONO_SUN_IMAGE.data());
        var r = colorized[0];
        var g = colorized[1];
        var b = colorized[2];
        Path tmpFile;
        try {
            tmpFile = Files.createTempFile("img", ".png");
            ImageUtils.writeRgbImage(colorImage.width(), colorImage.height(), r, g, b, tmpFile.toFile(), EnumSet.of(ImageFormat.PNG));
            sunPreview.setImage(new Image(tmpFile.toFile().toURI().toString()));
            Files.delete(tmpFile);
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    @FXML
    public void close() {
        SpectralRayIO.saveDefaults(elements.getItems());
        this.selectedRay = elements.getSelectionModel().getSelectedItem();
        requestClose();
    }

    private void requestClose() {
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    @FXML
    public void cancel() {
        this.selectedRay = null;
        requestClose();
    }

    @FXML
    public void reset() {
        elements.getItems().clear();
        elements.getItems().addAll(SpectralRay.predefined());
        elements.getSelectionModel().select(0);
    }

    public Optional<SpectralRay> getSelectedItem() {
        return Optional.ofNullable(selectedRay);
    }

    @FXML
    public void removeSelectedItem() {
        var idx = elements.getSelectionModel().getSelectedIndex();
        elements.getItems().remove(idx);
    }

    @FXML
    public void addNewItem() {
        var spectralRay = new SpectralRay(
            "<new> " + elements.getItems().size(),
            null,
            0
        );
        elements.getItems().add(spectralRay);
        elements.getSelectionModel().select(spectralRay);
    }

    static class SunDiskColorPreview {
        static ImageWrapper32 getMono() {
            BufferedImage image;
            try {
                image = ImageIO.read(getMonoImageStream());
            } catch (IOException e) {
                return null;
            }
            var width = image.getWidth();
            var height = image.getHeight();
            var data = new float[width * height];
            var rgb = image.getRGB(0, 0, width, height, null, 0, width);
            for (int i = 0; i < data.length; i++) {
                data[i] = rgb[i] & 0xFF;
            }
            var result = new ImageWrapper32(width, height, data, MutableMap.of());
            LinearStrechingStrategy.DEFAULT.stretch(result);
            return result;
        }

        private static InputStream getMonoImageStream() {
            return SunDiskColorPreview.class.getResourceAsStream("/me/champeau/a4j/jsolex/app/img/sun512.png");
        }
    }
}
