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

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;

public class AdvancedParamsController {
    @FXML
    private TextField watchModeWaitTimeMillis;

    @FXML
    private Slider memoryRestrictionMultiplier;

    @FXML
    private Label memoryRestrictionHelp;

    @FXML
    private TextField bass2000FtpUrl;

    @FXML
    private Button resetFtpUrlButton;

    @FXML
    private CheckBox pippCompatibleFits;

    private Stage stage;

    public void setup(Stage stage) {
        this.stage = stage;
        this.memoryRestrictionHelp.textProperty().bind(memoryRestrictionMultiplier.valueProperty().map(v -> "(" + v.intValue() + ") " + computeMemoryUsageHelpLabel(v)));
        watchModeWaitTimeMillis.setTextFormatter(new TextFormatter<>(new IntegerStringConverter() {
            @Override
            public Integer fromString(String s) {
                var value = super.fromString(s);
                if (value != null && value < 500) {
                    value = 500;
                }
                return value;
            }
        }));
        var initialWaitTime = Configuration.getInstance().getWatchModeWaitTimeMilis();
        watchModeWaitTimeMillis.setText(String.valueOf(initialWaitTime));
        memoryRestrictionMultiplier.setValue(Configuration.getInstance().getMemoryRestrictionMultiplier());
        bass2000FtpUrl.setText(Configuration.getInstance().getBass2000FtpUrl());
        pippCompatibleFits.setSelected(Configuration.getInstance().isWritePippCompatibleFits());
        pippCompatibleFits.setOnAction(event -> handlePippCompatibilityChange());
    }

    public static String computeMemoryUsageHelpLabel(Number value) {
        if (value.doubleValue() < 4) {
            return I18N.string(JSolEx.class, "advanced-params", "memory.usage.high");
        }
        if (value.doubleValue() < 8) {
            return I18N.string(JSolEx.class, "advanced-params", "memory.usage.conservative");
        }
        if (value.doubleValue() >= 16) {
            return I18N.string(JSolEx.class, "advanced-params", "memory.usage.very.conservative");
        }
        return I18N.string(JSolEx.class, "advanced-params", "memory.usage.low");
    }

    private void handlePippCompatibilityChange() {
        if (pippCompatibleFits.isSelected()) {
            var result = AlertFactory.confirmation(
                I18N.string(JSolEx.class, "advanced-params", "pipp.compatible.fits.warning")
            ).showAndWait();
            
            if (result.isEmpty() || result.get() != javafx.scene.control.ButtonType.OK) {
                pippCompatibleFits.setSelected(false);
            }
        }
    }

    public void close() {
        Configuration.getInstance().setWatchModeWaitTimeMilis(Integer.parseInt(watchModeWaitTimeMillis.getText()));
        Configuration.getInstance().setMemoryRestrictionMultiplier((int) memoryRestrictionMultiplier.getValue());
        Configuration.getInstance().setBass2000FtpUrl(bass2000FtpUrl.getText());
        Configuration.getInstance().setWritePippCompatibleFits(pippCompatibleFits.isSelected());
        AlertFactory.info(I18N.string(JSolEx.class, "advanced-params", "must.restart"))
            .showAndWait();
        stage.close();
    }

    public void cancel() {
        stage.close();
    }

    public void resetFtpUrl() {
        bass2000FtpUrl.setText(Configuration.DEFAULT_SOLAP_URL);
    }
}
