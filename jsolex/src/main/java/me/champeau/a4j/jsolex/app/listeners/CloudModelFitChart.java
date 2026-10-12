/*
 * Copyright 2026 the original author or authors.
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

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import me.champeau.a4j.jsolex.app.jfx.CustomTooltip;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudInversionResult;
import me.champeau.a4j.jsolex.processing.spectrum.cloud.CloudParameters;
import me.champeau.a4j.jsolex.processing.util.HeliographicProjection;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static me.champeau.a4j.jsolex.app.JSolEx.message;

/**
 * Builds the chart showing the cloud model fit at one position, in the same way as figure 4 of Chae et al. (2026),
 * and the panels showing the values of the cloud model.
 */
final class CloudModelFitChart {
    private static final String UNDEFINED = "—";
    private static final double GROUP_TITLE_WIDTH = 110;
    private static final String GROUP_TITLE_STYLE = "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #444444;";
    private static final String REFERENCE_TEMPERATURE = "7300 K";
    private static final String REFERENCE_COLUMN_MASS = "2.7 × 10⁻⁵ g/cm²";
    private static final String REFERENCE_MASS_FLUX_VELOCITY = "6.8 km/s";
    private static final String REFERENCE_KINETIC_ENERGY_VELOCITY = "14 km/s";
    private static final String REFERENCE_KINETIC_ENERGY_FLUX_VELOCITY = "12 km/s";
    private static final String REFERENCE_KINETIC_ENERGY_FLUX = "1.9 × 10⁵ erg/cm²/s";
    private static final String SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹⁻";

    private CloudModelFitChart() {
    }

    static LineChart<Number, Number> create(CloudInversionResult.PixelFit fit) {
        var xAxis = new NumberAxis();
        var yAxis = new NumberAxis();
        xAxis.setLabel(message("cloud.fit.wavelength"));
        yAxis.setLabel(message("cloud.fit.intensity"));
        xAxis.setForceZeroInRange(false);
        yAxis.setForceZeroInRange(false);
        var chart = new LineChart<>(xAxis, yAxis);
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.getData().add(series(message("cloud.fit.observed"), fit.offsets(), fit.observed()));
        chart.getData().add(series(message("cloud.fit.model"), fit.offsets(), fit.fitted()));
        chart.getData().add(series(message("cloud.fit.background"), fit.offsets(), fit.background()));
        chart.getData().add(series(message("cloud.fit.reference"), fit.offsets(), fit.reference()));
        return chart;
    }

    /**
     * Builds the panel showing the values of the fit, grouped by theme, in the style of the
     * statistics of the spectral profiles.
     *
     * @param fit the fit
     * @param position the heliographic coordinates of the position, if known
     * @return the panel
     */
    static Node summary(CloudInversionResult.PixelFit fit, Optional<HeliographicProjection.Coordinates> position) {
        var title = new Label(position.map(CloudModelFitChart::formatPosition).orElse(""));
        title.setFont(Font.font(title.getFont().getFamily(), FontWeight.BOLD, 12));
        if (position.isPresent()) {
            withTooltip(title, "cloud.fit.position");
        }
        var panel = new VBox(8, title);
        panel.setPadding(new Insets(10));
        panel.setStyle(SingleModeProcessingEventListener.STATISTICS_PANEL_STYLE);
        note(fit).ifPresent(text -> {
            var note = new Label(text);
            note.setWrapText(true);
            note.setMinHeight(Region.USE_PREF_SIZE);
            note.setStyle(SingleModeProcessingEventListener.WARNING_STYLE);
            panel.getChildren().add(note);
        });
        var p = fit.parameters();
        var q = fit.quantities();
        if (q != null) {
            var detected = fit.isDetected();
            var measured = fit.isTemperatureMeasured();
            panel.getChildren().add(group("cloud.fit.group.velocities",
                    item("cloud.fit.vm", detected, "%.1f km/s", q.massFluxVelocity()),
                    item("cloud.fit.vmax", detected, "%.1f km/s", fit.fastestVelocity()),
                    item("cloud.fit.ve", detected, "%.1f km/s", q.kineticEnergyVelocity()),
                    item("cloud.fit.vf", detected, "%.1f km/s", q.kineticEnergyFluxVelocity())));
            panel.getChildren().add(group("cloud.fit.group.cloud",
                    item("cloud.fit.temperature", measured, "%.0f K", q.temperature()),
                    scientificItem("cloud.fit.mass", measured, "g/cm²", q.columnMass()),
                    item("cloud.fit.source", detected, "%.3f", p.sourceFunction()),
                    item("cloud.fit.width", detected, "%.3f Å", p.dopplerWidth())));
            var components = new ArrayList<Node>();
            addComponent(components, p, CloudParameters.BLUE, "b");
            addComponent(components, p, CloudParameters.CENTER, "c");
            addComponent(components, p, CloudParameters.RED, "r");
            panel.getChildren().add(group("cloud.fit.group.components", components.toArray(Node[]::new)));
        }
        panel.getChildren().add(group("cloud.fit.group.background",
                item("cloud.fit.background.brightness", true, "×%.3f", p.backgroundScale()),
                item("cloud.fit.background.narrowing", true, "%.3f", p.backgroundWidth())));
        panel.getChildren().add(group("cloud.fit.group.fit",
                item("cloud.fit.chi2", true, "%.2f", fit.reducedChi2()),
                item("cloud.fit.snr", q != null, "%.1f", fit.detection())));
        return panel;
    }

