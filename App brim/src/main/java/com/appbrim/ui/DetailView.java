package com.appbrim.ui;

import com.appbrim.App;
import com.appbrim.api.LineupsClient;
import com.appbrim.model.LineupDetail;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Full-screen lineup view: big walkthrough image with prev/next, thumbnail strip,
 * numbered steps, meta info and a link to the source page.
 */
public final class DetailView extends VBox {

    private final LineupsClient client;
    private final ExecutorService imageIo;
    private final LineupDetail detail;

    private final ImageView mainImage = new ImageView();
    private final Label imageCounter = new Label();
    private final FlowPane thumbs = new FlowPane(6, 6);
    private final List<Image> images = new ArrayList<>();
    private int current = 0;
    private final List<ImageView> thumbViews = new ArrayList<>();

    public DetailView(LineupDetail detail, LineupsClient client, ExecutorService imageIo, Runnable onBack) {
        this.detail = detail;
        this.client = client;
        this.imageIo = imageIo;

        getStyleClass().add("detail-root");

        Button back = new Button("\u2190  Back");
        back.getStyleClass().add("btn");
        back.getStyleClass().add("ghost");
        back.setOnAction(e -> onBack.run());

        Label title = new Label(detail.getTitle());
        title.getStyleClass().add("detail-title");

        Label route = new Label(detail.getMap() + "   \u00b7   " + detail.getStart() + "  \u2192  " + detail.getEnd());
        route.getStyleClass().add("grid-sub");

        // Left: image + controls
        mainImage.getStyleClass().add("detail-image");
        mainImage.setPreserveRatio(true);
        mainImage.setFitWidth(760);
        Button prev = new Button("\u2190");
        Button next = new Button("\u2192");
        prev.getStyleClass().add("ghost"); next.getStyleClass().add("btn");
        prev.getStyleClass().add("btn"); next.getStyleClass().add("ghost");
        imageCounter.getStyleClass().add("grid-sub");
        HBox nav = new HBox(10, prev, next, imageCounter);
        nav.getStyleClass().add("toolbar");
        nav.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        prev.disableProperty().set(true);

        ScrollPane thumbScroll = new ScrollPane(thumbs);
        thumbScroll.getStyleClass().add("content-scroll");
        thumbScroll.setPrefHeight(96);
        thumbScroll.setFitToHeight(true);
        thumbs.getStyleClass().add("cards-flow");

        VBox imagePane = new VBox(10, mainImage, nav, thumbScroll);
        imagePane.setMinWidth(520);

        // Right: steps + meta
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
            num.setAlignment(javafx.geometry.Pos.CENTER);
            Label text = new Label(s);
            text.getStyleClass().add("step-text");
            text.setMaxWidth(360);
            HBox row = new HBox(10, num, text);
            row.setAlignment(javafx.geometry.Pos.TOP_LEFT);
            stepsPanel.getChildren().add(row);
        }

        Label meta = new Label("\u25b6 " + detail.getViews() + " views   \u2665 " + detail.getLikeCount()
                + "   \u00b7  by " + detail.getUsername());
        meta.getStyleClass().add("grid-sub");
        meta.setWrapText(true);
        meta.setMaxWidth(360);

        Hyperlink open = new Hyperlink("Open on lineupsvalorant.com \u2197");
        open.getStyleClass().add("hyperlink");
        open.setOnAction(e -> App.hostServices().showDocument(
                LineupsClient.SITE + "/?id=" + detail.getLineupId()));

        ScrollPane rightScroll = new ScrollPane(new VBox(12, stepsPanel, meta, open));
        rightScroll.getStyleClass().add("content-scroll");
        rightScroll.setPrefWidth(420);
        rightScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        HBox columns = new HBox(20, imagePane, rightScroll);
        VBox.setVgrow(columns, Priority.ALWAYS);

        getChildren().addAll(back, title, route, columns);

        prev.setOnAction(e -> showImage(current - 1));
        next.setOnAction(e -> showImage(current + 1));

        loadImages();
    }

    private void loadImages() {
        for (int i = 1; i <= detail.getImageCount(); i++) {
            int idx = i;
            imageIo.execute(new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    String url = LineupsClient.CDN + "/static/lineup_images/" + detail.getLineupId() + "/" + idx + ".webp";
                    BufferedImage bi = client.fetchImage(url);
                    Platform.runLater(() -> {
                        Image fx = SwingFXUtils.toFXImage(bi, null);
                        images.add(fx);
                        ImageView tv = new ImageView(fx);
                        tv.setFitWidth(120);
                        tv.setFitHeight(70);
                        tv.setPreserveRatio(false);
                        tv.getStyleClass().add("lineup-thumb");
                        tv.setUserData(images.size() - 1);
                        tv.setOnMouseClicked(e -> showImage((int) tv.getUserData()));
                        thumbViews.add(tv);
                        thumbs.getChildren().add(tv);
                        if (images.size() == 1) {
                            showImage(0);
                        }
                        if (images.size() == detail.getImageCount()) {
                            imageCounter.setText("1 / " + images.size());
                            markThumbSelection();
                        }
                    });
                    return null;
                }
            });
        }
    }

    private void showImage(int i) {
        if (i < 0 || i >= images.size()) {
            return;
        }
        current = i;
        mainImage.setImage(images.get(i));
        imageCounter.setText((i + 1) + " / " + detail.getImageCount());
        markThumbSelection();
    }

    private void markThumbSelection() {
        for (ImageView tv : thumbViews) {
            tv.setOpacity(tv.getUserData().equals(current) ? 1.0 : 0.45);
        }
    }

    /** Split the site's HTML-ish description into individual steps. */
    public static List<String> splitSteps(String description) {
        List<String> out = new ArrayList<>();
        if (description == null || description.isBlank()) {
            out.add("No step description provided for this lineup.");
            return out;
        }
        String cleaned = description.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
                .replace("</p>", "\n").replace("<li>", "\n").replace("</div>", "\n");
        cleaned = cleaned.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ");
        cleaned = cleaned.replaceAll("<[^>]+>", "");
        for (String part : cleaned.split("\n")) {
            String p = part.replaceAll("^\\s*\\d+[.)]\\s*", "").trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        if (out.isEmpty()) {
            out.add(cleaned.trim());
        }
        return out;
    }
}
