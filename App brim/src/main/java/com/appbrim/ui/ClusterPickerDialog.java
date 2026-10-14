package com.appbrim.ui;

import com.appbrim.detect.ScreenAnalyzer;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Shows a captured screenshot with the detected markers as clickable pins.
 * Used to confirm which red blob is the planted spike (and which white blob is
 * the player) whenever pixel classification finds more than one candidate.
 */
public final class ClusterPickerDialog {

    /**
     * Blocks until the user picks a marker. Returns null on skip/cancel.
     *
     * @param allowSkip adds a "Skip" button (used for optional player picking)
     */
    public static ScreenAnalyzer.Marker show(BufferedImage image, List<ScreenAnalyzer.Marker> markers,
                                             String title, boolean allowSkip) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(title);

        ImageView view = new ImageView(SwingFXUtils.toFXImage(image, null));
        double scale = Math.min(760.0 / image.getWidth(), 560.0 / image.getHeight());
        view.setFitWidth(image.getWidth() * scale);
        view.setFitHeight(image.getHeight() * scale);
        view.setSmooth(true);

        final ScreenAnalyzer.Marker[] picked = new ScreenAnalyzer.Marker[1];

        Pane pinLayer = new Pane(view);
        for (ScreenAnalyzer.Marker m : markers) {
            double cx = m.bounds().getCenterX() * scale;
            double cy = m.bounds().getCenterY() * scale;
            Region pin = new Region();
            pin.getStyleClass().addAll("marker-pin", m.red() ? "red" : "white");
            pin.setLayoutX(cx - 7);
            pin.setLayoutY(cy - 7);
            pin.setOnMouseClicked(e -> {
                picked[0] = m;
                stage.close();
            });
            Label tag = new Label(String.valueOf(markers.indexOf(m) + 1));
            tag.setStyle("-fx-text-fill: white; -fx-font-size: 10px;");
            tag.setMouseTransparent(true);
            tag.setLayoutX(cx - 3);
            tag.setLayoutY(cy - 18);
            pinLayer.getChildren().addAll(pin, tag);
        }

        Label titleL = new Label(title);
        titleL.getStyleClass().add("calib-title");
        Label hint = new Label("Click the matching marker on the captured map." +
                (allowSkip ? " Skip if none of these is right." : ""));
        hint.getStyleClass().add("calib-hint");

        HBox buttons = new HBox(10);
        if (allowSkip) {
            Button skip = new Button("Skip");
            skip.getStyleClass().addAll("btn", "ghost");
            skip.setOnAction(e -> stage.close());
            buttons.getChildren().add(skip);
        }

        BorderPane root = new BorderPane(pinLayer);
        HBox top = new HBox(16, titleL, hint);
        top.setStyle("-fx-padding: 12 16 12 16; -fx-alignment: center-left;");
        root.setTop(top);
        buttons.setStyle("-fx-padding: 12 16 12 16;");
        root.setBottom(buttons);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(ClusterPickerDialog.class.getResource("/theme.css").toExternalForm());
        stage.setScene(scene);
        stage.showAndWait();
        return picked[0];
    }
}
