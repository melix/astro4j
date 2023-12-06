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
package me.champeau.a4j.jsolex.app.jfx.stacking;

import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.app.jfx.I18N;

import java.io.File;
import java.util.List;

abstract class Card extends BorderPane {
    private final StackingAndMosaicController stackingAndMosaicController;

    protected Card(StackingAndMosaicController stackingAndMosaicController) {
        this.stackingAndMosaicController = stackingAndMosaicController;
        setOnMouseEntered(this::handleMouseEntered);
        setOnMouseExited(this::handleMouseExited);
        setDefaultStyle();
    }

    private void handleMouseExited(MouseEvent event) {
        setDefaultStyle();
    }

    protected void setDefaultStyle() {
        setStyle(
            "-fx-border-insets:8; -fx-background-insets:8; -fx-border-radius: 16; -fx-background-radius: 16; -fx-border-color: grey; -fx-border-width: 1; -fx-min-width: 200; -fx-min-height: 200; -fx-max-width:200; -fx-max-height:200; -fx-background-color: lightgrey;");
    }

    private void handleMouseEntered(MouseEvent event) {
        setStyle(
            "-fx-border-insets:6; -fx-background-insets:6; -fx-border-radius: 12;  -fx-background-radius: 12; -fx-border-color: grey; -fx-border-width: 2; -fx-min-width: 200; -fx-min-height: 200; -fx-max-width:200; -fx-max-height:200; -fx-background-color: lightblue;");
    }

    protected List<File> selectFilesUsingFileChooser() {
        var fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.string(JSolEx.class, "mosaic-params", "select.images"));
        fileChooser.getExtensionFilters().addAll(JSolEx.IMAGE_FILES_EXTENSIONS);
        var files = fileChooser.showOpenMultipleDialog(stackingAndMosaicController.getStage());
        return files == null ? List.of() : files;
    }

}
