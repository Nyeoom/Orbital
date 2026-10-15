package com.appbrim.ui;

import com.appbrim.api.LineupsClient;
import com.appbrim.model.Favorites;
import com.appbrim.model.Lineup;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;

/** Grid card for one lineup: thumbnail, star, title, start -> end route. */
public final class LineupCard extends VBox {

    public static final double THUMB_W = 224;
    public static final double THUMB_H = 126;

    public interface ClickHandler {
        void onClick(Lineup lineup);
    }

    private final Lineup lineup;
    private final Favorites favorites;
    private final ImageView thumb = new ImageView();
    private final Label star = new Label();
    private final Label placeholder = new Label("loading\u2026");

    public LineupCard(Lineup lineup, LineupsClient client, ExecutorService imageIo,
                      Favorites favorites, boolean showMap, ClickHandler handler) {
        this.lineup = lineup;
        this.favorites = favorites;
        getStyleClass().add("lineup-card");
        setMaxSize(USE_COMPUTED_SIZE, USE_COMPUTED_SIZE);

        thumb.setPreserveRatio(false);
        thumb.setFitWidth(THUMB_W);
        thumb.setFitHeight(THUMB_H);
        thumb.setSmooth(true);
        placeholder.getStyleClass().add("card-meta");

        star.getStyleClass().add("star");
        star.setOnMouseClicked(e -> {
            favorites.toggle(lineup);
            refreshStar();
            e.consume();
        });
        Tooltip.install(star, new Tooltip("Save / unsave this lineup"));
        refreshStar();

        StackPane thumbBox = new StackPane(placeholder, thumb, star);
        thumbBox.getStyleClass().add("thumb-box");
        thumbBox.setMinSize(THUMB_W, THUMB_H);
        thumbBox.setPrefSize(THUMB_W, THUMB_H);
        thumbBox.setMaxSize(THUMB_W, THUMB_H);
        StackPane.setAlignment(star, Pos.TOP_RIGHT);
        StackPane.setMargin(star, new Insets(6));

        Label title = new Label(lineup.getTitle());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label from = new Label(orAnywhere(lineup.getStart()));
        Label arrow = new Label("  \u2192  ");
        arrow.getStyleClass().add("arrow");
        Label to = new Label(orAnywhere(lineup.getEnd()));
        from.getStyleClass().add("card-route");
        to.getStyleClass().add("card-route");
        HBox routeBox = new HBox(2, from, arrow, to);

        String abilities = lineup.getAbilitiesRaw() == null ? "" : lineup.getAbilitiesRaw().replace(":", " / ");
        String meta = (showMap && lineup.getMap() != null ? lineup.getMap() + "  \u00b7  " : "")
                + abilities + "  \u00b7  " + lineup.getNumImages() + " imgs";
        Label metaLabel = new Label(meta);
        metaLabel.getStyleClass().add("card-meta");

        setOnMouseClicked(e -> handler.onClick(lineup));
        Tooltip.install(this, new Tooltip(lineup.getTitle() + "\n"
                + orAnywhere(lineup.getStart()) + " \u2192 " + orAnywhere(lineup.getEnd())));

        getChildren().addAll(thumbBox, title, routeBox, metaLabel);

        imageIo.execute(new Task<Void>() {
            @Override
            protected Void call() {
                BufferedImage img = null;
                // The last frame is the landing shot; fall back to earlier ones if it's missing.
                for (int n = lineup.getNumImages(); n >= 1 && img == null; n--) {
                    String url = LineupsClient.CDN + "/static/lineup_images_thumbnail/" + lineup.getId() + "/" + n + ".webp";
                    try {
                        img = client.fetchImage(url);
                    } catch (Exception ignored) {
                        // Try the previous frame.
                    }
                }
                BufferedImage bi = img;
                Platform.runLater(() -> {
                    if (bi != null) {
                        thumb.setImage(SwingFXUtils.toFXImage(bi, null));
                        placeholder.setVisible(false);
                    } else {
                        placeholder.setText("no preview");
                    }
                });
                return null;
            }
        });
    }

    /** Re-reads the favorite state (call after it may have changed elsewhere). */
    public void refreshStar() {
        boolean on = favorites.contains(lineup.getId());
        star.setText(on ? "\u2605" : "\u2606");
        star.getStyleClass().remove("on");
        if (on) {
            star.getStyleClass().add("on");
        }
    }

    private static String orAnywhere(String s) {
        return s == null || s.isBlank() ? "Anywhere" : s;
    }
}