    /**
     * Builds the panel showing the calibration of an inversion and the averages over the disk.
     *
     * @param result the result of the inversion
     * @return the panel
     */
    static Node inversionSummary(CloudInversionResult result) {
        var calibration = result.calibration();
        var stats = result.statistics();
        var panel = new VBox(8,
                group("cloud.summary.group.calibration",
                        item("cloud.summary.fwhm", "%.3f Å", calibration.instrumentFwhm()),
                        item("cloud.summary.stray", "%.1f %%", 100 * calibration.strayLight()),
                        item("cloud.summary.dispersion", "×%.3f", calibration.dispersionScale()),
                        item("cloud.summary.offset", "%.3f Å", calibration.offset()),
                        item("cloud.summary.telluric", "×%.1f", calibration.telluricExponent()),
                        item("cloud.summary.residuals", "%.2f %%", 100 * calibration.rmsResidual())),
                group("cloud.summary.group.averages",
                        withReference(item("cloud.summary.temperature", "%.0f K", stats.meanTemperature()), REFERENCE_TEMPERATURE),
                        withReference(scientificItem("cloud.summary.mass", Double.isFinite(stats.meanColumnMass()), "g/cm²", stats.meanColumnMass()), REFERENCE_COLUMN_MASS),
                        withReference(item("cloud.summary.vm", "%.1f km/s", stats.meanAbsoluteMassFluxVelocity()), REFERENCE_MASS_FLUX_VELOCITY),
                        withReference(item("cloud.summary.ve", "%.1f km/s", stats.meanKineticEnergyVelocity()), REFERENCE_KINETIC_ENERGY_VELOCITY),
                        withReference(item("cloud.summary.vf", "%.1f km/s", stats.meanAbsoluteKineticEnergyFluxVelocity()), REFERENCE_KINETIC_ENERGY_FLUX_VELOCITY),
                        withReference(scientificItem("cloud.summary.energy.flux", Double.isFinite(stats.kineticEnergyFluxEstimate()), "erg/cm²/s", stats.kineticEnergyFluxEstimate()),
                                REFERENCE_KINETIC_ENERGY_FLUX)));
        panel.setPadding(new Insets(10));
        panel.setStyle(SingleModeProcessingEventListener.STATISTICS_PANEL_STYLE);
        return panel;
    }

    private static void addComponent(List<Node> components, CloudParameters p, int component, String name) {
        if (p.opacities()[component] > 0) {
            components.add(withTooltip(SingleModeProcessingEventListener.createStatItem(message("cloud.fit.component." + name),
                    String.format(Locale.US, message("cloud.fit.component"), p.opacities()[component], p.velocities()[component])), "cloud.fit.component"));
        }
    }

    private static Optional<String> note(CloudInversionResult.PixelFit fit) {
        if (fit.rejected()) {
            return Optional.of(message("cloud.fit.rejected"));
        }
        if (fit.brighter()) {
            return Optional.of(message("cloud.fit.brighter"));
        }
        if (fit.quantities() == null) {
            return Optional.of(message("cloud.fit.none"));
        }
        if (!fit.isDetected()) {
            return Optional.of(String.format(Locale.US, message("cloud.fit.weak"), fit.detection(), CloudInversionResult.MIN_DETECTION));
        }
        return Optional.empty();
    }

    private static String formatPosition(HeliographicProjection.Coordinates coordinates) {
        var latitude = coordinates.latitude();
        var longitude = coordinates.longitude();
        return String.format(Locale.US, message("cloud.fit.position"),
                Math.abs(latitude), message(latitude >= 0 ? "cloud.fit.north" : "cloud.fit.south"),
                Math.abs(longitude), message(longitude >= 0 ? "cloud.fit.west" : "cloud.fit.east"));
    }

