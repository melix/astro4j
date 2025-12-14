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

import javafx.application.HostServices;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import me.champeau.a4j.jsolex.app.jfx.ApplyUserRotation;
import me.champeau.a4j.jsolex.app.jfx.MultipleImagesViewer;
import me.champeau.a4j.jsolex.processing.event.ProgressOperation;
import me.champeau.a4j.jsolex.processing.expr.ImageMathScriptExecutor;
import me.champeau.a4j.jsolex.processing.params.ProcessParams;
import me.champeau.a4j.jsolex.processing.params.SpectralRay;
import me.champeau.a4j.jsolex.processing.sun.TrimmingParameters;
import me.champeau.a4j.jsolex.processing.util.ImageWrapper32;
import me.champeau.a4j.math.regression.Ellipse;
import java.util.concurrent.CompletableFuture;

/**
 * Interface defining the main application methods exposed to processing listeners.
 */
public interface JSolExInterface {
    /**
     * Returns the images viewer component.
     * @return the images viewer
     */
    MultipleImagesViewer getImagesViewer();

    /**
     * Returns the main application stage.
     * @return the main stage
     */
    Stage getMainStage();

    /**
     * Returns the logs tab.
     * @return the logs tab
     */
    Tab getLogsTab();

    /**
     * Returns the statistics tab.
     * @return the stats tab
     */
    Tab getStatsTab();

    /**
     * Returns the profile tab.
     * @return the profile tab
     */
    Tab getProfileTab();

    /**
     * Returns the metadata tab.
     * @return the metadata tab
     */
    Tab getMetadataTab();

    /**
     * Returns the redshift tab.
     * @return the redshift tab
     */
    Tab getRedshiftTab();

    /**
     * Returns the images viewer tab.
     * @return the images viewer tab
     */
    Tab getImagesViewerTab();

    /**
     * Returns the main tab pane.
     * @return the tabs
     */
    TabPane getTabs();

    /**
     * Shows the progress indicator.
     */
    void showProgress();

    /**
     * Hides the progress indicator.
     */
    void hideProgress();

    /**
     * Updates the progress indicator.
     * @param progress the progress value between 0.0 and 1.0
     * @param text the progress text to display
     */
    void updateProgress(double progress, String text);

    /**
     * Prepares the UI for script execution.
     * @param executor the script executor
     * @param params the processing parameters
     * @param rootOperation the root progress operation
     * @param sectionKind the section kind being executed
     */
    void prepareForScriptExecution(ImageMathScriptExecutor executor, ProcessParams params, ProgressOperation rootOperation, ImageMathScriptExecutor.SectionKind sectionKind);

    /**
     * Returns the host services for opening URLs in the browser.
     * @return the host services
     */
    HostServices getHostServices();

    /**
     * Starts a new processing session.
     */
    void newSession();

    /**
     * Prepares the UI for redshift image processing.
     * @param processor the redshift images processor
     */
    void prepareForRedshiftImages(RedshiftImagesProcessor processor);

    /**
     * Prepares the UI for GONG image download.
     * @param processParams the processing parameters
     */
    void prepareForGongImageDownload(ProcessParams processParams);

    /**
     * Applies a user-defined rotation to images.
     * @param params the rotation parameters
     */
    void applyUserRotation(ApplyUserRotation params);

    /**
     * Sets the trimming parameters for image processing.
     * @param payload the trimming parameters
     */
    void setTrimmingParameters(TrimmingParameters payload);

    /**
     * Shows the ellipse fitting dialog for interactive ellipse adjustment.
     * @param image the image to fit the ellipse on
     * @param initialEllipse the initial ellipse parameters
     * @return a future that completes with the fitted ellipse
     */
    CompletableFuture<Ellipse> showEllipseFittingDialog(ImageWrapper32 image, Ellipse initialEllipse);

    /**
     * Shows the ellipse fitting dialog for interactive ellipse adjustment with progress information.
     * @param image the image to fit the ellipse on
     * @param initialEllipse the initial ellipse parameters
     * @param fileName the name of the file being processed
     * @param currentFile the index of the current file
     * @param totalFiles the total number of files to process
     * @return a future that completes with the fitted ellipse
     */
    CompletableFuture<Ellipse> showEllipseFittingDialog(ImageWrapper32 image, Ellipse initialEllipse, String fileName, int currentFile, int totalFiles);

    /**
     * Returns the script executor for image math operations.
     * @return the script executor
     */
    ImageMathScriptExecutor getScriptExecutor();

    /**
     * Updates the spectral line indicator in the UI.
     * @param ray the spectral ray to display
     * @param autoDetected whether the spectral line was auto-detected
     */
    void updateSpectralLineIndicator(SpectralRay ray, boolean autoDetected);

    /**
     * Hides the spectral line indicator from the UI.
     */
    void hideSpectralLineIndicator();

}
