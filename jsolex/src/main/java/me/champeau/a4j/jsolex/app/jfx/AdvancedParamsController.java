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

    private Stage stage;

    public void setup(Stage stage) {
        this.stage = stage;
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
    }

    public void close() {
        Configuration.getInstance().setWatchModeWaitTimeMilis(Integer.parseInt(watchModeWaitTimeMillis.getText()));
        AlertFactory.info(I18N.string(JSolEx.class, "advanced-params", "must.restart"))
            .showAndWait();
        stage.close();
    }

    public void cancel() {
        stage.close();
    }
}
