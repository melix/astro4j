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

import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;

class PlusCard extends Card {

    private final StackingAndMosaicController stackingAndMosaicController;

    public PlusCard(StackingAndMosaicController stackingAndMosaicController) {
        super(stackingAndMosaicController);
        this.stackingAndMosaicController = stackingAndMosaicController;
        var circle = new Circle(50); // Adjust the radius as needed
        circle.setFill(Color.TRANSPARENT);
        circle.setStroke(Color.BLACK);
        circle.setStrokeWidth(4);
        var hline = new Line(75, 75, 125, 75);
        hline.setStrokeWidth(4);
        var vline = new Line(100, 50, 100, 100);
        vline.setStrokeWidth(4);
        var stackPane = new StackPane();
        stackPane.getChildren().addAll(circle, hline, vline);
        setCenter(stackPane);
        setOnMouseClicked(event -> addPanelCard());
    }

    private void addPanelCard() {
        var files = selectFilesUsingFileChooser();
        if (!files.isEmpty()) {
            var card = new PanelCard(stackingAndMosaicController);
            card.getListView().getItems().addAll(files);
            stackingAndMosaicController.getCardsPane().getChildren().add(0, card);
        }
    }
}
