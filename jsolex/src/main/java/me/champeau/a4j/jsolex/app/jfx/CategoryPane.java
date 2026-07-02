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

import me.champeau.a4j.jsolex.app.util.FxUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import me.champeau.a4j.jsolex.processing.sun.workflow.PixelShift;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

/** Pane displaying a category of images with links. */
public class CategoryPane extends VBox {

    private final List<Hyperlink> safeLinks = Collections.synchronizedList(new ArrayList<>());
    private final ObservableList<Hyperlink> links = FXCollections.observableArrayList();
    private final Map<Hyperlink, RowHandle> rows = new LinkedHashMap<>();

    private Hyperlink selected = null;
    private final Consumer<? super CategoryPane> whenEmpty;
    private final Label titleLabel;
    private final String baseTitle;
    private boolean collapsed;

    /**
     * Creates a category pane.
     * @param title the category title
     * @param whenEmpty callback when the category becomes empty
     */
    public CategoryPane(String title, Consumer<? super CategoryPane> whenEmpty) {
        this.whenEmpty = whenEmpty;
        this.baseTitle = title;
        titleLabel = new Label();
        titleLabel.getStyleClass().addAll("category-title", "category-title-collapsible");
        titleLabel.setOnMouseClicked(e -> toggleCollapsed());
        var header = new HBox();
        header.getStyleClass().add("category-header");
        header.setAlignment(Pos.CENTER_LEFT);
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var closeAllButton = new Hyperlink("x");
        closeAllButton.getStyleClass().add("category-close");
        closeAllButton.setAlignment(Pos.CENTER_RIGHT);
        closeAllButton.setTooltip(new Tooltip(message("close.section")));
        closeAllButton.setOnAction(e -> closeAll());
        header.getChildren().addAll(titleLabel, spacer, closeAllButton);
        getChildren().add(header);
        getStyleClass().add("category-pane");
        updateTitle();
    }

    private void updateTitle() {
        titleLabel.setText((collapsed ? "▸ " : "▾ ") + baseTitle);
    }

    private void toggleCollapsed() {
        collapsed = !collapsed;
        for (var i = 1; i < getChildren().size(); i++) {
            applyCollapseState(getChildren().get(i));
        }
        updateTitle();
    }

    private void applyCollapseState(Node node) {
        node.setVisible(!collapsed);
        node.setManaged(!collapsed);
    }

    Hyperlink addImage(String title, PixelShift pixelShift, String badge, String badgeTooltip, Consumer<? super Hyperlink> onClick, Consumer<? super Hyperlink> onClose, Consumer<? super String> onRename, Runnable onClone) {
        var box = new HBox();
        box.getStyleClass().add("category-row");
        box.getProperties().put(PixelShift.class, pixelShift);
        box.setAlignment(Pos.CENTER_LEFT);
        var link = new Hyperlink(title);
        link.setOnAction(e -> {
            if (selected == link) {
                return;
            }
            onClick.accept(link);
            select(link);
        });
        link.getStyleClass().add("category-link");
        safeLinks.add(link);
        box.getChildren().add(link);
        if (badge != null) {
            var badgeLabel = new Label(badge);
            badgeLabel.getStyleClass().add("category-run-badge");
            if (badgeTooltip != null) {
                badgeLabel.setTooltip(new Tooltip(badgeTooltip));
            }
            box.getChildren().add(badgeLabel);
        }
        String shiftSuffix = null;
        if (pixelShift != null && pixelShift.pixelShift() != 0) {
            var label = pixelShift.pixelShift() > 0 ? "+" : "";
            var shiftValue = pixelShift.pixelShift();
            if (shiftValue == Math.floor(shiftValue)) {
                label = String.format("%s%d", label, (int) shiftValue);
            } else {
                label = String.format("%s%.2f", label, shiftValue);
            }
            var shift = new Label(label);
            shift.getStyleClass().add("category-shift");
            box.getChildren().add(shift);
            shiftSuffix = String.format(" (%s)", label);
        }
        link.setTooltip(new Tooltip(shiftSuffix == null ? title : title + shiftSuffix));
        int insertPoint = 1;
        for (int i = 1; i < getChildren().size(); i++) {
            var child = getChildren().get(i);
            var shift = (PixelShift) child.getProperties().get(PixelShift.class);
            if (shift == null || pixelShift == null || shift.pixelShift() > pixelShift.pixelShift()) {
                break;
            }
            insertPoint++;
        }
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        box.getChildren().add(spacer);
        var close = createCloseLink(box, link, onClose);
        box.getChildren().add(close);
        if (onRename != null || onClone != null) {
            installContextMenu(box, link, shiftSuffix, onRename, onClone);
        }
        getChildren().add(insertPoint, box);
        applyCollapseState(box);
        FxUtils.runLater(() -> links.setAll(safeLinks));
        return link;
    }

