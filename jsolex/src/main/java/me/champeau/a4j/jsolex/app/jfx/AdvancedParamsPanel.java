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

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.converter.IntegerStringConverter;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.util.AnimationFormat;
import me.champeau.a4j.jsolex.processing.util.ImageFormat;
import me.champeau.a4j.jsolex.processing.util.LocaleUtils;

import java.util.EnumSet;

public class AdvancedParamsPanel extends BaseParameterPanel {
    private final TextField watchModeWaitTimeMillis;
    private final Slider memoryRestrictionMultiplier;
    private final Label memoryRestrictionHelp;
    private final TextField bass2000FtpUrl;
    private final CheckBox pippCompatibleFits;
    private final ChoiceBox<String> languageSelector;
    private final CheckBox generatePng;
    private final CheckBox generateJpg;
    private final CheckBox generateTif;
    private final CheckBox generateFits;
    private final CheckBox generateMp4;
    private final CheckBox generateGif;

    private int initialMemoryRestriction;
    private String initialLanguage;

    public AdvancedParamsPanel() {
        getStyleClass().add("parameter-panel");
        setPadding(new Insets(24));
        setSpacing(24);

        languageSelector = createChoiceBox();
        watchModeWaitTimeMillis = createTextField("", I18N.string(JSolEx.class, "advanced-params", "watch.mode.wait.time.tooltip"));
        memoryRestrictionMultiplier = new Slider(1, 32, 1);
        memoryRestrictionHelp = new Label();
        bass2000FtpUrl = createTextField("", I18N.string(JSolEx.class, "advanced-params", "bass2000.ftp.url.tooltip"));
        pippCompatibleFits = createCheckBox("", I18N.string(JSolEx.class, "advanced-params", "pipp.compatible.fits.tooltip"));
        generatePng = createCheckBox("PNG", I18N.string(JSolEx.class, "advanced-params", "generate.png.files"));
        generateJpg = createCheckBox("JPG", I18N.string(JSolEx.class, "advanced-params", "generate.jpg.files"));
        generateTif = createCheckBox("TIF", I18N.string(JSolEx.class, "advanced-params", "generate.tif.files"));
        generateFits = createCheckBox("FITS", I18N.string(JSolEx.class, "advanced-params", "generate.fits.files"));
        generateMp4 = createCheckBox("MP4", I18N.string(JSolEx.class, "advanced-params", "generate.mp4.files"));
        generateGif = createCheckBox("GIF", I18N.string(JSolEx.class, "advanced-params", "generate.gif.files"));

        setupLayout();
        loadConfiguration();
    }

    private void setupLayout() {
        var localizationSection = createSection("localization.section");
        var localizationGrid = createGrid();
        addGridRow(localizationGrid, 0,
                I18N.string(JSolEx.class, "advanced-params", "language"),
                languageSelector,
                "language.tooltip");
        localizationSection.getChildren().add(localizationGrid);

        var outputSection = createSection("output.section");
        var outputGrid = createGrid();

        var formatsBox = new VBox(8);
        formatsBox.getChildren().addAll(generatePng, generateJpg, generateTif, generateFits);

        addGridRow(outputGrid, 0,
                I18N.string(JSolEx.class, "advanced-params", "image.formats"),
                formatsBox,
                "image.formats.tooltip");

        var animationFormatsBox = new VBox(8);
        animationFormatsBox.getChildren().addAll(generateMp4, generateGif);

        addGridRow(outputGrid, 1,
                I18N.string(JSolEx.class, "advanced-params", "animation.formats"),
                animationFormatsBox,
                "animation.formats.tooltip");

        outputSection.getChildren().add(outputGrid);

        var performanceSection = createSection("performance.section");
        var performanceGrid = createGrid();

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

        var waitTimeBox = new HBox(8);
        waitTimeBox.getChildren().addAll(
                watchModeWaitTimeMillis,
                new Label("ms")
        );

        addGridRow(performanceGrid, 0,
                I18N.string(JSolEx.class, "advanced-params", "watch.mode.wait.time"),
                waitTimeBox,
                "watch.mode.wait.time.tooltip");

        memoryRestrictionMultiplier.setBlockIncrement(1.0);
        memoryRestrictionMultiplier.setMajorTickUnit(8.0);
        memoryRestrictionMultiplier.setMinorTickCount(1);
        memoryRestrictionMultiplier.setTooltip(new Tooltip(I18N.string(JSolEx.class, "advanced-params", "memory.restriction.tooltip")));
        memoryRestrictionHelp.getStyleClass().add("help-text");
        memoryRestrictionHelp.textProperty().bind(
                memoryRestrictionMultiplier.valueProperty().map(v ->
                        "(" + v.intValue() + ") " + computeMemoryUsageHelpLabel(v))
        );

        var memoryBox = new VBox(8);
        memoryBox.getChildren().addAll(memoryRestrictionMultiplier, memoryRestrictionHelp);

        addGridRow(performanceGrid, 1,
                I18N.string(JSolEx.class, "advanced-params", "memory.restriction"),
                memoryBox,
                "memory.restriction.tooltip");

        performanceSection.getChildren().add(performanceGrid);

        var dataSection = createSection("data.section");
        var dataGrid = createGrid();

        var resetButton = new Button(I18N.string(JSolEx.class, "advanced-params", "reset"));
        resetButton.setOnAction(e -> bass2000FtpUrl.setText(Configuration.DEFAULT_SOLAP_URL));
        resetButton.setTooltip(new Tooltip(I18N.string(JSolEx.class, "advanced-params", "reset.ftp.url.tooltip")));

        var ftpBox = new HBox(8);
        ftpBox.getChildren().addAll(bass2000FtpUrl, resetButton);

        addGridRow(dataGrid, 0,
                I18N.string(JSolEx.class, "advanced-params", "bass2000.ftp.url"),
                ftpBox,
                "bass2000.ftp.url.tooltip");

        addGridRow(dataGrid, 1,
                I18N.string(JSolEx.class, "advanced-params", "pipp.compatible.fits"),
                pippCompatibleFits,
                "pipp.compatible.fits.tooltip");

        dataSection.getChildren().add(dataGrid);

        getChildren().addAll(localizationSection, outputSection, performanceSection, dataSection);
    }

