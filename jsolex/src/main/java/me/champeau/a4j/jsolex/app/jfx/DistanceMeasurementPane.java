package me.champeau.a4j.jsolex.app.jfx;

import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.stage.FileChooser;
import me.champeau.a4j.jsolex.app.Configuration;
import me.champeau.a4j.jsolex.app.JSolEx;
import me.champeau.a4j.jsolex.processing.util.SolarParameters;
import me.champeau.a4j.math.regression.Ellipse;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static me.champeau.a4j.jsolex.processing.util.FilesUtils.createDirectoriesIfNeeded;

public class DistanceMeasurementPane extends BorderPane {
    public static final int SOLAR_RADIUS_KMS = 696342;
    private final ImageView imageView;
    private final Label distanceLabel;
    private final ObservableList<Measurement> allMeasurements = FXCollections.observableArrayList();
    private Measurement currentMeasurement = new Measurement();
    private final double cx;
    private final double cy;
    private final double radius;
    private final Image image;
    private final SolarParameters solarParams;
    private Path previewPath = null;
    private final Pane overlayLayer;
    private double zoom = 1.0;
    private final Pane container;

    public DistanceMeasurementPane(Image image,
                                   Ellipse ellipse,
                                   SolarParameters solarParams) {
        this.image = image;
        this.solarParams = solarParams;
        imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        overlayLayer = new Pane();
        overlayLayer.setPickOnBounds(false);
        container = new Pane();
        container.getChildren().addAll(imageView, overlayLayer);
        var scrollPane = new ScrollPane(container);
        scrollPane.setPannable(true);
        setCenter(scrollPane);
        var topBar = new VBox();
        topBar.setPadding(new Insets(8));
        topBar.setAlignment(Pos.CENTER);
        var help = new Label(I18N.string(JSolEx.class, "measures", "measure.distance.help"));
        help.setWrapText(true);
        topBar.getChildren().add(help);
        HBox distanceBar = new HBox();
        distanceBar.getChildren().add(new Label("Distance: "));
        distanceLabel = new Label();
        distanceLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        distanceBar.getChildren().add(distanceLabel);
        distanceBar.setAlignment(Pos.CENTER);
        topBar.getChildren().add(distanceBar);
        var clearMeasurementsButton = new Button(I18N.string(JSolEx.class, "measures", "measure.distance.clear"));
        clearMeasurementsButton.setOnMouseClicked(event -> {
            allMeasurements.clear();
            overlayLayer.getChildren().clear();
            currentMeasurement = new Measurement();
            distanceLabel.setText("");
        });
        clearMeasurementsButton.disableProperty().bind(Bindings.size(allMeasurements).isEqualTo(0));
        var saveButton = new Button(I18N.string(JSolEx.class, "measures", "measure.distance.save"));
        saveButton.setOnMouseClicked(event -> saveImage(container));
        var buttonBar = new HBox(clearMeasurementsButton, saveButton);
        buttonBar.setAlignment(Pos.CENTER);
        buttonBar.setPadding(new Insets(8));
        buttonBar.setSpacing(10);
        setBottom(buttonBar);
        setTop(topBar);
        HBox.setHgrow(topBar, javafx.scene.layout.Priority.ALWAYS);
        setupMouseHandlers();
        setupKeyHandlers();
        setFocusTraversable(true);
        this.radius = (ellipse.semiAxis().a() + ellipse.semiAxis().b()) / 2;
        this.cx = ellipse.center().a();
        this.cy = ellipse.center().b();
        imageView.boundsInParentProperty().addListener((obs, oldVal, newVal) -> redrawLines());
        container.setOnScroll(this::handleScroll);
    }

    private void handleScroll(ScrollEvent event) {
        if (!event.isControlDown()) {
            return;
        }
        double zoomFactor = (event.getDeltaY() > 0) ? 1.1 : 0.9;
        zoom *= zoomFactor;
        zoom = Math.max(0.1, Math.min(zoom, 10));
        applyZoom();
        updateCurrentMeasurementPaths();
        event.consume();
    }

    void fitToContainer() {
        double width = image.getWidth();
        double height = image.getHeight();
        double containerWidth = getWidth();
        double containerHeight = getHeight();
        zoom = Math.max(containerWidth/width, containerHeight/height);
        applyZoom();
    }

    private void applyZoom() {
        imageView.setFitWidth(image.getWidth() * zoom);
        imageView.setFitHeight(image.getHeight() * zoom);
        container.setPrefWidth(imageView.getFitWidth());
        container.setPrefHeight(imageView.getFitHeight());
    }

