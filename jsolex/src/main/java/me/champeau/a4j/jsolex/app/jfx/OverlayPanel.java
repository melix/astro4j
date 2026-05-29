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

import javafx.animation.PauseTransition;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.processing.expr.impl.ImageDraw;

import java.awt.GraphicsEnvironment;
import me.champeau.a4j.jsolex.processing.params.GlobeStyle;
import me.champeau.a4j.jsolex.processing.sun.workflow.GeneratedImageKind;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

final class OverlayPanel {
    private static final double SWATCH_RADIUS = 9;
    private static final String ICON_GRID = "fltfal-grid-24";
    private static final String ICON_SCALE = "fltfmz-ruler-24";
    private static final String ICON_DETAILS = "fltfal-info-24";
    private static final String ICON_SOLAR = "fltfmz-weather-sunny-24";
    private static final String ICON_EARTH = "fltfal-earth-24";
    private static final String ICON_SIGNATURE = "fltfmz-signature-24";

    private final Consumer<ImageOverlayState> onChange;
    private final Consumer<GlobeStyle> onGlobeStyleChange;
    private final Runnable onResetEarth;
    private final Runnable onResetSignature;
    private final Runnable onResetObsDetails;
    private final Runnable onResetSolarParams;
    private final Popup popup;
    private final VBox root;
    private ImageOverlayState state;
    private GlobeStyle globeStyle;
    private boolean ignoreChanges;
    private boolean userMoved;
    private double dragOffsetX;
    private double dragOffsetY;

    private ToggleButton gridBtn;
    private Shape gridSwatch;
    private ToggleButton obsBtn;
    private Shape obsSwatch;
    private Button obsOptionsBtn;
    private ToggleButton solarBtn;
    private Shape solarSwatch;
    private ToggleButton earthBtn;
    private Button earthResetBtn;
    private ToggleButton promBtn;
    private Shape promSwatch;
    private Button promOptionsBtn;
    private Button gridOptionsBtn;
    private ToggleButton signatureBtn;
    private Shape signatureSwatch;
    private Button signatureOptionsBtn;

    OverlayPanel(GeneratedImageKind kind,
                 boolean hasEllipse,
                 ImageOverlayState initial,
                 GlobeStyle initialGlobeStyle,
                 Consumer<ImageOverlayState> onChange,
                 Consumer<GlobeStyle> onGlobeStyleChange,
                 Runnable onResetEarth,
                 Runnable onResetSignature,
                 Runnable onResetObsDetails,
                 Runnable onResetSolarParams) {
        this.state = initial;
        this.globeStyle = initialGlobeStyle;
        this.onChange = onChange;
        this.onGlobeStyleChange = onGlobeStyleChange;
        this.onResetEarth = onResetEarth;
        this.onResetSignature = onResetSignature;
        this.onResetObsDetails = onResetObsDetails;
        this.onResetSolarParams = onResetSolarParams;
        this.popup = new Popup();
        this.popup.setAutoHide(false);
        this.popup.setHideOnEscape(true);
        this.root = new VBox(6);
        this.root.getStyleClass().add("overlay-popover");
        var header = buildHeader();
        var body = buildBody(kind, hasEllipse);
        var footer = buildFooter();
        root.getChildren().addAll(header, body, footer);
        applyState(initial);
        popup.getContent().add(root);
    }

