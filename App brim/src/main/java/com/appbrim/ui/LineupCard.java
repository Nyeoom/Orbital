package com.appbrim.ui;

import com.appbrim.api.LineupsClient;
import com.appbrim.model.Lineup;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;

/** Grid card for one lineup: thumbnail, title, start -> end route. */
public final class LineupCard extends VBox {

    public interface ClickHandler {
        void onClick(Lineup lineup);
    }

    private final ImageView thumb = new ImageView();

    public LineupCard(Lineup lineup, LineupsClient client, ExecutorService imageIo, ClickHandler handler) {
        getStyleClass().add("lineup-card");
        setMaxSize(USE_COMPUTED_SIZE, USE_COMPUTED_SIZE);

        thumb.getStyleClass().add("lineup-thumb");
        thumb.setPreserveRatio(false);
        thumb.setFitWidth(224);
        thumb.setFitHeight(126);
        thumb.setSmooth(true);

        Label title = new Label(lineup.getTitle());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label arrow = new Label("  \u2192  ");
        arrow.getStyleClass().add("arrow");
        HBox routeBox = new HBox(2);
        Label from = new Label(lineup.getStart() == null ? "Anywhere" : lineup.getStart());
        Label to = new Label(lineup.getEnd() == null ? "Anywhere" : lineup.getEnd());
        from.getStyleClass().add("card-route");
        to.getStyleClass().add("card-route");
        routeBox.getChildren().addAll(from, arrow, to);

        String abilities = lineup.getAbilitiesRaw() == null ? "" : lineup.getAbilitiesRaw().replace(":", " / ");
        Label meta = new Label(abilities + "  \u00b7  " + lineup.getNumImages() + " imgs");
        meta.getStyleClass().add("card-meta");

        setOnMouseClicked(e -> handler.onClick(lineup));
        Tooltip.install(this, new Tooltip(lineup.getTitle() + "\n" + lineup.getStart() + " \u2192 " + lineup.getEnd()));

        VBox.setVgrow(thumb, Priority.ALWAYS);
        getChildren().addAll(thumb, title, routeBox, meta);

        imageIo.execute(new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                BufferedImage img = null;
                for (int n = lineup.getNumImages(); n >= 1 && img == null; n--) {
                    String url = LineupsClient.CDN + "/static/lineup_images_thumbnail/" + lineup.getId() + "/" + n + ".webp";
                    try {
                        img = client.fetchImage(url);
                    } catch (Exception ignored) {
                        // Try the previous frame.
                    }
                }
                if (img != null) {
                    BufferedImage bi = img;
                    javafx.application.Platform.runLater(() -> thumb.setImage(SwingFXUtils.toFXImage(bi, null)));
                }
                return null;
            }
        });
    }
}