    private void loadConfiguration() {
        var config = Configuration.getInstance();

        languageSelector.getItems().addAll(
                I18N.string(JSolEx.class, "advanced-params", "language.english"),
                I18N.string(JSolEx.class, "advanced-params", "language.french")
        );

        initialLanguage = config.getSelectedLanguage();
        if (initialLanguage == null) {
            initialLanguage = LocaleUtils.getConfiguredLanguageCode();
        }
        if ("fr".equals(initialLanguage)) {
            languageSelector.setValue(I18N.string(JSolEx.class, "advanced-params", "language.french"));
        } else {
            languageSelector.setValue(I18N.string(JSolEx.class, "advanced-params", "language.english"));
        }

        watchModeWaitTimeMillis.setText(String.valueOf(config.getWatchModeWaitTimeMilis()));

        initialMemoryRestriction = config.getMemoryRestrictionMultiplier();
        memoryRestrictionMultiplier.setValue(initialMemoryRestriction);

        bass2000FtpUrl.setText(config.getBass2000FtpUrl());
        pippCompatibleFits.setSelected(config.isWritePippCompatibleFits());

        var imageFormats = config.getImageFormats();
        generatePng.setSelected(imageFormats.contains(ImageFormat.PNG));
        generateJpg.setSelected(imageFormats.contains(ImageFormat.JPG));
        generateTif.setSelected(imageFormats.contains(ImageFormat.TIF));
        generateFits.setSelected(imageFormats.contains(ImageFormat.FITS));

        var animationFormats = config.getAnimationFormats();
        generateMp4.setSelected(animationFormats.contains(AnimationFormat.MP4));
        generateGif.setSelected(animationFormats.contains(AnimationFormat.GIF));
    }

    public void saveConfiguration() {
        var config = Configuration.getInstance();

        config.setWatchModeWaitTimeMilis(Integer.parseInt(watchModeWaitTimeMillis.getText()));
        config.setMemoryRestrictionMultiplier((int) memoryRestrictionMultiplier.getValue());
        config.setBass2000FtpUrl(bass2000FtpUrl.getText());
        config.setWritePippCompatibleFits(pippCompatibleFits.isSelected());

        var selectedLanguageDisplay = languageSelector.getValue();
        var frenchDisplay = I18N.string(JSolEx.class, "advanced-params", "language.french");
        var newLanguage = frenchDisplay.equals(selectedLanguageDisplay) ? "fr" : "en";
        config.setSelectedLanguage(newLanguage);

        var imageFormats = EnumSet.noneOf(ImageFormat.class);
        if (generatePng.isSelected()) {
            imageFormats.add(ImageFormat.PNG);
        }
        if (generateJpg.isSelected()) {
            imageFormats.add(ImageFormat.JPG);
        }
        if (generateTif.isSelected()) {
            imageFormats.add(ImageFormat.TIF);
        }
        if (generateFits.isSelected()) {
            imageFormats.add(ImageFormat.FITS);
        }
        config.setImageFormats(imageFormats);

        var animationFormats = EnumSet.noneOf(AnimationFormat.class);
        if (generateMp4.isSelected()) {
            animationFormats.add(AnimationFormat.MP4);
        }
        if (generateGif.isSelected()) {
            animationFormats.add(AnimationFormat.GIF);
        }
        config.setAnimationFormats(animationFormats);
    }

    public boolean requiresRestart() {
        var newMemoryRestriction = (int) memoryRestrictionMultiplier.getValue();
        var selectedLanguageDisplay = languageSelector.getValue();
        var frenchDisplay = I18N.string(JSolEx.class, "advanced-params", "language.french");
        var newLanguage = frenchDisplay.equals(selectedLanguageDisplay) ? "fr" : "en";

        var memoryChanged = initialMemoryRestriction != newMemoryRestriction;
        var languageChanged = !newLanguage.equals(initialLanguage);

        return memoryChanged || languageChanged;
    }

    private String computeMemoryUsageHelpLabel(Number value) {
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

    @Override
    protected void addGridRow(GridPane grid, int row, String labelText, Node control, String tooltipKey) {
        var label = new Label(labelText);
        label.getStyleClass().addAll("field-label", "field-label-wrapped");

        grid.add(label, 0, row);
        grid.add(control, 1, row);

        if (tooltipKey != null) {
            var tooltipText = I18N.string(JSolEx.class, "advanced-params", tooltipKey);
            if (!tooltipText.equals(tooltipKey)) {
                var helpIcon = new Label("?");
                helpIcon.getStyleClass().addAll("help-icon");
                helpIcon.setTooltip(new Tooltip(tooltipText));
                grid.add(helpIcon, 2, row);
            }
        }
    }

    protected VBox createSection(String title) {
        var localized = I18N.string(JSolEx.class, "advanced-params", title);
        if (localized.isEmpty()) {
            localized = title;
        }
        var section = new VBox(8);
        section.getStyleClass().add("panel-section");

        var titleLabel = new Label(localized);
        titleLabel.getStyleClass().add("panel-section-title");
        section.getChildren().add(titleLabel);

        return section;
    }
}
