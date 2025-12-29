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
package me.champeau.a4j.jsolex.app.listeners;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Region;
import javafx.scene.transform.Transform;
import me.champeau.a4j.jsolex.app.AlertFactory;
import me.champeau.a4j.jsolex.processing.file.FileNamingStrategy;
import me.champeau.a4j.jsolex.processing.util.Constants;
import me.champeau.a4j.jsolex.processing.util.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

/**
 * Helper class for chart export operations including saving charts to files,
 * exporting to CSV, and managing chart legend interactions.
 */
final class ChartExportHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChartExportHelper.class);

    private ChartExportHelper() {
        // utility class
    }

    /**
     * Adds click handlers to chart legend items to toggle series visibility.
     *
     * @param chart the chart to add legend handlers to
     */
    static void addLegendToggleHandlers(XYChart<?, ?> chart) {
        for (var node : chart.lookupAll(".chart-legend-item")) {
            if (node instanceof Label legendLabel) {
                legendLabel.setCursor(Cursor.HAND);
                var seriesName = legendLabel.getText();
                legendLabel.setOnMouseClicked(event -> {
                    for (var s : chart.getData()) {
                        if (s.getName().equals(seriesName)) {
                            toggleSeriesVisibility(s, legendLabel);
                            break;
                        }
                    }
                });
            }
        }
    }

    /**
     * Toggles the visibility of a chart series and updates the legend opacity.
     *
     * @param series the series to toggle
     * @param legendLabel the legend label to update
     */
    static void toggleSeriesVisibility(XYChart.Series<?, ?> series, Label legendLabel) {
        var node = series.getNode();
        if (node != null) {
            var visible = !node.isVisible();
            node.setVisible(visible);
            for (var data : series.getData()) {
                if (data.getNode() != null) {
                    data.getNode().setVisible(visible);
                }
            }
            legendLabel.setOpacity(visible ? 1.0 : 0.4);
        }
    }

    /**
     * Saves a chart (or any node) to a PNG file.
     *
     * @param name the base name for the file
     * @param node the node to snapshot
     * @param outputDirectory the output directory
     * @param baseName the base name for file naming
     * @param namingStrategy the file naming strategy
     */
    static void saveChartToFile(String name, Node node, Path outputDirectory, String baseName, FileNamingStrategy namingStrategy) {
        var snapshotParameters = new SnapshotParameters();
        snapshotParameters.setTransform(Transform.scale(2, 2));
        var writable = node.snapshot(snapshotParameters, null);
        try {
            var outputFile = outputDirectory.resolve(namingStrategy.render(0, null, Constants.TYPE_DEBUG, name, baseName, null) + ".png");
            var bufferedImage = SwingFXUtils.fromFXImage(writable, null);
            createDirectoriesIfNeeded(outputFile.getParent());
            ImageIO.write(bufferedImage, "png", outputFile.toFile());
            LOGGER.info(message("chart.saved"), outputFile);
            showFileSavedAlert(message("chart.saved.title"), "Chart saved to: " + outputFile);
        } catch (IOException ex) {
            throw new ProcessingException(ex);
        }
    }

    /**
     * Exports profile data to a CSV file.
     *
     * @param name the base name for the file
     * @param csvWriter a consumer that writes CSV content to the PrintWriter
     * @param outputDirectory the output directory
     * @param baseName the base name for file naming
     * @param namingStrategy the file naming strategy
     */
    static void exportProfileToCsv(String name, Consumer<? super PrintWriter> csvWriter, Path outputDirectory, String baseName, FileNamingStrategy namingStrategy) {
        var outputFile = outputDirectory.resolve(namingStrategy.render(0, null, Constants.TYPE_DEBUG, name, baseName, null) + ".csv");
        try {
            createDirectoriesIfNeeded(outputFile.getParent());
            try (var writer = new PrintWriter(new FileWriter(outputFile.toFile(), StandardCharsets.UTF_8))) {
                csvWriter.accept(writer);
                LOGGER.info(message("csv.saved"), outputFile);
                showFileSavedAlert(message("csv.saved.title"), "CSV saved to: " + outputFile);
            }
        } catch (IOException e) {
            throw new ProcessingException(e);
        }
    }

    /**
     * Creates a context menu for chart operations (save to file, export to CSV).
     *
     * @param name the chart name
     * @param nodeToSnapshot supplier for the node to snapshot when saving
     * @param csvWriter optional CSV writer (null if not applicable)
     * @param outputDirectory the output directory
     * @param baseName the base name for file naming
     * @param namingStrategySupplier supplier for the file naming strategy
     * @return the context menu
     */
    static ContextMenu createChartContextMenu(
            String name,
            Supplier<Node> nodeToSnapshot,
            Consumer<? super PrintWriter> csvWriter,
            Path outputDirectory,
            String baseName,
            Supplier<FileNamingStrategy> namingStrategySupplier) {
        var menu = new ContextMenu();

        var saveToFile = new MenuItem(message("save.to.file"));
        saveToFile.setOnAction(evt -> saveChartToFile(name, nodeToSnapshot.get(), outputDirectory, baseName, namingStrategySupplier.get()));
        menu.getItems().add(saveToFile);

        if (csvWriter != null) {
            var exportToCsv = new MenuItem(message("export.to.csv"));
            exportToCsv.setOnAction(evt -> exportProfileToCsv(name, csvWriter, outputDirectory, baseName, namingStrategySupplier.get()));
            menu.getItems().add(exportToCsv);
        }

        return menu;
    }

    /**
     * Shows an alert dialog indicating a file was saved.
     *
     * @param title the alert title
     * @param message the alert message
     */
    static void showFileSavedAlert(String title, String message) {
        var alert = AlertFactory.info();
        alert.setTitle(title);
        var textArea = new TextArea(message);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxHeight(Region.USE_PREF_SIZE);
        textArea.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");
        textArea.setPrefRowCount(1);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setFocusTraversable(false);
        alert.getDialogPane().setContent(textArea);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private static void createDirectoriesIfNeeded(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
        }
    }
}
