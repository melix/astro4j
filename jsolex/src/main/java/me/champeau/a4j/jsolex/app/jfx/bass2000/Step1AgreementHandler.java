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
package me.champeau.a4j.jsolex.app.jfx.bass2000;

import javafx.application.Platform;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import me.champeau.a4j.jsolex.app.jfx.I18N;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.util.BackgroundOperations;
import me.champeau.a4j.jsolex.processing.util.Bass2000UploadHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Desktop;
import java.net.URI;
import java.text.MessageFormat;
import java.util.regex.Pattern;

class Step1AgreementHandler implements StepHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Step1AgreementHandler.class);

    private Label step1DuplicateWarningLabel;
    private ProcessParams processParams;

    Step1AgreementHandler() {
    }

    @Override
    public VBox createContent() {
        var content = new VBox(12);

        var headerLabel = new Label(message("requirements.title"));
        headerLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");

        var warningLabel = new Label(message("requirements.warning"));
        warningLabel.setWrapText(true);
        warningLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");

        var requirementsFlow = createRequirementsTextFlow();

        var processLabel = new Label(message("requirements.process"));
        processLabel.setWrapText(true);
        processLabel.setStyle("-fx-font-size: 14px;");

        var agreeLabel = new Label(message("agreement"));
        agreeLabel.setWrapText(true);
        agreeLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: red; -fx-font-weight: bold;");

        step1DuplicateWarningLabel = new Label();
        step1DuplicateWarningLabel.setWrapText(true);
        step1DuplicateWarningLabel.setVisible(false);
        step1DuplicateWarningLabel.setManaged(false);
        step1DuplicateWarningLabel.setStyle("-fx-text-fill: orange; -fx-font-weight: bold; -fx-padding: 10px; -fx-border-color: orange; -fx-border-width: 1px; -fx-background-color: #fff3cd;");

        content.getChildren().addAll(headerLabel, warningLabel, requirementsFlow, processLabel, agreeLabel, step1DuplicateWarningLabel);
        return content;
    }

    @Override
    public void load() {
    }

    void setImageAndParams(ProcessParams processParams) {
        this.processParams = processParams;
        checkForDuplicate();
    }

    @Override
    public void cleanup() {
    }

    @Override
    public boolean validate() {
        return true;
    }

    private void checkForDuplicate() {
        BackgroundOperations.async(() -> {
            var spectralRay = processParams.spectrumParams().ray();
            if (spectralRay != null) {
                var wavelengthAngstroms = spectralRay.wavelength().angstroms();
                var observationDate = processParams.observationDetails().date().toLocalDate();
                var duplicate = Bass2000UploadHistoryService.getInstance().checkForDuplicateUpload(observationDate, wavelengthAngstroms);
                Platform.runLater(() -> updateDuplicateWarning(duplicate.orElse(null)));
            }
        });
    }

    private void updateDuplicateWarning(Bass2000UploadHistoryService.UploadRecord duplicate) {
        if (duplicate != null) {
            step1DuplicateWarningLabel.setText(MessageFormat.format(message("duplicate.warning"), duplicate.sourceFilename()));
            step1DuplicateWarningLabel.setVisible(true);
            step1DuplicateWarningLabel.setManaged(true);
        } else {
            step1DuplicateWarningLabel.setVisible(false);
            step1DuplicateWarningLabel.setManaged(false);
        }
    }

    private TextFlow createRequirementsTextFlow() {
        var textFlow = new TextFlow();
        var requirementsText = message("requirements.requirements");

        var urlPattern = Pattern.compile("(https?://[^\\s]+)");
        var matcher = urlPattern.matcher(requirementsText);

        var lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                var beforeText = requirementsText.substring(lastEnd, matcher.start());
                var textNode = new Text(beforeText);
                textNode.setStyle("-fx-font-size: 14px;");
                textFlow.getChildren().add(textNode);
            }

            var url = matcher.group();
            var hyperlink = new Hyperlink(url);
            hyperlink.setOnAction(e -> {
                try {
                    Desktop.getDesktop().browse(new URI(url));
                } catch (Exception ex) {
                    LOGGER.error("Error opening URL: {}", url, ex);
                }
            });
            hyperlink.setStyle("-fx-font-size: 14px;");
            textFlow.getChildren().add(hyperlink);

            lastEnd = matcher.end();
        }

        if (lastEnd < requirementsText.length()) {
            var remainingText = requirementsText.substring(lastEnd);
            var textNode = new Text(remainingText);
            textNode.setStyle("-fx-font-size: 14px;");
            textFlow.getChildren().add(textNode);
        }

        return textFlow;
    }

    private static String message(String messageKey) {
        return I18N.string(JSolEx.class, "bass2000-submission", messageKey);
    }
}