    private static VBox item(String key, String format, double value) {
        return item(key, Double.isFinite(value), format, value);
    }

    private static VBox item(String key, boolean defined, String format, double value) {
        return textItem(key, defined ? String.format(Locale.US, format, value) : UNDEFINED);
    }

    private static VBox scientificItem(String key, boolean defined, String unit, double value) {
        return textItem(key, defined ? scientific(value) + " " + unit : UNDEFINED);
    }

    private static VBox withReference(VBox item, String reference) {
        var label = new Label(String.format(message("cloud.summary.reference"), reference));
        label.getStyleClass().add("help-text");
        return new VBox(item, withTooltip(label, "cloud.summary.reference"));
    }

    private static VBox textItem(String key, String text) {
        return withTooltip(SingleModeProcessingEventListener.createStatItem(message(key), text), key);
    }

    private static String scientific(double value) {
        if (value == 0) {
            return "0";
        }
        var exponent = (int) Math.floor(Math.log10(Math.abs(value)));
        var mantissa = value / Math.pow(10, exponent);
        if (Math.abs(mantissa) >= 9.995) {
            exponent++;
            mantissa /= 10;
        }
        var digits = new StringBuilder();
        for (var c : Integer.toString(exponent).toCharArray()) {
            digits.append(SUPERSCRIPTS.charAt(c == '-' ? 10 : c - '0'));
        }
        return String.format(Locale.US, "%.2f × 10%s", mantissa, digits);
    }

    private static <T extends Node> T withTooltip(T node, String key) {
        new CustomTooltip(message(key + ".tooltip")).attachTo(node);
        return node;
    }

    private static Node group(String key, Node... items) {
        var title = new Label(message(key));
        title.setMinWidth(GROUP_TITLE_WIDTH);
        title.setPrefWidth(GROUP_TITLE_WIDTH);
        title.setStyle(GROUP_TITLE_STYLE);
        title.setWrapText(true);
        var flow = new FlowPane(20, 6, items);
        HBox.setHgrow(flow, Priority.ALWAYS);
        var row = new HBox(10, title, flow);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    static void writeCsv(CloudInversionResult.PixelFit fit, PrintWriter writer) {
        writer.println("offset_angstroms;observed;model;background;reference");
        for (var i = 0; i < fit.offsets().length; i++) {
            writer.printf(Locale.US, "%.4f;%.6f;%.6f;%.6f;%.6f%n", fit.offsets()[i], fit.observed()[i], fit.fitted()[i], fit.background()[i], fit.reference()[i]);
        }
        var p = fit.parameters();
        var q = fit.quantities();
        writer.println();
        writer.println("parameter;value");
        writer.printf(Locale.US, "background_scale;%.5f%n", p.backgroundScale());
        writer.printf(Locale.US, "background_width;%.5f%n", p.backgroundWidth());
        writer.printf(Locale.US, "components;%d%n", p.components());
        writer.printf(Locale.US, "reduced_chi2;%.4f%n", fit.reducedChi2());
        writer.printf(Locale.US, "detection;%.2f%n", fit.detection());
        if (q == null) {
            return;
        }
        writer.printf(Locale.US, "source_function;%.5f%n", p.sourceFunction());
        writer.printf(Locale.US, "doppler_width_angstroms;%.5f%n", p.dopplerWidth());
        writer.printf(Locale.US, "tau_blue;%.5f%ntau_center;%.5f%ntau_red;%.5f%n",
                p.opacities()[CloudParameters.BLUE], p.opacities()[CloudParameters.CENTER], p.opacities()[CloudParameters.RED]);
        writer.printf(Locale.US, "v_blue_kms;%.3f%nv_center_kms;%.3f%nv_red_kms;%.3f%n",
                p.velocities()[CloudParameters.BLUE], p.velocities()[CloudParameters.CENTER], p.velocities()[CloudParameters.RED]);
        writer.printf(Locale.US, "temperature_k;%.1f%n", q.temperature());
        writer.printf(Locale.US, "column_mass_g_cm2;%.4e%n", q.columnMass());
        writer.printf(Locale.US, "v_m_kms;%.3f%nv_e_kms;%.3f%nv_f_kms;%.3f%n", q.massFluxVelocity(), q.kineticEnergyVelocity(), q.kineticEnergyFluxVelocity());
    }

    private static XYChart.Series<Number, Number> series(String name, double[] x, double[] y) {
        var series = new XYChart.Series<Number, Number>();
        series.setName(name);
        for (var i = 0; i < x.length; i++) {
            series.getData().add(new XYChart.Data<>(x[i], y[i]));
        }
        return series;
    }
}
