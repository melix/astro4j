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

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

class RemoteSubmissionsTimelineView extends Canvas {
    private static final double WIDTH = 520;
    private static final double HEIGHT = 56;
    private static final double LEFT_PAD = 6;
    private static final double RIGHT_PAD = 6;
    private static final double TOP_PAD = 6;
    private static final double BOTTOM_PAD = 14;
    private static final double MARKER_HIT_RADIUS = 6;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private record MarkerEntry(double x, boolean userMarker, String tooltipText) {
    }

    private final List<MarkerEntry> markers = new ArrayList<>();
    private final Tooltip tooltip = new Tooltip();
    private MarkerEntry hoveredMarker;

    RemoteSubmissionsTimelineView() {
        super(WIDTH, HEIGHT);
        tooltip.setShowDelay(Duration.millis(100));
        Tooltip.install(this, tooltip);
        tooltip.setOnShowing(e -> {
            if (hoveredMarker == null) {
                e.consume();
            }
        });
        setOnMouseMoved(e -> updateHover(e.getX(), e.getY()));
        setOnMouseExited(e -> {
            hoveredMarker = null;
            tooltip.hide();
        });
    }

    void update(List<Bass2000FtpListingService.RemoteSubmission> submissions, LocalDateTime userCaptureUtc) {
        markers.clear();
        var drawableWidth = WIDTH - LEFT_PAD - RIGHT_PAD;
        for (var s : submissions) {
            var x = LEFT_PAD + drawableWidth * fractionOfDay(s.captureTime().toLocalTime());
            var label = s.filename() + "\n" + s.captureTime().toLocalTime().format(TIME_FORMATTER) + " UTC";
            markers.add(new MarkerEntry(x, false, label));
        }
        if (userCaptureUtc != null) {
            var x = LEFT_PAD + drawableWidth * fractionOfDay(userCaptureUtc.toLocalTime());
            markers.add(new MarkerEntry(x, true, "Your capture\n" + userCaptureUtc.toLocalTime().format(TIME_FORMATTER) + " UTC"));
        }
        redraw();
    }

    private void redraw() {
        var gc = getGraphicsContext2D();
        gc.clearRect(0, 0, WIDTH, HEIGHT);
        drawBackground(gc);
        drawHourTicks(gc);
        drawMarkers(gc);
    }

    private void drawBackground(GraphicsContext gc) {
        gc.setFill(Color.web("#f8f9fa"));
        gc.fillRect(LEFT_PAD, TOP_PAD, WIDTH - LEFT_PAD - RIGHT_PAD, HEIGHT - TOP_PAD - BOTTOM_PAD);
        gc.setStroke(Color.web("#dee2e6"));
        gc.setLineWidth(1);
        gc.strokeRect(LEFT_PAD, TOP_PAD, WIDTH - LEFT_PAD - RIGHT_PAD, HEIGHT - TOP_PAD - BOTTOM_PAD);
    }

    private void drawHourTicks(GraphicsContext gc) {
        var drawableWidth = WIDTH - LEFT_PAD - RIGHT_PAD;
        gc.setFill(Color.web("#6c757d"));
        gc.setFont(Font.font(9));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setStroke(Color.web("#ced4da"));
        gc.setLineWidth(1);
        for (var hour = 0; hour <= 24; hour++) {
            var x = LEFT_PAD + drawableWidth * (hour / 24.0);
            if (hour % 6 == 0) {
                gc.strokeLine(x, TOP_PAD, x, HEIGHT - BOTTOM_PAD);
                gc.fillText(String.format("%02d", hour), x, HEIGHT - 2);
            } else if (hour % 3 == 0) {
                gc.strokeLine(x, HEIGHT - BOTTOM_PAD - 4, x, HEIGHT - BOTTOM_PAD);
            }
        }
    }

    private void drawMarkers(GraphicsContext gc) {
        for (var marker : markers) {
            if (marker.userMarker) {
                continue;
            }
            gc.setStroke(Color.web("#0d6efd"));
            gc.setLineWidth(2);
            gc.strokeLine(marker.x, TOP_PAD + 2, marker.x, HEIGHT - BOTTOM_PAD - 2);
        }
        for (var marker : markers) {
            if (!marker.userMarker) {
                continue;
            }
            gc.setStroke(Color.web("#dc3545"));
            gc.setLineWidth(3);
            gc.strokeLine(marker.x, TOP_PAD - 2, marker.x, HEIGHT - BOTTOM_PAD + 2);
            gc.setFill(Color.web("#dc3545"));
            gc.fillPolygon(
                    new double[]{marker.x - 4, marker.x + 4, marker.x},
                    new double[]{TOP_PAD - 6, TOP_PAD - 6, TOP_PAD - 1},
                    3
            );
        }
    }

    private void updateHover(double mouseX, double mouseY) {
        MarkerEntry closest = null;
        double bestDx = MARKER_HIT_RADIUS + 1;
        for (var marker : markers) {
            var dx = Math.abs(marker.x - mouseX);
            if (dx <= MARKER_HIT_RADIUS && dx < bestDx) {
                closest = marker;
                bestDx = dx;
            }
        }
        if (closest != hoveredMarker) {
            hoveredMarker = closest;
            if (hoveredMarker == null) {
                tooltip.hide();
            } else {
                tooltip.setText(hoveredMarker.tooltipText);
            }
        }
    }

    private static double fractionOfDay(LocalTime t) {
        return (t.toSecondOfDay()) / (double) (24 * 3600);
    }
}