    private void updateCurrentMeasurementPaths() {
        // Remove existing paths for current measurement
        currentMeasurement.drawnPaths.forEach(path -> overlayLayer.getChildren().remove(path));
        currentMeasurement.drawnPaths.clear();
        // Rebuild paths from fixed points
        if (!currentMeasurement.fixedPoints.isEmpty()) {
            for (int i = 1; i < currentMeasurement.fixedPoints.size(); i++) {
                var start = currentMeasurement.fixedPoints.get(i - 1);
                var end = currentMeasurement.fixedPoints.get(i);
                var path = currentMeasurement.isDiskMeasurementMode
                        ? createGeodesicPath(start, end)
                        : createStraightPath(start, end);
                currentMeasurement.drawnPaths.add(path);
                overlayLayer.getChildren().add(path);
            }
            // Update preview path if exists
            if (previewPath != null) {
                overlayLayer.getChildren().remove(previewPath);
                previewPath = null;
            }
            updateDistanceLabel();
        }
    }

    private void saveImage(Pane pane) {
        var fileChooser = new FileChooser();
        fileChooser.setTitle(I18N.string(JSolEx.class, "measures", "measure.distance.save"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG File", "*.png"));
        Configuration.getInstance().findLastOpenDirectory().ifPresent(f -> fileChooser.setInitialDirectory(f.toFile()));
        var outputFile = fileChooser.showSaveDialog(imageView.getScene().getWindow());
        if (outputFile != null) {
            if (!outputFile.getName().endsWith(".png")) {
                outputFile = new File(outputFile.getAbsolutePath() + ".png");
            }
            var params = new SnapshotParameters();
            var imageWithPaths = pane.snapshot(params, null);
            var bufferedImage = SwingFXUtils.fromFXImage(imageWithPaths, null);
            try {
                createDirectoriesIfNeeded(outputFile.getParentFile().toPath());
                ImageIO.write(bufferedImage, "png", outputFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void redrawLines() {
        for (var measurement : allMeasurements) {
            measurement.drawnPaths.forEach(path -> overlayLayer.getChildren().remove(path));
            measurement.drawnPaths.clear();
            if (measurement.fixedPoints.isEmpty()) {
                break;
            }
            for (int i = 1; i < measurement.fixedPoints.size(); i++) {
                var start = measurement.fixedPoints.get(i - 1);
                var end = measurement.fixedPoints.get(i);
                var path = measurement.isDiskMeasurementMode ? createGeodesicPath(start, end) : createStraightPath(start, end);
                measurement.drawnPaths.add(path);
                overlayLayer.getChildren().add(path);
            }
            repositionFinalLabel(measurement);
        }
        if (previewPath != null) {
            overlayLayer.getChildren().remove(previewPath);
            previewPath = null;
        }
    }

    private Path createStraightPath(Point2D start, Point2D end) {
        return buildPath(List.of(start, end));
    }

    private Path createGeodesicPath(Point2D startImg, Point2D endImg) {
        var curvePoints = calculateGeodesicCurve(startImg, endImg);
        return buildPath(curvePoints);
    }

    private List<Point2D> calculateGeodesicCurve(Point2D start, Point2D end) {
        List<Point2D> points = new ArrayList<>();
        var p1 = imageToSphere(start);
        var p2 = imageToSphere(end);
        var rotatedP1 = applyInverseRotation(p1);
        var rotatedP2 = applyInverseRotation(p2);
        for (double t = 0; t <= 1; t += 0.02) {
            var interp = slerp(rotatedP1, rotatedP2, t);
            var observed = applySolarRotation(interp);
            if (observed.z() > 0) {
                points.add(sphereToImage(observed));
            }
        }
        return points;
    }

    private Path buildPath(List<Point2D> imagePoints) {
        var path = new Path();
        path.setStroke(Color.RED);
        path.setStrokeWidth(2);
        if (imagePoints.isEmpty()) return path;
        var first = imageToPane(imagePoints.get(0));
        path.getElements().add(new MoveTo(first.getX(), first.getY()));
        for (int i = 1; i < imagePoints.size(); i++) {
            var p = imageToPane(imagePoints.get(i));
            path.getElements().add(new LineTo(p.getX(), p.getY()));
        }
        return path;
    }

    private Point3D imageToSphere(Point2D p) {
        double x = (p.getX() - cx) / radius;
        double y = (p.getY() - cy) / radius;
        double z = Math.sqrt(1 - x * x - y * y);
        return new Point3D(x, y, z);
    }

    private Point2D sphereToImage(Point3D p) {
        return new Point2D(cx + p.x() * radius, cy + p.y() * radius);
    }

    private Point3D applyInverseRotation(Point3D p) {
        double pAngle = solarParams.p();
        double b0Angle = solarParams.b0();
        double x1 = p.x() * Math.cos(pAngle) - p.y() * Math.sin(pAngle);
        double y1 = p.x() * Math.sin(pAngle) + p.y() * Math.cos(pAngle);
        double z1 = p.z();
        double y2 = y1 * Math.cos(b0Angle) - z1 * Math.sin(b0Angle);
        double z2 = y1 * Math.sin(b0Angle) + z1 * Math.cos(b0Angle);
        return new Point3D(x1, y2, z2);
    }

    private Point3D applySolarRotation(Point3D p) {
        double pAngle = -solarParams.p();
        double b0Angle = -solarParams.b0();
        double y1 = p.y() * Math.cos(b0Angle) - p.z() * Math.sin(b0Angle);
        double z1 = p.y() * Math.sin(b0Angle) + p.z() * Math.cos(b0Angle);
        double x2 = p.x() * Math.cos(pAngle) - y1 * Math.sin(pAngle);
        double y2 = p.x() * Math.sin(pAngle) + y1 * Math.cos(pAngle);
        return new Point3D(x2, y2, z1);
    }

    private Point3D slerp(Point3D start, Point3D end, double t) {
        double dot = start.x() * end.x() + start.y() * end.y() + start.z() * end.z();
        double theta = Math.acos(Math.min(1, Math.max(-1, dot)));
        double sinTheta = Math.sin(theta);
        if (sinTheta < 1e-6) return start;
        double a = Math.sin((1 - t) * theta) / sinTheta;
        double b = Math.sin(t * theta) / sinTheta;
        return new Point3D(a * start.x() + b * end.x(), a * start.y() + b * end.y(), a * start.z() + b * end.z());
    }

    private void setupMouseHandlers() {
        setOnMouseClicked(this::handleMouseClick);
        setOnMouseMoved(this::handleMouseMoved);
    }

    private void setupKeyHandlers() {
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.ESCAPE) {
                if (!currentMeasurement.fixedPoints.isEmpty() && currentMeasurement.fixedPoints.size() > 1) {
                    recordMeasurement();
                    finishDrawing();
                }
                event.consume();
            }
        });
    }

    private void handleMouseClick(MouseEvent event) {
        var clickOnOverlay = overlayLayer.sceneToLocal(event.getSceneX(), event.getSceneY());
        var imageCoords = paneToImage(clickOnOverlay);
        double radiusWithTolerance = radius * 1.01;
        if (currentMeasurement.fixedPoints.isEmpty()) {
            if (event.getClickCount() != 2) {
                return;
            }
            currentMeasurement.isDiskMeasurementMode = imageCoords.distance(cx, cy) <= radiusWithTolerance;
        } else if (currentMeasurement.isDiskMeasurementMode && imageCoords.distance(cx, cy) > radiusWithTolerance ||
                !currentMeasurement.isDiskMeasurementMode && imageCoords.distance(cx, cy) <= radiusWithTolerance) {
            return;
        }
        if (event.getClickCount() == 2 && currentMeasurement.fixedPoints.size() > 1) {
            recordMeasurement();
            finishDrawing();
            return;
        }
        if (!currentMeasurement.fixedPoints.isEmpty()) {
            var last = currentMeasurement.fixedPoints.getLast();
            var path = currentMeasurement.isDiskMeasurementMode ? createGeodesicPath(last, imageCoords)
                    : createStraightPath(last, imageCoords);
            currentMeasurement.drawnPaths.add(path);
            overlayLayer.getChildren().add(path);
        }
        currentMeasurement.fixedPoints.add(imageCoords);
        updateDistanceLabel();
    }

    private void handleMouseMoved(MouseEvent event) {
        if (currentMeasurement.fixedPoints.isEmpty()) {
            return;
        }
        var mousePos = overlayLayer.sceneToLocal(event.getSceneX(), event.getSceneY());
        var current = paneToImage(mousePos);
        removePreview();
        var radiusTolerance = 1.01 * radius;
        if (currentMeasurement.isDiskMeasurementMode && current.distance(cx, cy) > radiusTolerance ||
                !currentMeasurement.isDiskMeasurementMode && current.distance(cx, cy) <= radiusTolerance) {
            return;
        }
        List<Point2D> previewPoints;
        if (currentMeasurement.isDiskMeasurementMode) {
            previewPoints = calculateGeodesicCurve(
                    currentMeasurement.fixedPoints.getLast(),
                    current
            );
        } else {
            previewPoints = List.of(currentMeasurement.fixedPoints.getLast(), current);
        }
        previewPath = buildPath(previewPoints);
        previewPath.setStroke(Color.RED);
        previewPath.getStrokeDashArray().setAll(10.0, 5.0);
        overlayLayer.getChildren().add(previewPath);
    }

    private Point2D paneToImage(Point2D p) {
        double scale = zoom;
        double x = p.getX() / scale;
        double y = p.getY() / scale;
        return new Point2D(x, y);
    }

    private Point2D imageToPane(Point2D p) {
        double scale = zoom;
        double x = p.getX() * scale;
        double y = p.getY() * scale;
        return new Point2D(x, y);
    }

    private void finishDrawing() {
        if (previewPath != null) {
            overlayLayer.getChildren().remove(previewPath);
            previewPath = null;
        }
        if (!currentMeasurement.fixedPoints.isEmpty()) {
            var distanceText = toFormattedDistance();
            currentMeasurement.distanceLabel.setText(distanceText);
            overlayLayer.getChildren().add(currentMeasurement.distanceLabel);
            repositionFinalLabel(currentMeasurement);
            currentMeasurement = new Measurement();
        }
    }

    private String toFormattedDistance() {
        var solarDistanceKms = getSolarDistanceKms();
        solarDistanceKms = Math.round(solarDistanceKms / 50) * 50;
        return String.format("%,d", (long) solarDistanceKms).replaceAll("[^0-9]+", " ") + " km";
    }

    private void repositionFinalLabel(Measurement measurement) {
        if (measurement.completed && !measurement.fixedPoints.isEmpty()) {
            var lastPoint = measurement.fixedPoints.getLast();
            var paneCoords = imageToPane(lastPoint);
            measurement.distanceLabel.setLayoutX(paneCoords.getX() + 10);
            measurement.distanceLabel.setLayoutY(paneCoords.getY() - 10);
        }
    }

    private void updateDistanceLabel() {
        distanceLabel.setText(toFormattedDistance());
    }

    private static double computeZ(Point2D relativeCoords) {
        double x = relativeCoords.getX();
        double y = relativeCoords.getY();
        return Math.sqrt(1 - x * x - y * y);
    }

    private Point2D relativeCoords(Point2D p) {
        return new Point2D((p.getX() - cx) / radius, (p.getY() - cy) / radius);
    }

    private static double angularDistance(Point2D p1, Point2D p2) {
        double x1 = p1.getX();
        double y1 = p1.getY();
        double x2 = p2.getX();
        double y2 = p2.getY();
        return Math.acos(x1 * x2 + y1 * y2 + computeZ(p1) * computeZ(p2));
    }

    private double totalDistance() {
        double total = 0;
        for (int i = 1; i < currentMeasurement.fixedPoints.size(); i++) {
            var p1 = currentMeasurement.fixedPoints.get(i - 1);
            var p2 = currentMeasurement.fixedPoints.get(i);
            if (currentMeasurement.isDiskMeasurementMode) {
                var relP1 = relativeCoords(p1);
                var relP2 = relativeCoords(p2);
                total += angularDistance(relP1, relP2);
            } else {
                double dx = p1.getX() - p2.getX();
                double dy = p1.getY() - p2.getY();
                total += (Math.sqrt(dx * dx + dy * dy) / radius);
            }
        }
        return total;
    }

    public double getSolarDistanceKms() {
        var distanceInSolarRadii = totalDistance();
        return distanceInSolarRadii * (double) SOLAR_RADIUS_KMS;
    }

    private void removePreview() {
        if (previewPath != null) {
            overlayLayer.getChildren().remove(previewPath);
            previewPath = null;
        }
    }

    private void recordMeasurement() {
        currentMeasurement.completed = true;
        distanceLabel.setText("");
        removePreview();
        allMeasurements.add(currentMeasurement);
    }

    private record Point3D(double x, double y, double z) {}

    private static class Measurement {
        private final List<Point2D> fixedPoints = new ArrayList<>();
        private final List<Path> drawnPaths = new ArrayList<>();
        private final Label distanceLabel;
        private boolean completed;
        private boolean isDiskMeasurementMode;
        public Measurement() {
            distanceLabel = new Label();
            distanceLabel.setStyle("-fx-text-fill: red; -fx-font-weight: bold; -fx-background-color: white; -fx-border-color: red; -fx-padding: 2;");
        }
    }
}