    private void installContextMenu(HBox box, Hyperlink link, String shiftSuffix, Consumer<? super String> onRename, Runnable onClone) {
        var menu = new ContextMenu();
        if (onRename != null) {
            var renameItem = new MenuItem(message("rename.image"));
            renameItem.setOnAction(e -> beginInlineEdit(box, link, shiftSuffix, onRename));
            menu.getItems().add(renameItem);
        }
        if (onClone != null) {
            var cloneItem = new MenuItem(message("clone.image"));
            cloneItem.setOnAction(e -> onClone.run());
            menu.getItems().add(cloneItem);
        }
        link.setContextMenu(menu);
    }

    private void beginInlineEdit(HBox box, Hyperlink link, String shiftSuffix, Consumer<? super String> onRename) {
        var idx = box.getChildren().indexOf(link);
        if (idx < 0) {
            return;
        }
        var editor = new TextField(link.getText());
        editor.getStyleClass().add("category-link-editor");
        editor.setPrefColumnCount(Math.max(8, link.getText().length()));
        Runnable cancel = () -> {
            if (box.getChildren().contains(editor)) {
                box.getChildren().set(idx, link);
            }
        };
        Runnable commit = () -> {
            var newTitle = editor.getText() == null ? "" : editor.getText().trim();
            if (newTitle.isEmpty() || newTitle.equals(link.getText())) {
                cancel.run();
                return;
            }
            link.setText(newTitle);
            link.setTooltip(new Tooltip(shiftSuffix == null ? newTitle : newTitle + shiftSuffix));
            if (box.getChildren().contains(editor)) {
                box.getChildren().set(idx, link);
            }
            onRename.accept(newTitle);
        };
        editor.setOnAction(e -> commit.run());
        editor.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                cancel.run();
                e.consume();
            }
        });
        editor.focusedProperty().addListener((obs, was, isNow) -> {
            if (was && !isNow) {
                commit.run();
            }
        });
        box.getChildren().set(idx, editor);
        editor.requestFocus();
        editor.selectAll();
    }

    private Hyperlink createCloseLink(HBox box, Hyperlink link, Consumer<? super Hyperlink> onClose) {
        rows.put(link, new RowHandle(box, onClose));
        var close = new Hyperlink("x");
        close.getStyleClass().add("category-close");
        close.setAlignment(Pos.CENTER_RIGHT);
        close.setOnAction(e -> closeRow(link, true));
        close.setTooltip(new Tooltip(message("close.image")));
        return close;
    }

    private void closeRow(Hyperlink link, boolean reselect) {
        var handle = rows.remove(link);
        if (handle == null) {
            return;
        }
        getChildren().remove(handle.box());
        var idx = Math.max(0, links.indexOf(link) - 1);
        links.remove(link);
        safeLinks.remove(link);
        handle.onClose().accept(link);
        if (links.isEmpty()) {
            whenEmpty.accept(this);
        } else if (reselect && selected == link) {
            links.get(idx).fire();
        }
        if (selected == link) {
            selected = null;
        }
    }

    /** Closes every item in this section, which also removes the section once empty. */
    public void closeAll() {
        for (var link : new ArrayList<>(links)) {
            closeRow(link, false);
        }
    }

    private record RowHandle(HBox box, Consumer<? super Hyperlink> onClose) {
    }

    /**
     * Adds a video link.
     * @param title the video title
     * @param onClick callback when clicked
     * @param onClose callback when closed
     * @return the created hyperlink
     */
    public Hyperlink addVideo(String title, String badge, String badgeTooltip, Consumer<? super Hyperlink> onClick, Consumer<? super Hyperlink> onClose) {
        var link = new Hyperlink(title);
        link.setOnAction(e -> {
            if (selected == link) {
                return;
            }
            onClick.accept(link);
            select(link);
        });
        link.getStyleClass().add("category-link");
        links.add(link);
        var box = new HBox();
        box.getStyleClass().add("category-row");
        box.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(link);
        if (badge != null) {
            var badgeLabel = new Label(badge);
            badgeLabel.getStyleClass().add("category-run-badge");
            if (badgeTooltip != null) {
                badgeLabel.setTooltip(new Tooltip(badgeTooltip));
            }
            box.getChildren().add(badgeLabel);
        }
        var spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        var close = createCloseLink(box, link, onClose);
        box.getChildren().addAll(spacer, close);
        getChildren().add(box);
        applyCollapseState(box);
        return link;
    }

    private void select(Hyperlink link) {
        clearSelection();
        if (link.getParent() instanceof HBox row) {
            row.getStyleClass().add("category-row-selected");
        }
        selected = link;
    }

    /** Clears the current selection. */
    public void clearSelection() {
        if (selected != null) {
            if (selected.getParent() instanceof HBox row) {
                row.getStyleClass().remove("category-row-selected");
            }
            selected = null;
        }
    }

    /** Selects the first item. */
    public void selectFirst() {
        links.stream()
            .findFirst()
            .ifPresent(Hyperlink::fire);
    }

}