    private HBox buildHeader() {
        var title = new Label(message("overlay.title"));
        title.getStyleClass().add("overlay-popover-title");
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var close = new Button("✕");
        close.getStyleClass().add("overlay-popover-close");
        close.setFocusTraversable(false);
        close.setOnAction(e -> popup.hide());
        var header = new HBox(8, title, spacer, close);
        header.getStyleClass().add("overlay-popover-header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setOnMousePressed(e -> {
            dragOffsetX = e.getScreenX() - popup.getX();
            dragOffsetY = e.getScreenY() - popup.getY();
        });
        header.setOnMouseDragged(e -> {
            popup.setX(e.getScreenX() - dragOffsetX);
            popup.setY(e.getScreenY() - dragOffsetY);
            userMoved = true;
        });
        return header;
    }

    private HBox buildFooter() {
        var saveBtn = new Button(message("overlay.preset.save"));
        var saveIcon = safeIcon("fltfmz-save-24");
        if (saveIcon != null) {
            saveBtn.setGraphic(saveIcon);
        }
        saveBtn.getStyleClass().add("overlay-popover-footer-button");
        saveBtn.setFocusTraversable(false);
        saveBtn.setTooltip(new Tooltip(message("overlay.preset.save.tooltip")));
        var loadMenu = new MenuButton(message("overlay.preset.load"));
        var loadIcon = safeIcon("fltfmz-open-24");
        if (loadIcon != null) {
            loadMenu.setGraphic(loadIcon);
        }
        loadMenu.getStyleClass().add("overlay-popover-footer-button");
        loadMenu.setFocusTraversable(false);
        loadMenu.setTooltip(new Tooltip(message("overlay.preset.load.tooltip")));
        refreshPresetMenu(loadMenu);
        saveBtn.setOnAction(e -> {
            promptForPresetName(null).ifPresent(name -> {
                try {
                    OverlayPresetIO.save(name, state);
                    refreshPresetMenu(loadMenu);
                    flashFeedback(saveBtn, saveIcon, message("overlay.preset.save"),
                            message("overlay.preset.saved"));
                } catch (RuntimeException ex) {
                    showError(message("overlay.preset.save.failed"), ex.getMessage());
                }
            });
        });
        var doneBtn = new Button(message("overlay.done"));
        var doneIcon = safeIcon("fltfal-checkmark-24");
        if (doneIcon != null) {
            doneBtn.setGraphic(doneIcon);
        }
        doneBtn.getStyleClass().add("overlay-popover-footer-button");
        doneBtn.setFocusTraversable(false);
        doneBtn.setTooltip(new Tooltip(message("overlay.done.tooltip")));
        doneBtn.setOnAction(e -> popup.hide());
        var spacerL = new Region();
        HBox.setHgrow(spacerL, Priority.ALWAYS);
        var spacerR = new Region();
        HBox.setHgrow(spacerR, Priority.ALWAYS);
        var footer = new HBox(8, spacerL, saveBtn, loadMenu, doneBtn, spacerR);
        footer.getStyleClass().add("overlay-popover-footer");
        footer.setAlignment(Pos.CENTER);
        return footer;
    }

    private void refreshPresetMenu(MenuButton loadMenu) {
        var names = OverlayPresetIO.names();
        loadMenu.getItems().clear();
        if (names.isEmpty()) {
            loadMenu.setDisable(true);
            loadMenu.setTooltip(new Tooltip(message("overlay.preset.none")));
            return;
        }
        loadMenu.setDisable(false);
        loadMenu.setTooltip(new Tooltip(message("overlay.preset.load.tooltip")));
        for (var name : names) {
            var item = new MenuItem(name);
            item.setOnAction(e -> loadPresetByName(name, loadMenu));
            loadMenu.getItems().add(item);
        }
        loadMenu.getItems().add(new SeparatorMenuItem());
        var manage = new MenuItem(message("overlay.preset.manage"));
        manage.setOnAction(e -> showPresetManager(loadMenu));
        loadMenu.getItems().add(manage);
    }

    private void loadPresetByName(String name, MenuButton loadMenu) {
        var match = OverlayPresetIO.loadAll().stream()
                .filter(p -> p.name().equals(name))
                .findFirst();
        if (match.isEmpty()) {
            return;
        }
        state = state.mergePreset(match.get().state());
        applyState(state);
        onChange.accept(state);
        flashFeedback(loadMenu, loadMenu.getGraphic(), message("overlay.preset.load"),
                message("overlay.preset.loaded"));
    }

    private void flashFeedback(MenuButton button, Node originalIcon, String originalLabel, String confirmLabel) {
        var check = safeIcon("fltfal-checkmark-circle-24");
        button.setText(confirmLabel);
        if (check != null) {
            button.setGraphic(check);
        }
        button.getStyleClass().add("overlay-popover-footer-button-confirmed");
        var pause = new PauseTransition(Duration.seconds(1.5));
        pause.setOnFinished(ev -> {
            button.setText(originalLabel);
            button.setGraphic(originalIcon);
            button.getStyleClass().remove("overlay-popover-footer-button-confirmed");
        });
        pause.play();
    }

    private Optional<String> promptForPresetName(String suggested) {
        var textField = new TextField(suggested != null ? suggested : "");
        textField.setPromptText(message("overlay.preset.name.placeholder"));
        textField.setPrefColumnCount(24);
        var label = new Label(message("overlay.preset.name") + ":");
        var row = new HBox(8, label, textField);
        row.setAlignment(Pos.CENTER_LEFT);
        var body = new VBox(8, row);
        body.setPadding(new Insets(15, 20, 10, 20));
        var saveBtn = new Button(message("overlay.preset.save"));
        saveBtn.getStyleClass().add("primary-button");
        saveBtn.setDefaultButton(true);
        ButtonBar.setButtonData(saveBtn, ButtonBar.ButtonData.OK_DONE);
        var cancelBtn = new Button(message("overlay.close"));
        cancelBtn.getStyleClass().add("default-button");
        ButtonBar.setButtonData(cancelBtn, ButtonBar.ButtonData.CANCEL_CLOSE);
        var buttonBar = new ButtonBar();
        buttonBar.setPadding(new Insets(15, 20, 15, 20));
        buttonBar.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");
        buttonBar.getButtons().addAll(cancelBtn, saveBtn);
        var root = new BorderPane();
        root.setCenter(body);
        root.setBottom(buttonBar);
        var ownerStage = (Stage) popup.getOwnerWindow();
        var stage = FXUtils.newModalStage(ownerStage, root);
        stage.setTitle(message("overlay.preset.save"));
        var result = new String[1];
        saveBtn.setOnAction(e -> {
            var name = textField.getText() == null ? "" : textField.getText().trim();
            if (name.isEmpty()) {
                return;
            }
            result[0] = name;
            stage.close();
        });
        cancelBtn.setOnAction(e -> stage.close());
        boolean wasShowing = popup.isShowing();
        if (wasShowing) {
            popup.hide();
        }
        stage.showAndWait();
        if (wasShowing) {
            popup.show(ownerStage, popup.getX(), popup.getY());
        }
        return Optional.ofNullable(result[0]);
    }

    private void showPresetManager(MenuButton loadMenu) {
        var listView = new ListView<String>();
        listView.getItems().setAll(OverlayPresetIO.names());
        listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        listView.setPrefHeight(220);
        var body = new VBox(8, new Label(message("overlay.preset.manage") + ":"), listView);
        body.setPadding(new Insets(15, 20, 10, 20));
        var deleteBtn = new Button(message("overlay.preset.delete"));
        deleteBtn.getStyleClass().add("default-button");
        deleteBtn.setDisable(true);
        ButtonBar.setButtonData(deleteBtn, ButtonBar.ButtonData.LEFT);
        listView.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) ->
                deleteBtn.setDisable(nv == null));
        var closeBtn = new Button(message("overlay.close"));
        closeBtn.getStyleClass().add("default-button");
        ButtonBar.setButtonData(closeBtn, ButtonBar.ButtonData.CANCEL_CLOSE);
        var buttonBar = new ButtonBar();
        buttonBar.setPadding(new Insets(15, 20, 15, 20));
        buttonBar.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");
        buttonBar.getButtons().addAll(deleteBtn, closeBtn);
        var root = new BorderPane();
        root.setCenter(body);
        root.setBottom(buttonBar);
        var ownerStage = (Stage) popup.getOwnerWindow();
        var stage = FXUtils.newModalStage(ownerStage, root);
        stage.setTitle(message("overlay.preset.manage"));
        deleteBtn.setOnAction(e -> {
            var name = listView.getSelectionModel().getSelectedItem();
            if (name == null) {
                return;
            }
            OverlayPresetIO.delete(name);
            listView.getItems().setAll(OverlayPresetIO.names());
        });
        closeBtn.setOnAction(e -> stage.close());
        boolean wasShowing = popup.isShowing();
        if (wasShowing) {
            popup.hide();
        }
        stage.showAndWait();
        refreshPresetMenu(loadMenu);
        if (wasShowing) {
            popup.show(ownerStage, popup.getX(), popup.getY());
        }
    }

    private static void flashFeedback(Button button, Node originalIcon, String originalLabel, String confirmLabel) {
        var check = safeIcon("fltfal-checkmark-circle-24");
        button.setText(confirmLabel);
        if (check != null) {
            button.setGraphic(check);
        }
        button.getStyleClass().add("overlay-popover-footer-button-confirmed");
        var pause = new PauseTransition(Duration.seconds(1.5));
        pause.setOnFinished(ev -> {
            button.setText(originalLabel);
            button.setGraphic(originalIcon);
            button.getStyleClass().remove("overlay-popover-footer-button-confirmed");
        });
        pause.play();
    }

    private static void showError(String title, String body) {
        var alert = AlertFactory.warning(body);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private VBox buildBody(GeneratedImageKind kind, boolean hasEllipse) {
        var grid = buildChipsGrid(kind, hasEllipse);
        var container = new VBox(8, grid);
        container.getStyleClass().add("overlay-popover-body");
        return container;
    }


    private double initialThickness() {
        return state.lineThickness() != null ? state.lineThickness() : ImageDraw.DEFAULT_LINE_THICKNESS;
    }

    private GridPane buildChipsGrid(GeneratedImageKind kind, boolean hasEllipse) {
        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        var c0 = new ColumnConstraints();
        c0.setHgrow(Priority.ALWAYS);
        c0.setFillWidth(true);
        var c1 = new ColumnConstraints();
        c1.setHalignment(HPos.CENTER);
        c1.setMinWidth(24);
        var c2 = new ColumnConstraints();
        c2.setHalignment(HPos.CENTER);
        c2.setMinWidth(28);
        var c3 = new ColumnConstraints();
        c3.setHalignment(HPos.CENTER);
        c3.setMinWidth(28);
        grid.getColumnConstraints().addAll(c0, c1, c2, c3);

        int row = 0;
        if (hasEllipse) {
            gridBtn = makeChip(ICON_GRID, "overlay.grid",
                    ImageOverlayState::drawGlobe, ImageOverlayState::withDrawGlobe);
            gridSwatch = makeSwatch(state.gridColor(),
                    hex -> mutate(s -> s.withGridColor(hex)));
            gridOptionsBtn = new Button("⋯");
            gridOptionsBtn.getStyleClass().add("overlay-popover-mini");
            gridOptionsBtn.setFocusTraversable(false);
            gridOptionsBtn.setOnAction(e -> showGridOptions(kind));
            addRow(grid, row++, gridBtn, gridSwatch, null, gridOptionsBtn);

            promBtn = makeChip(ICON_SCALE, "overlay.prom.scale",
                    ImageOverlayState::drawProminenceScale, ImageOverlayState::withDrawProminenceScale);
            promSwatch = makeSwatch(state.promScaleColor(),
                    hex -> mutate(s -> s.withPromScaleColor(hex)));
            promOptionsBtn = new Button("⋯");
            promOptionsBtn.getStyleClass().add("overlay-popover-mini");
            promOptionsBtn.setFocusTraversable(false);
            promOptionsBtn.setOnAction(e -> showPromOptions());
            addRow(grid, row++, promBtn, promSwatch, null, promOptionsBtn);
        }
        obsBtn = makeChip(ICON_DETAILS, "overlay.obs.details",
                ImageOverlayState::drawObservationDetails, ImageOverlayState::withDrawObservationDetails);
        obsSwatch = makeSwatch(state.obsDetailsColor(),
                hex -> mutate(s -> s.withObsDetailsColor(hex)));
        obsOptionsBtn = new Button("⋯");
        obsOptionsBtn.getStyleClass().add("overlay-popover-mini");
        obsOptionsBtn.setFocusTraversable(false);
        obsOptionsBtn.setOnAction(e -> showObsTemplate());
        var obsResetBtn = new Button("⟲");
        obsResetBtn.getStyleClass().add("overlay-popover-mini");
        obsResetBtn.setFocusTraversable(false);
        obsResetBtn.setTooltip(new Tooltip(message("overlay.reset.position")));
        obsResetBtn.setOnAction(e -> {
            if (onResetObsDetails != null) {
                onResetObsDetails.run();
            }
        });
        obsResetBtn.disableProperty().bind(obsBtn.selectedProperty().not());
        addRow(grid, row++, obsBtn, obsSwatch, obsResetBtn, obsOptionsBtn);

        solarBtn = makeChip(ICON_SOLAR, "overlay.solar.params",
                ImageOverlayState::drawSolarParameters, ImageOverlayState::withDrawSolarParameters);
        solarSwatch = makeSwatch(state.solarParamsColor(),
                hex -> mutate(s -> s.withSolarParamsColor(hex)));
        var solarResetBtn = new Button("⟲");
        solarResetBtn.getStyleClass().add("overlay-popover-mini");
        solarResetBtn.setFocusTraversable(false);
        solarResetBtn.setTooltip(new Tooltip(message("overlay.reset.position")));
        solarResetBtn.setOnAction(e -> {
            if (onResetSolarParams != null) {
                onResetSolarParams.run();
            }
        });
        solarResetBtn.disableProperty().bind(solarBtn.selectedProperty().not());
        addRow(grid, row++, solarBtn, solarSwatch, solarResetBtn, null);

        if (hasEllipse) {
            earthBtn = makeChip(ICON_EARTH, "overlay.earth",
                    ImageOverlayState::drawEarth, ImageOverlayState::withDrawEarth);
            earthResetBtn = new Button("⟲");
            earthResetBtn.getStyleClass().add("overlay-popover-mini");
            earthResetBtn.setFocusTraversable(false);
            earthResetBtn.setTooltip(new Tooltip(message("overlay.reset.earth")));
            earthResetBtn.setOnAction(e -> {
                if (onResetEarth != null) {
                    onResetEarth.run();
                }
            });
            earthResetBtn.disableProperty().bind(earthBtn.selectedProperty().not());
            addRow(grid, row++, earthBtn, null, earthResetBtn, null);
        }
        signatureBtn = makeChip(ICON_SIGNATURE, "overlay.signature",
                ImageOverlayState::drawSignature, ImageOverlayState::withDrawSignature);
        signatureSwatch = makeSwatch(state.signatureColor(),
                hex -> mutate(s -> s.withSignatureColor(hex)));
        signatureOptionsBtn = new Button("⋯");
        signatureOptionsBtn.getStyleClass().add("overlay-popover-mini");
        signatureOptionsBtn.setFocusTraversable(false);
        signatureOptionsBtn.setOnAction(e -> showSignatureOptions());
        var signatureResetBtn = new Button("⟲");
        signatureResetBtn.getStyleClass().add("overlay-popover-mini");
        signatureResetBtn.setFocusTraversable(false);
        signatureResetBtn.setTooltip(new Tooltip(message("overlay.reset.position")));
        signatureResetBtn.setOnAction(e -> {
            if (onResetSignature != null) {
                onResetSignature.run();
            }
        });
        signatureResetBtn.disableProperty().bind(signatureBtn.selectedProperty().not());
        addRow(grid, row, signatureBtn, signatureSwatch, signatureResetBtn, signatureOptionsBtn);
        return grid;
    }

    private static void addRow(GridPane grid, int row, Node chip, Node swatch, Node reset, Node options) {
        grid.add(chip, 0, row);
        GridPane.setHgrow(chip, Priority.ALWAYS);
        if (chip instanceof Region r) {
            r.setMaxWidth(Double.MAX_VALUE);
        }
        if (swatch != null) {
            grid.add(swatch, 1, row);
        }
        if (options != null) {
            grid.add(options, 2, row);
        }
        if (reset != null) {
            grid.add(reset, 3, row);
        }
    }

    private ToggleButton makeChip(String iconName, String labelKey,
                                  Function<ImageOverlayState, Boolean> read,
                                  BiFunction<ImageOverlayState, Boolean, ImageOverlayState> write) {
        var btn = new ToggleButton(message(labelKey));
        btn.getStyleClass().add("overlay-chip");
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setContentDisplay(ContentDisplay.LEFT);
        btn.setGraphicTextGap(8);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setFocusTraversable(false);
        var icon = safeIcon(iconName);
        if (icon != null) {
            btn.setGraphic(icon);
        }
        btn.setSelected(read.apply(state));
        btn.selectedProperty().addListener((o, ov, nv) -> mutate(s -> write.apply(s, nv)));
        return btn;
    }

    private static FontIcon safeIcon(String name) {
        try {
            var icon = new FontIcon(name);
            icon.setIconSize(16);
            return icon;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Shape makeSwatch(String initialHex, Consumer<String> onColorChange) {
        var swatch = new Circle(SWATCH_RADIUS);
        swatch.getStyleClass().add("overlay-swatch");
        swatch.setFill(initialHex == null ? defaultSwatchFill() : parseHex(initialHex));
        swatch.setStroke(Color.web("#adb5bd"));
        swatch.setStrokeWidth(1);
        swatch.setOnMouseClicked(e -> showColorPicker(swatch, onColorChange));
        Tooltip.install(swatch, new Tooltip(message("overlay.color")));
        return swatch;
    }

    private static Color defaultSwatchFill() {
        return Color.web("#e9ecef");
    }

    private void showColorPicker(Shape swatch, Consumer<String> onColorChange) {
        var picker = new ColorPicker(swatch.getFill() instanceof Color c && !c.equals(defaultSwatchFill()) ? c : Color.WHITE);
        picker.setOnAction(e -> {
            var c = picker.getValue();
            swatch.setFill(c);
            onColorChange.accept(toHex(c));
        });
        var clearBtn = new Button(message("overlay.reset.color"));
        clearBtn.getStyleClass().add("default-button");
        clearBtn.setOnAction(e -> {
            swatch.setFill(defaultSwatchFill());
            onColorChange.accept(null);
        });
        var content = new HBox(8, picker, clearBtn);
        content.setAlignment(Pos.CENTER_LEFT);
        content.getStyleClass().add("overlay-subpopup");
        var sub = new Popup();
        sub.setAutoHide(true);
        sub.setHideOnEscape(true);
        sub.getContent().add(content);
        var bounds = swatch.localToScreen(swatch.getBoundsInLocal());
        if (bounds != null) {
            sub.show(swatch, bounds.getMinX(), bounds.getMaxY() + 4);
        }
    }

    private static ListCell<String> fontPreviewCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setFont(Font.getDefault());
                } else {
                    setText(item);
                    setFont(Font.font(item, 14));
                }
            }
        };
    }

    private void showSignatureOptions() {
        var initialText = state.signatureText() != null ? state.signatureText() : "";
        var textArea = new TextArea(initialText);
        textArea.setPrefRowCount(3);
        textArea.setPrefColumnCount(30);
        textArea.setWrapText(true);
        var textLabel = new Label(message("overlay.signature.text") + ":");
        var fontLabel = new Label(message("overlay.signature.font") + ":");
        var fontChoice = new ComboBox<String>();
        fontChoice.setVisibleRowCount(12);
        fontChoice.setPrefWidth(220);
        var fontNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        fontChoice.getItems().add(ImageDraw.DEFAULT_SIGNATURE_FONT);
        for (var name : fontNames) {
            if (!name.equals(ImageDraw.DEFAULT_SIGNATURE_FONT)) {
                fontChoice.getItems().add(name);
            }
        }
        fontChoice.setCellFactory(lv -> fontPreviewCell());
        fontChoice.setButtonCell(fontPreviewCell());
        fontChoice.setValue(state.signatureFontFamily() != null ? state.signatureFontFamily() : ImageDraw.DEFAULT_SIGNATURE_FONT);
        var sizeLabel = new Label(message("overlay.signature.size") + ":");
        int initialSize = state.signatureFontSize() != null ? state.signatureFontSize() : ImageDraw.DEFAULT_SIGNATURE_SIZE;
        var sizeSpinner = new Spinner<Integer>(8, 200, initialSize, 1);
        sizeSpinner.setEditable(true);
        sizeSpinner.setPrefWidth(90);
        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.add(textLabel, 0, 0);
        grid.add(textArea, 1, 0);
        grid.add(fontLabel, 0, 1);
        grid.add(fontChoice, 1, 1);
        grid.add(sizeLabel, 0, 2);
        grid.add(sizeSpinner, 1, 2);
        var body = new VBox(10, grid);
        body.setPadding(new Insets(15, 20, 10, 20));
        var closeBtn = new Button(message("overlay.close"));
        closeBtn.getStyleClass().add("default-button");
        ButtonBar.setButtonData(closeBtn, ButtonBar.ButtonData.CANCEL_CLOSE);
        var applyBtn = new Button(message("overlay.apply"));
        applyBtn.getStyleClass().add("primary-button");
        ButtonBar.setButtonData(applyBtn, ButtonBar.ButtonData.APPLY);
        applyBtn.setDefaultButton(true);
        var buttonBar = new ButtonBar();
        buttonBar.setPadding(new Insets(15, 20, 15, 20));
        buttonBar.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");
        buttonBar.getButtons().addAll(closeBtn, applyBtn);
        var root = new BorderPane();
        root.setCenter(body);
        root.setBottom(buttonBar);
        var ownerStage = (Stage) popup.getOwnerWindow();
        var stage = FXUtils.newModalStage(ownerStage, root);
        stage.setTitle(message("overlay.signature"));
        var snapshot = state;
        boolean[] applied = {false};
        textArea.textProperty().addListener((o, ov, nv) ->
                mutate(s -> s.withSignatureText(nv == null || nv.isBlank() ? null : nv)));
        fontChoice.valueProperty().addListener((o, ov, nv) ->
                mutate(s -> s.withSignatureFontFamily(nv == null || nv.equals(ImageDraw.DEFAULT_SIGNATURE_FONT) ? null : nv)));
        sizeSpinner.valueProperty().addListener((o, ov, nv) ->
                mutate(s -> s.withSignatureFontSize(nv == null || nv == ImageDraw.DEFAULT_SIGNATURE_SIZE ? null : nv)));
        closeBtn.setOnAction(e -> stage.close());
        applyBtn.setOnAction(e -> {
            applied[0] = true;
            stage.close();
        });
        stage.setOnHidden(e -> {
            if (!applied[0]) {
                state = snapshot;
                applyState(state);
                onChange.accept(state);
            }
        });
        boolean wasShowing = popup.isShowing();
        if (wasShowing) {
            popup.hide();
        }
        stage.showAndWait();
        if (wasShowing) {
            popup.show(ownerStage, popup.getX(), popup.getY());
        }
    }

    private void showObsTemplate() {
        var initial = state.obsDetailsTemplate() != null ? state.obsDetailsTemplate() : ImageDraw.DEFAULT_OBS_DETAILS_TEMPLATE;
        var textArea = new TextArea(initial);
        textArea.setPrefRowCount(12);
        textArea.setPrefColumnCount(48);
        textArea.setWrapText(false);
        var hint = new Label(message("overlay.obs.template.hint"));
        hint.setWrapText(true);
        hint.setMaxWidth(520);
        hint.getStyleClass().add("overlay-popover-hint");
        var body = new VBox(10, hint, textArea);
        body.setPadding(new Insets(15, 20, 10, 20));
        var resetBtn = new Button(message("overlay.reset.color"));
        resetBtn.getStyleClass().add("default-button");
        ButtonBar.setButtonData(resetBtn, ButtonBar.ButtonData.LEFT);
        var closeBtn = new Button(message("overlay.close"));
        closeBtn.getStyleClass().add("default-button");
        ButtonBar.setButtonData(closeBtn, ButtonBar.ButtonData.CANCEL_CLOSE);
        var applyBtn = new Button(message("overlay.apply"));
        applyBtn.getStyleClass().add("primary-button");
        ButtonBar.setButtonData(applyBtn, ButtonBar.ButtonData.APPLY);
        applyBtn.setDefaultButton(true);
        var buttonBar = new ButtonBar();
        buttonBar.setPadding(new Insets(15, 20, 15, 20));
        buttonBar.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");
        buttonBar.getButtons().addAll(resetBtn, closeBtn, applyBtn);
        var root = new BorderPane();
        root.setCenter(body);
        root.setBottom(buttonBar);
        var ownerStage = (Stage) popup.getOwnerWindow();
        var stage = FXUtils.newModalStage(ownerStage, root);
        stage.setTitle(message("overlay.obs.details"));
        var snapshot = state;
        boolean[] applied = {false};
        textArea.textProperty().addListener((o, ov, nv) ->
                mutate(s -> s.withObsDetailsTemplate(nv == null || nv.equals(ImageDraw.DEFAULT_OBS_DETAILS_TEMPLATE) ? null : nv)));
        resetBtn.setOnAction(e -> textArea.setText(ImageDraw.DEFAULT_OBS_DETAILS_TEMPLATE));
        closeBtn.setOnAction(e -> stage.close());
        applyBtn.setOnAction(e -> {
            applied[0] = true;
            stage.close();
        });
        stage.setOnHidden(e -> {
            if (!applied[0]) {
                state = snapshot;
                applyState(state);
                onChange.accept(state);
            }
        });
        boolean wasShowing = popup.isShowing();
        if (wasShowing) {
            popup.hide();
        }
        stage.showAndWait();
        if (wasShowing) {
            popup.show(ownerStage, popup.getX(), popup.getY());
        }
    }

    private void showPromOptions() {
        int initialCircles = state.promCircles() != null ? state.promCircles() : ImageDraw.PROMS_CIRCLES;
        int initialStep = state.promStepKm() != null ? state.promStepKm() : ImageDraw.PROMINENCE_SCALE_STEP_KM;
        var circlesLabel = new Label(message("overlay.prom.circles") + ":");
        var circlesSpinner = new Spinner<Integer>(1, 20, initialCircles);
        circlesSpinner.setEditable(true);
        circlesSpinner.setPrefWidth(80);
        circlesSpinner.valueProperty().addListener((o, ov, nv) -> {
            if (ignoreChanges || nv == null) {
                return;
            }
            mutate(s -> s.withPromCircles(nv.equals(ImageDraw.PROMS_CIRCLES) ? null : nv));
        });
        var stepLabel = new Label(message("overlay.prom.step.km") + ":");
        var stepSpinner = new Spinner<Integer>(5000, 500000, initialStep, 5000);
        stepSpinner.setEditable(true);
        stepSpinner.setPrefWidth(100);
        stepSpinner.valueProperty().addListener((o, ov, nv) -> {
            if (ignoreChanges || nv == null) {
                return;
            }
            mutate(s -> s.withPromStepKm(nv.equals(ImageDraw.PROMINENCE_SCALE_STEP_KM) ? null : nv));
        });
        var grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(circlesLabel, 0, 0);
        grid.add(circlesSpinner, 1, 0);
        grid.add(stepLabel, 0, 1);
        grid.add(stepSpinner, 1, 1);
        addPromThicknessSpinner(grid, 2);
        var content = new VBox(grid);
        content.getStyleClass().add("overlay-subpopup");
        var sub = new Popup();
        sub.setAutoHide(true);
        sub.setHideOnEscape(true);
        sub.getContent().add(content);
        var bounds = promOptionsBtn.localToScreen(promOptionsBtn.getBoundsInLocal());
        if (bounds != null) {
            sub.show(promOptionsBtn, bounds.getMinX(), bounds.getMaxY() + 4);
        }
    }

    private void addPromThicknessSpinner(GridPane grid, int row) {
        var label = new Label(message("overlay.line.thickness") + ":");
        double initial = state.promScaleLineThickness() != null ? state.promScaleLineThickness() : ImageDraw.DEFAULT_LINE_THICKNESS;
        var spinner = new Spinner<Double>(0.5, 5.0, initial, 0.5);
        spinner.setEditable(true);
        spinner.setPrefWidth(80);
        spinner.valueProperty().addListener((o, ov, nv) -> {
            if (ignoreChanges || nv == null) {
                return;
            }
            float v = nv.floatValue();
            mutate(s -> s.withPromScaleLineThickness(v == ImageDraw.DEFAULT_LINE_THICKNESS ? null : v));
        });
        grid.add(label, 0, row);
        grid.add(spinner, 1, row);
    }

    private void showGridOptions(GeneratedImageKind kind) {
        var styleLabel = new Label(message("overlay.grid.style") + ":");
        var styleChoice = new ChoiceBox<GlobeStyle>();
        styleChoice.getItems().addAll(GlobeStyle.values());
        styleChoice.setValue(globeStyle);
        styleChoice.setConverter(new StringConverter<>() {
            @Override
            public String toString(GlobeStyle s) {
                return s == null ? "" : message("globestyle." + s.name().toLowerCase());
            }

            @Override
            public GlobeStyle fromString(String s) {
                return null;
            }
        });
        styleChoice.valueProperty().addListener((o, ov, nv) -> {
            if (ignoreChanges || nv == null) {
                return;
            }
            globeStyle = nv;
            onGlobeStyleChange.accept(nv);
        });
        var content = new VBox(8);
        content.getStyleClass().add("overlay-subpopup");
        var styleRow = new HBox(6, styleLabel, styleChoice);
        styleRow.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().add(styleRow);
        if (kind == GeneratedImageKind.IMAGE_MATH) {
            var pCorrected = new CheckBox(message("overlay.p.corrected"));
            pCorrected.setSelected(state.pCorrected());
            pCorrected.selectedProperty().addListener((o, ov, nv) -> mutate(s -> s.withPCorrected(nv)));
            content.getChildren().add(pCorrected);
        }
        var thicknessLabel = new Label(message("overlay.line.thickness") + ":");
        var thicknessLocal = new Spinner<Double>(0.5, 5.0, initialThickness(), 0.5);
        thicknessLocal.setEditable(true);
        thicknessLocal.setPrefWidth(80);
        thicknessLocal.valueProperty().addListener((o, ov, nv) -> {
            if (ignoreChanges || nv == null) {
                return;
            }
            float v = nv.floatValue();
            mutate(s -> s.withLineThickness(v == ImageDraw.DEFAULT_LINE_THICKNESS ? null : v));
        });
        var thicknessRow = new HBox(6, thicknessLabel, thicknessLocal);
        thicknessRow.setAlignment(Pos.CENTER_LEFT);
        content.getChildren().add(thicknessRow);
        var sub = new Popup();
        sub.setAutoHide(true);
        sub.setHideOnEscape(true);
        sub.getContent().add(content);
        var bounds = gridOptionsBtn.localToScreen(gridOptionsBtn.getBoundsInLocal());
        if (bounds != null) {
            sub.show(gridOptionsBtn, bounds.getMinX(), bounds.getMaxY() + 4);
        }
    }

    private void applyState(ImageOverlayState s) {
        ignoreChanges = true;
        if (gridBtn != null) {
            gridBtn.setSelected(s.drawGlobe());
            gridSwatch.setFill(s.gridColor() == null ? defaultSwatchFill() : parseHex(s.gridColor()));
        }
        if (promBtn != null) {
            promBtn.setSelected(s.drawProminenceScale());
            promSwatch.setFill(s.promScaleColor() == null ? defaultSwatchFill() : parseHex(s.promScaleColor()));
        }
        obsBtn.setSelected(s.drawObservationDetails());
        obsSwatch.setFill(s.obsDetailsColor() == null ? defaultSwatchFill() : parseHex(s.obsDetailsColor()));
        solarBtn.setSelected(s.drawSolarParameters());
        solarSwatch.setFill(s.solarParamsColor() == null ? defaultSwatchFill() : parseHex(s.solarParamsColor()));
        if (earthBtn != null) {
            earthBtn.setSelected(s.drawEarth());
        }
        if (signatureBtn != null) {
            signatureBtn.setSelected(s.drawSignature());
            signatureSwatch.setFill(s.signatureColor() == null ? defaultSwatchFill() : parseHex(s.signatureColor()));
        }
        ignoreChanges = false;
    }

    private void mutate(Function<ImageOverlayState, ImageOverlayState> f) {
        if (ignoreChanges) {
            return;
        }
        state = f.apply(state);
        onChange.accept(state);
    }

    private static Color parseHex(String hex) {
        try {
            return Color.web("#" + hex);
        } catch (IllegalArgumentException ex) {
            return Color.WHITE;
        }
    }

    private static String toHex(Color c) {
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        return String.format("%02X%02X%02X", r, g, b);
    }

    void updateState(ImageOverlayState newState) {
        this.state = newState;
        applyState(newState);
    }

    void hide() {
        if (popup.isShowing()) {
            popup.hide();
        }
    }

    boolean isShowing() {
        return popup.isShowing();
    }

    void setOnShownStateChanged(Consumer<Boolean> listener) {
        popup.setOnShown(e -> listener.accept(true));
        popup.setOnHidden(e -> listener.accept(false));
    }

    void toggle(Node anchor) {
        if (popup.isShowing()) {
            popup.hide();
            return;
        }
        if (userMoved) {
            popup.show(anchor.getScene().getWindow(), popup.getX(), popup.getY());
            return;
        }
        var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds != null) {
            popup.show(anchor, bounds.getMinX(), bounds.getMaxY() + 6);
        } else {
            popup.show(anchor.getScene().getWindow());
        }
    }
}
