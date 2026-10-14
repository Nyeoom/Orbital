package com.appbrim.ui;

import com.appbrim.detect.ScreenAnalyzer;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;

/**
 * One-time calibration: shows a frozen full-screen screenshot and asks the user
 * to drag a rectangle around Valorant's full-map overlay (the map shown while M
 * is held). Returns the rectangle in screen coordinates, or null on cancel.
 */
public final class CalibrationDialog {

    /** Shows the dialog; blocks until done. Returns screen-space rect or null. */
    public static java.awt.Rectangle show(ScreenAnalyzer analyzer) {
        BufferedImage shot;
        try {
            shot = analyzer.captureScreen();
        } catch (Exception e) {
            return null;
        }

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Calibrate - drag over the in-game full map (M)");

        ImageView view = new ImageView(SwingFXUtils.toFXImage(shot, null));
        double scale = Math.min(1100.0 / shot.getWidth(), 640.0 / shot.getHeight());
        view.setFitWidth(shot.getWidth() * scale);
        view.setFitHeight(shot.getHeight() * scale);
        view.setSmooth(true);

        Rectangle sel = new Rectangle(0, 0, 0, 0);
        sel.setStroke(Color.web("#ff4655"));
        sel.setStrokeWidth(2);
        sel.getStrokeDashArray().addAll(6.0, 6.0);
        sel.setFill(Color.rgb(255, 70, 85, 0.08));
        sel.setVisible(false);

        final double[] start = new double[2];
        final Rectangle2D[] chosen = new Rectangle2D[1];

        view.setOnMousePressed(e -> {
            start[0] = e.getX();
            start[1] = e.getY();
            sel.setVisible(true);
        });
        view.setOnMouseDragged(e -> {
            double x = Math.min(start[0], e.getX());
            double y = Math.min(start[1], e.getY());
            sel.setX(x);
            sel.setY(y);
            sel.setWidth(Math.abs(e.getX() - start[0]));
            sel.setHeight(Math.abs(e.getY() - start[1]));
        });
        view.setOnMouseReleased(e -> {
            if (sel.getWidth() > 20 && sel.getHeight() > 20) {
                chosen[0] = new Rectangle2D(sel.getX() / scale, sel.getY() / scale,
                        sel.getWidth() / scale, sel.getHeight() / scale);
            }
        });

        Label hint = new Label("Open Valorant, hold M so the full map is showing, then come back and drag a box over the map. (Screenshot is frozen.)");
        hint.getStyleClass().add("calib-hint");
        hint.setWrapText(true);
        hint.setMaxWidth(560);

        Button ok = new Button("Save");
        ok.getStyleClass().add("btn");
        ok.setDisable(true);
        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("btn");
        cancel.getStyleClass().add("ghost");

        view.setOnMouseReleased(new javafx.event.EventHandler<javafx.scene.input.MouseEvent>() {
            @Override
            public void handle(javafx.scene.input.MouseEvent e) {
                if (chosen[0] != null) {
                    ok.setDisable(false);
                }
            }
        });

        Pane overlay = new Pane(view, sel);
        Group group = new Group(overlay);

        BorderPane root = new BorderPane(group);
        HBox bottom = new HBox(16, hint, ok, cancel);
        bottom.setStyle("-fx-padding: 12 16 12 16; -fx-alignment: center-left;");
        root.setBottom(bottom);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(CalibrationDialog.class.getResource("/theme.css").toExternalForm());
        stage.setScene(scene);

        final java.awt.Rectangle[] result = new java.awt.Rectangle[1];
        ok.setOnAction(e -> {
            Rectangle2D r = chosen[0];
            result[0] = new java.awt.Rectangle((int) r.getMinX(), (int) r.getMinY(),
                    (int) r.getWidth(), (int) r.getHeight());
            stage.close();
        });
        cancel.setOnAction(e -> stage.close());

        stage.showAndWait();
        return result[0];
    }
}
