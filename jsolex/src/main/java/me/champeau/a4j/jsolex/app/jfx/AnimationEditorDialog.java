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
package me.champeau.a4j.jsolex.app.jfx;

import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.processing.util.AnimationEditor;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static me.champeau.a4j.jsolex.app.JSolEx.message;
import static me.champeau.a4j.jsolex.app.jfx.FXUtils.newModalStage;

/**
 * Dialog which lets the user crop, flip or rotate an animation, using
 * a single frame as a preview. The operations are then applied to every frame.
 */
public class AnimationEditorDialog {
    private final BufferedImage sourceFrame;
    private final List<AnimationEditor.Operation> operations = new ArrayList<>();
    private final ZoomableImageView preview = new ZoomableImageView();
    private final Label dimensions = new Label();
    private BufferedImage currentFrame;

    private AnimationEditorDialog(BufferedImage sourceFrame) {
        this.sourceFrame = sourceFrame;
        this.currentFrame = sourceFrame;
    }

    /**
     * Opens the dialog.
     *
     * @param owner the owner stage
     * @param frame the frame used as a preview
     * @param onApply called with the operations to apply, in order, when the user confirms
     */
    public static void open(Stage owner, BufferedImage frame, Consumer<? super List<AnimationEditor.Operation>> onApply) {
        var dialog = new AnimationEditorDialog(frame);
        var stage = newModalStage(owner, dialog.createContent(onApply));
        stage.setResizable(true);
        stage.setTitle(message("edit.animation.title"));
        stage.show();
    }

    private BorderPane createContent(Consumer<? super List<AnimationEditor.Operation>> onApply) {
        var root = new BorderPane();
        root.getStyleClass().add("image-viewer-root");
        root.setPadding(new Insets(8));
        root.setPrefSize(900, 700);

        var host = new StackPane(preview);
        preview.setSelectionOverlayHost(host);
        preview.setRectangleSelectionListener(new RectangleSelectionListener() {
            @Override
            public boolean supports(ActionKind kind) {
                return kind == ActionKind.CROP;
            }

            @Override
            public void onSelectRegion(ActionKind kind, int x, int y, int width, int height) {
                addOperation(new AnimationEditor.Crop(x, y, width, height));
            }
        });
        HBox.setHgrow(host, Priority.ALWAYS);
        root.setCenter(host);
        root.setTop(createToolbar());
        root.setBottom(createButtonBar(onApply));
        refreshPreview();
        return root;
    }

    private HBox createToolbar() {
        var leftRotate = createIconButton("↶", message("rotate.left"));
        leftRotate.setOnAction(evt -> addOperation(new AnimationEditor.Rotate(-1)));
        var rightRotate = createIconButton("↷", message("rotate.right"));
        rightRotate.setOnAction(evt -> addOperation(new AnimationEditor.Rotate(1)));
        var verticalFlip = createIconButton("⇅", message("vertical.flip"));
        verticalFlip.setOnAction(evt -> addOperation(new AnimationEditor.Flip(true)));
        var horizontalFlip = createIconButton("⇄", message("horizontal.flip"));
        horizontalFlip.setOnAction(evt -> addOperation(new AnimationEditor.Flip(false)));
        var crop = createIconButton("⛶", message("crop"));
        crop.setOnAction(evt -> preview.startCropSelection());
        var hint = new Label(message("edit.animation.hint"));
        hint.getStyleClass().add("help-text");
        hint.setWrapText(true);
        HBox.setHgrow(hint, Priority.ALWAYS);
        var toolbar = new HBox(8, leftRotate, rightRotate, verticalFlip, horizontalFlip, crop, dimensions, hint);
        toolbar.getStyleClass().add("image-viewer-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(10));
        return toolbar;
    }

    private ButtonBar createButtonBar(Consumer<? super List<AnimationEditor.Operation>> onApply) {
        var reset = new Button(message("reset"));
        reset.getStyleClass().add("default-button");
        reset.setOnAction(evt -> {
            operations.clear();
            currentFrame = sourceFrame;
            refreshPreview();
        });
        ButtonBar.setButtonData(reset, ButtonBar.ButtonData.LEFT);
        var cancel = new Button(message("cancel"));
        cancel.getStyleClass().add("default-button");
        cancel.setCancelButton(true);
        cancel.setOnAction(evt -> closeWindow(cancel));
        ButtonBar.setButtonData(cancel, ButtonBar.ButtonData.CANCEL_CLOSE);
        var apply = new Button(message("apply"));
        apply.getStyleClass().add("primary-button");
        apply.setDefaultButton(true);
        apply.setOnAction(evt -> {
            closeWindow(apply);
            onApply.accept(List.copyOf(operations));
        });
        ButtonBar.setButtonData(apply, ButtonBar.ButtonData.OK_DONE);
        var bar = new ButtonBar();
        bar.getButtons().addAll(reset, cancel, apply);
        bar.setPadding(new Insets(10, 0, 0, 0));
        return bar;
    }

    private static void closeWindow(Button button) {
        ((Stage) button.getScene().getWindow()).close();
    }

    private static Button createIconButton(String glyph, String tooltip) {
        var button = new Button(glyph);
        button.getStyleClass().addAll("image-viewer-button", "image-viewer-icon-button");
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    private void addOperation(AnimationEditor.Operation operation) {
        operations.add(operation);
        currentFrame = operation.apply(currentFrame);
        refreshPreview();
    }

    private void refreshPreview() {
        preview.setImage(SwingFXUtils.toFXImage(currentFrame, null));
        dimensions.setText(currentFrame.getWidth() + "x" + currentFrame.getHeight());
    }
}
