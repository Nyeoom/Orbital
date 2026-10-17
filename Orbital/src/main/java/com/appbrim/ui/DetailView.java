package com.appbrim.ui;

import com.appbrim.App;
import com.appbrim.api.LineupsClient;
import com.appbrim.model.Favorites;
import com.appbrim.model.Lineup;
import com.appbrim.model.LineupDetail;
import com.appbrim.model.StepText;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Full lineup view: walkthrough image with prev/next (buttons, arrow keys, click),
 * thumbnail strip, numbered steps, save-to-favorites, and a link to the source page.
 * Lays out side by side when wide and stacked when narrow (compact mode).
 */
public final class DetailView extends VBox {

    private static final double WIDE_BREAKPOINT = 900;
    private static final double STEPS_WIDTH = 380;
    private static final double ASPECT = 9.0 / 16.0;

    private final Lineup summary;
    private final LineupDetail detail;
    private final LineupsClient client;
    private final ExecutorService imageIo;
    private final Favorites favorites;

    private final int count;
    private final Image[] slots;
    private final boolean[] failed;
    private final ImageView[] thumbViews;
    private int current = 0;

    private final ImageView mainImage = new ImageView();
    private final Label imageStatus = new Label("Loading image\u2026");
    private final Label counter = new Label();
    private final Button prev = new Button("\u2190");
    private final Button next = new Button("\u2192");
    private final Button favBtn = new Button();

    private final VBox imagePane = new VBox(10);
    private final VBox stepsColumn = new VBox(12);
    private final StackPane bodyHost = new StackPane();
    private boolean wideLayout;

    public DetailView(Lineup summary, LineupDetail detail, LineupsClient client, ExecutorService imageIo,
                      Favorites favorites, Runnable onBack) {
        this.summary = summary;
        this.detail = detail;
        this.client = client;
        this.imageIo = imageIo;
        this.favorites = favorites;
        this.count = Math.max(detail.getImageCount(), 0);
        this.slots = new Image[count];
        this.failed = new boolean[count];
        this.thumbViews = new ImageView[count];

        getStyleClass().add("detail-root");
        setFocusTraversable(true);

        // ---- top bar
        Button back = new Button("\u2190  Back");
        back.getStyleClass().addAll("btn", "ghost");
        back.setOnAction(e -> onBack.run());

        favBtn.getStyleClass().addAll("btn", "ghost");
        favBtn.setOnAction(e -> {
            favorites.toggle(summary);
            refreshFavButton();
        });
        refreshFavButton();

        Button copy = new Button("Copy link");
        copy.getStyleClass().addAll("btn", "ghost");
        copy.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(siteLink());
            Clipboard.getSystemClipboard().setContent(cc);
            copy.setText("Copied \u2713");
            PauseTransition t = new PauseTransition(Duration.seconds(1.5));
            t.setOnFinished(ev -> copy.setText("Copy link"));
            t.play();
        });

        Button open = new Button("Open on site \u2197");
        open.getStyleClass().addAll("btn", "ghost");
        open.setOnAction(e -> {
            if (App.hostServices() != null) {
                App.hostServices().showDocument(siteLink());
            }
        });

        HBox topBar = new HBox(10, back, favBtn, copy, open);
        topBar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(detail.getTitle());
        title.getStyleClass().add("detail-title");
        title.setWrapText(true);

        Label route = new Label(nz(detail.getMap()) + "   \u00b7   " + nz(detail.getStart())
                + "  \u2192  " + nz(detail.getEnd()));
        route.getStyleClass().add("grid-sub");
        route.setWrapText(true);

        // ---- image column
        mainImage.getStyleClass().add("detail-image");
        mainImage.setPreserveRatio(true);
        mainImage.setSmooth(true);
        imageStatus.getStyleClass().add("card-meta");
        imageStatus.setWrapText(true);

        StackPane imageBox = new StackPane(imageStatus, mainImage);
        imageBox.getStyleClass().add("thumb-box");
        imageBox.setMinWidth(0);
        imageBox.prefHeightProperty().bind(imagePane.widthProperty().multiply(ASPECT));
        imageBox.minHeightProperty().bind(imageBox.prefHeightProperty());
        imageBox.maxHeightProperty().bind(imageBox.prefHeightProperty());
        mainImage.fitWidthProperty().bind(imagePane.widthProperty());
        mainImage.fitHeightProperty().bind(imagePane.widthProperty().multiply(ASPECT));
        imageBox.setOnMouseClicked(e -> {
            if (failed[current]) {
                loadImage(current);
            } else {
                showImage(current + 1);
            }
        });

        prev.getStyleClass().addAll("btn", "ghost");
        next.getStyleClass().addAll("btn", "ghost");
        counter.getStyleClass().add("grid-sub");
        prev.setOnAction(e -> showImage(current - 1));
        next.setOnAction(e -> showImage(current + 1));
        HBox nav = new HBox(10, prev, next, counter);
        nav.setAlignment(Pos.CENTER_LEFT);

        FlowPane thumbs = new FlowPane(6, 6);
        for (int i = 0; i < count; i++) {
            int idx = i;
            ImageView tv = new ImageView();
            tv.setFitWidth(120);
            tv.setFitHeight(68);
            tv.setPreserveRatio(false);
            tv.getStyleClass().add("lineup-thumb");
            tv.setOnMouseClicked(e -> showImage(idx));
            thumbViews[i] = tv;
            StackPane cell = new StackPane(tv);
            cell.getStyleClass().add("thumb-box");
            cell.setPrefSize(120, 68);
            cell.setMaxSize(120, 68);
            thumbs.getChildren().add(cell);
        }

        imagePane.getChildren().addAll(imageBox, nav, thumbs);
        imagePane.setMinWidth(0);
        imagePane.setPrefWidth(100);
        imagePane.setMaxWidth(Double.MAX_VALUE);

        // ---- steps column
        VBox stepsPanel = new VBox(10);
        stepsPanel.getStyleClass().add("steps-panel");
        Label stepsTitle = new Label("STEPS");
        stepsTitle.getStyleClass().add("section-label");
        stepsPanel.getChildren().add(stepsTitle);
        List<String> steps = splitSteps(detail.getDescription());
        int n = 1;
        for (String s : steps) {
            Label num = new Label(String.valueOf(n++));
            num.getStyleClass().add("step-num");
            num.setMinSize(22, 22);
            num.setAlignment(Pos.CENTER);
            Label text = new Label(s);
            text.getStyleClass().add("step-text");
            text.setWrapText(true);
            text.setMinHeight(Region.USE_PREF_SIZE);
            HBox.setHgrow(text, Priority.ALWAYS);
            text.setMaxWidth(Double.MAX_VALUE);
            HBox row = new HBox(10, num, text);
            row.setAlignment(Pos.TOP_LEFT);
            stepsPanel.getChildren().add(row);
        }
        Label meta = new Label("\u25b6 " + detail.getViews() + " views   \u2665 " + detail.getLikeCount()
                + "   \u00b7  by " + nz(detail.getUsername()));
        meta.getStyleClass().add("grid-sub");
        meta.setWrapText(true);
        stepsColumn.getChildren().addAll(stepsPanel, meta);

        // ---- responsive body
        ScrollPane bodyScroll = new ScrollPane(bodyHost);
        bodyScroll.getStyleClass().add("content-scroll");
        bodyScroll.setFitToWidth(true);
        bodyScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(bodyScroll, Priority.ALWAYS);
        bodyScroll.widthProperty().addListener((o, a, w) -> relayout(w.doubleValue()));
        relayout(1000);

        getChildren().addAll(topBar, title, route, bodyScroll);

        // Arrow keys / Esc work wherever focus is inside this view (filter: beats button traversal).
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.LEFT) {
                showImage(current - 1);
                e.consume();
            } else if (e.getCode() == KeyCode.RIGHT) {
                showImage(current + 1);
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                onBack.run();
                e.consume();
            }
        });
        sceneProperty().addListener((o, a, scene) -> {
            if (scene != null) {
                Platform.runLater(this::requestFocus);
            }
        });

        if (count == 0) {
            imageStatus.setText("This lineup has no images.");
            prev.setDisable(true);
            next.setDisable(true);
        } else {
            for (int i = 0; i < count; i++) {
                loadImage(i);
            }
        }
        updateNav();
    }

    private void relayout(double width) {
        boolean wide = width >= WIDE_BREAKPOINT;
        if (bodyHost.getChildren().size() == 1 && wide == wideLayout) {
            return;
        }
        wideLayout = wide;
        if (wide) {
            HBox h = new HBox(20);
            stepsColumn.setMinWidth(STEPS_WIDTH);
            stepsColumn.setPrefWidth(STEPS_WIDTH);
            stepsColumn.setMaxWidth(STEPS_WIDTH);
            HBox.setHgrow(imagePane, Priority.ALWAYS);
            h.getChildren().addAll(imagePane, stepsColumn);
            h.setAlignment(Pos.TOP_LEFT);
            bodyHost.getChildren().setAll(h);
        } else {
            VBox v = new VBox(14);
            stepsColumn.setMinWidth(0);
            stepsColumn.setPrefWidth(Region.USE_COMPUTED_SIZE);
            stepsColumn.setMaxWidth(Double.MAX_VALUE);
            v.getChildren().addAll(imagePane, stepsColumn);
            bodyHost.getChildren().setAll(v);
        }
    }

    private void loadImage(int idx) {
        failed[idx] = false;
        if (idx == current) {
            imageStatus.setText("Loading image\u2026");
            imageStatus.setVisible(true);
        }
        imageIo.execute(new Task<Void>() {
            @Override
            protected Void call() {
                String url = LineupsClient.CDN + "/static/lineup_images/" + detail.getLineupId() + "/" + (idx + 1) + ".webp";
                try {
                    BufferedImage bi = client.fetchImage(url);
                    Image fx = SwingFXUtils.toFXImage(bi, null);
                    Platform.runLater(() -> {
                        slots[idx] = fx;
                        thumbViews[idx].setImage(fx);
                        if (idx == current) {
                            showImage(current);
                        }
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        failed[idx] = true;
                        if (idx == current) {
                            showImage(current);
                        }
                    });
                }
                return null;
            }
        });
    }

    private void showImage(int i) {
        if (i < 0 || i >= count) {
            return;
        }
        current = i;
        Image img = slots[i];
        mainImage.setImage(img);
        if (img != null) {
            imageStatus.setVisible(false);
        } else {
            imageStatus.setVisible(true);
            imageStatus.setText(failed[i] ? "Couldn't load this image \u2014 click to retry" : "Loading image\u2026");
        }
        updateNav();
        for (int k = 0; k < count; k++) {
            thumbViews[k].setOpacity(k == current ? 1.0 : 0.45);
        }
    }

    private void updateNav() {
        counter.setText(count == 0 ? "" : (current + 1) + " / " + count);
        prev.setDisable(current <= 0);
        next.setDisable(current >= count - 1);
    }

    private void refreshFavButton() {
        favBtn.setText(favorites.contains(summary.getId()) ? "\u2605  Saved" : "\u2606  Save");
    }

    private String siteLink() {
        return LineupsClient.SITE + "/?id=" + detail.getLineupId();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Split the site's HTML-ish description into individual steps. */
    public static List<String> splitSteps(String description) {
        return StepText.split(description);
    }
}
