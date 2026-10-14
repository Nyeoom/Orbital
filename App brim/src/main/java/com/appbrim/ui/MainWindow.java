package com.appbrim.ui;

import com.appbrim.Prefs;
import com.appbrim.api.LineupsClient;
import com.appbrim.detect.MapCodenames;
import com.appbrim.detect.ScreenAnalyzer;
import com.appbrim.model.GameState;
import com.appbrim.model.Lineup;
import com.appbrim.model.LineupDetail;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** The whole main window: sidebar, toolbar, lineup grid, detail view, toasts. */
public final class MainWindow {

    public static final List<String> MAPS = List.of(
            "Ascent", "Bind", "Breeze", "Corrode", "Fracture", "Haven", "Icebox",
            "Lotus", "Pearl", "Split", "Sunset", "Abyss", "Summit");

    private final GameState state;
    private final LineupsClient client;
    private final ScreenAnalyzer analyzer;
    private final ExecutorService io;
    private final ExecutorService imageIo;

    private Stage stage;
    private final Map<String, Button> mapButtons = new LinkedHashMap<>();
    private Label gameV, phaseV, mapV, spikeV, youV;
    private Label gridTitle, gridSub;
    private FlowPane cardsFlow;
    private StackPane contentStack;
    private ScrollPane gridScroll;
    private VBox emptyState;
    private HBox confirmBar;
    private Label toastLabel;
    private javafx.animation.PauseTransition toastHide;
    private javafx.animation.PauseTransition searchDebounce;

    // Search filters
    private String abilityMode = "molly";         // molly | smoke | both
    private int side = LineupsClient.SIDE_ANY;
    private String manualSite;                    // "A"/"B"/"C" or null (= follow detection)
    private boolean fromMyPosition = true;
    private boolean relaxedStart;

    private String lastSearchedMap, lastSearchedEnd, lastSearchedStart, lastSearchedAbilities, lastSideKey;

    private final AtomicBoolean detectBusy = new AtomicBoolean(false);

    public MainWindow(GameState state, LineupsClient client, ScreenAnalyzer analyzer,
                      ExecutorService io, ExecutorService imageIo) {
        this.state = state;
        this.client = client;
        this.analyzer = analyzer;
        this.io = io;
        this.imageIo = imageIo;
        state.addListener(s -> Platform.runLater(this::onStateChanged));
    }

    public Scene build(Stage stage) {
        this.stage = stage;

        BorderPane root = new BorderPane();
        root.setLeft(buildSidebar());
        BorderPane center = new BorderPane();
        center.setCenter(buildCenter());
        root.setCenter(center);

        // Confirm bar (unknown map codename) sits above the toolbar when visible.
        confirmBar = buildConfirmBar();
        confirmBar.setVisible(false);
        confirmBar.setManaged(false);
        VBox topBox = new VBox(confirmBar, buildToolbar());
        center.setTop(topBox);

        StackPane overlay = new StackPane(root);
        toastLabel = new Label();
        toastLabel.getStyleClass().add("toast");
        toastLabel.setVisible(false);
        toastLabel.setManaged(false);
        StackPane.setAlignment(toastLabel, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toastLabel, new Insets(0, 0, 24, 0));
        overlay.getChildren().add(toastLabel);

        Scene scene = new Scene(overlay, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/theme.css").toExternalForm());
        stage.setTitle("Orbital");
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
        return scene;
    }

    // ------------------------------------------------------------------
    // Sidebar
    // ------------------------------------------------------------------

    private VBox buildSidebar() {
        Text t1 = new Text("Orbital");
        t1.setStyle("-fx-fill: #ece8e1; -fx-font-weight: 800; -fx-font-size: 26px;");
        TextFlow brand = new TextFlow(t1);
        Label sub = new Label("post-plant lineups, auto-detected");
        sub.getStyleClass().add("brand-sub");

        VBox mapList = new VBox(2);
        for (String m : MAPS) {
            Button b = new Button(m);
            b.getStyleClass().add("map-btn");
            b.setMaxWidth(Double.MAX_VALUE);
            Region dot = new Region();
            dot.getStyleClass().add("dot");
            b.setGraphic(dot);
            b.setOnAction(e -> state.setManualMap(m));
            mapButtons.put(m, b);
            mapList.getChildren().add(b);
        }
        ScrollPane mapScroll = new ScrollPane(mapList);
        mapScroll.getStyleClass().add("content-scroll");
        mapScroll.setFitToWidth(true);
        mapScroll.setPrefHeight(330);
        VBox.setVgrow(mapScroll, javafx.scene.layout.Priority.ALWAYS);

        Label statusLabel = new Label("LIVE GAME STATE");
        statusLabel.getStyleClass().add("section-label");
        gameV = statusValue();
        phaseV = statusValue();
        mapV = statusValue();
        spikeV = statusValue();
        youV = statusValue();
        VBox statusCard = new VBox(6,
                statusRow("Game", gameV), statusRow("Phase", phaseV),
                statusRow("Map", mapV), statusRow("Spike", spikeV), statusRow("You", youV));
        statusCard.getStyleClass().add("status-card");

        Button calibrate = new Button("Calibrate map region");
        calibrate.getStyleClass().addAll("btn", "ghost");
        calibrate.setOnAction(e -> calibrate());

        CheckBox onTop = new CheckBox("Always on top");
        onTop.getStyleClass().add("check-item");
        boolean initialTop = Prefs.get().getBool("alwaysOnTop", false);
        onTop.setSelected(initialTop);
        stage.setAlwaysOnTop(initialTop);
        onTop.selectedProperty().addListener((obs, o, n) -> {
            stage.setAlwaysOnTop(n);
            Prefs.get().put("alwaysOnTop", n);
        });

        Label hotkeyHint = new Label("F9 in-game (full map open) \u2192 detect spike & position");
        hotkeyHint.getStyleClass().add("brand-sub");
        hotkeyHint.setWrapText(true);

        VBox sidebar = new VBox(14, brand, sub, statusCard, statusLabel, mapScroll,
                calibrate, onTop, hotkeyHint);
        sidebar.getStyleClass().add("sidebar");
        return sidebar;
    }

    private Label statusValue() {
        Label l = new Label("\u2014");
        l.getStyleClass().add("status-v");
        return l;
    }

    private HBox statusRow(String key, Label value) {
        Label k = new Label(key.toUpperCase());
        k.getStyleClass().add("status-k");
        return new HBox(8, k, value);
    }

    // ------------------------------------------------------------------
    // Toolbar
    // ------------------------------------------------------------------

    private HBox buildToolbar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("toolbar");

        Button detect = new Button("\u25c9  DETECT SPIKE   [F9]");
        detect.getStyleClass().add("btn");
        detect.setOnAction(e -> triggerDetection());

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().addAll("btn", "ghost");
        refresh.setOnAction(e -> forceSearch());

        bar.getChildren().addAll(
                chipGroup("SITE", new String[]{"AUTO", "A", "B", "C"}, "AUTO", v ->
                        manualSite = "AUTO".equals(v) ? null : v),
                chipGroup("ABILITY", new String[]{"MOLLY", "SMOKES", "BOTH"}, "MOLLY", v ->
                        abilityMode = v.toLowerCase()),
                chipGroup("SIDE", new String[]{"ANY", "ATK", "DEF"}, "ANY", v ->
                        side = switch (v) {
                            case "ATK" -> LineupsClient.SIDE_ATTACK;
                            case "DEF" -> LineupsClient.SIDE_DEFENSE;
                            default -> LineupsClient.SIDE_ANY;
                        }),
                detect, refresh);
        return bar;
    }

    private HBox chipGroup(String name, String[] options, String initial, java.util.function.Consumer<String> onChange) {
        Label label = new Label(name);
        label.getStyleClass().add("chip-label");
        HBox group = new HBox(2);
        group.getStyleClass().add("chip-group");
        for (String opt : options) {
            Button b = new Button(opt);
            b.getStyleClass().add("chip");
            if (opt.equals(initial)) {
                b.getStyleClass().add("active");
            }
            b.setOnAction(e -> {
                group.getChildren().forEach(n -> n.getStyleClass().remove("active"));
                b.getStyleClass().add("active");
                onChange.accept(opt);
                scheduleSearch();
            });
            group.getChildren().add(b);
        }
        HBox withLabel = new HBox(8, label, group);
        withLabel.setAlignment(Pos.CENTER_LEFT);
        return withLabel;
    }

    // ------------------------------------------------------------------
    // Center content
    // ------------------------------------------------------------------

    private StackPane buildCenter() {
        gridTitle = new Label("Waiting for a match\u2026");
        gridTitle.getStyleClass().add("grid-title");
        gridSub = new Label("Pick a map on the left, or just start a game \u2014 I'll follow along from the Valorant log.");
        gridSub.getStyleClass().add("grid-sub");

        cardsFlow = new FlowPane(14, 14);
        cardsFlow.getStyleClass().add("cards-flow");

        VBox gridBox = new VBox(2, gridTitle, gridSub, cardsFlow);
        gridScroll = new ScrollPane(gridBox);
        gridScroll.getStyleClass().add("content-scroll");
        gridScroll.setFitToWidth(true);

        emptyState = new VBox(10);
        emptyState.setAlignment(Pos.CENTER);
        contentStack = new StackPane(gridScroll);
        return contentStack;
    }

    private HBox buildConfirmBar() {
        Label q = new Label("Unknown map codename from the log:");
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll(MAPS);
        combo.setPromptText("pick map");
        Button ok = new Button("Confirm");
        ok.getStyleClass().add("btn");
        ok.disableProperty().bind(combo.valueProperty().isNull());
        ok.setOnAction(e -> {
            String chosen = combo.getValue();
            String codename = state.getMapCodename();
            if (codename != null) {
                MapCodenames.learn(codename, chosen);
            }
            confirmBar.setVisible(false);
            confirmBar.setManaged(false);
            state.setManualMap(chosen);
        });
        HBox bar = new HBox(10, q, combo, ok);
        bar.getStyleClass().add("confirm-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    // ------------------------------------------------------------------
    // State changes
    // ------------------------------------------------------------------

    private void onStateChanged() {
        boolean running = state.isGameRunning();
        gameV.setText(running ? "Running" : "Offline");
        gameV.getStyleClass().setAll("status-v", running ? "ok" : "warn");
        phaseV.setText(switch (state.getPhase()) {
            case IN_MATCH -> "In match";
            case AGENT_SELECT -> "Agent select";
            case MENU -> "Menu";
            case OFFLINE -> "\u2014";
        });
        String map = state.getMap();
        mapV.setText(map == null ? "\u2014" : map + ("manual".equals(state.getMapSource()) ? "  (manual)" : "  (auto)"));
        mapV.getStyleClass().setAll("status-v", map == null ? "status-v" : "teal");
        spikeV.setText(state.plantCallout == null ? "\u2014" : state.plantCallout);
        spikeV.getStyleClass().setAll("status-v", state.plantSite == null ? "status-v" : "warn");
        youV.setText(state.playerCallout == null ? "\u2014" : state.playerCallout);

        for (Map.Entry<String, Button> e : mapButtons.entrySet()) {
            e.getValue().getStyleClass().remove("active");
            ((Region) e.getValue().getGraphic()).getStyleClass().remove("on");
            if (e.getKey().equals(map)) {
                e.getValue().getStyleClass().add("active");
            }
            if (running && e.getKey().equals(map)) {
                ((Region) e.getValue().getGraphic()).getStyleClass().add("on");
            }
        }

        boolean needConfirm = state.getPhase() == GameState.Phase.IN_MATCH
                && state.getMap() == null
                && state.getMapCodename() != null;
        confirmBar.setVisible(needConfirm);
        confirmBar.setManaged(needConfirm);
        if (needConfirm) {
            ((Label) confirmBar.getChildren().get(0)).setText(
                    "Unknown map codename \u201c" + state.getMapCodename() + "\u201d from the log \u2014 which map is this?");
        }

        if (map != null) {
            scheduleSearch();
        } else {
            showEmpty("Waiting for a Valorant match\u2026\nPick a map manually on the left if you want lineups now.");
        }
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    private void scheduleSearch() {
        if (searchDebounce == null) {
            searchDebounce = new javafx.animation.PauseTransition(Duration.millis(300));
            searchDebounce.setOnFinished(e -> runSearch());
        }
        searchDebounce.playFromStart();
    }

    /** Runs the search even if filters didn't change (e.g. Refresh button). */
    public void forceSearch() {
        lastSearchedMap = null;
        runSearch();
    }

    private String currentEnd() {
        if (manualSite != null) {
            return manualSite + " Site";
        }
        String c = state.plantCallout;
        if (c == null) {
            return null;
        }
        if (c.toLowerCase().contains("site")) {
            return c;
        }
        String letter = c.trim().isEmpty() ? null : c.trim().substring(0, 1).toUpperCase();
        return ("A".equals(letter) || "B".equals(letter) || "C".equals(letter)) ? letter + " Site" : null;
    }

    private List<String> currentAbilities() {
        return switch (abilityMode) {
            case "smoke" -> List.of(LineupsClient.ABILITY_SMOKE);
            case "both" -> List.of(LineupsClient.ABILITY_MOLLY, LineupsClient.ABILITY_SMOKE);
            default -> List.of(LineupsClient.ABILITY_MOLLY);
        };
    }

    private String sideKey() {
        return side + "|" + abilityMode + "|" + (manualSite == null ? "auto" : manualSite) + "|" + fromMyPosition;
    }

    private void runSearch() {
        String map = state.getMap();
        if (map == null) {
            showEmpty("Waiting for a Valorant match\u2026\nPick a map manually on the left if you want lineups now.");
            return;
        }
        String end = currentEnd();
        String start = fromMyPosition ? state.playerCallout : null;
        String abilities = String.join(",", currentAbilities());
        String sideK = sideKey();

        // Skip duplicate refreshes unless explicitly forced.
        if (map.equals(lastSearchedMap) && eq(end, lastSearchedEnd) && eq(start, lastSearchedStart)
                && abilities.equals(lastSearchedAbilities) && sideK.equals(lastSideKey)
                && !cardsFlow.getChildren().isEmpty()) {
            return;
        }
        lastSearchedMap = map;
        lastSearchedEnd = end;
        lastSearchedStart = start;
        lastSearchedAbilities = abilities;
        lastSideKey = sideK;

        gridTitle.setText("Searching\u2026");
        cardsFlow.getChildren().clear();

        io.execute(new Task<Void>() {
            List<Lineup> results = new ArrayList<>();

            @Override
            protected Void call() throws Exception {
                try {
                    results = client.search(map, currentAbilities(), start, end, side, "lineup", 60);
                    relaxedStart = false;
                    if (results.isEmpty() && start != null) {
                        results = client.search(map, currentAbilities(), null, end, side, "lineup", 60);
                        relaxedStart = true;
                    }
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        gridTitle.setText("Couldn't reach lineupsvalorant.com");
                        gridSub.setText(ex.getMessage() == null ? ex.toString() : ex.getMessage());
                    });
                }
                results.sort(Comparator.comparingLong(Lineup::getScore).reversed());
                return null;
            }

            @Override
            protected void succeeded() {
                populateResults(results, map, end, start, relaxedStart);
            }
        });
    }

    private void populateResults(List<Lineup> results, String map, String end, String start, boolean relaxed) {
        contentStack.getChildren().setAll(gridScroll);
        gridTitle.setText("Brimstone \u2014 " + map + (end != null ? "  \u2192  " + end : "  (all sites)"));
        StringBuilder sub = new StringBuilder();
        sub.append(results.size()).append(" lineups");
        if (end != null && state.plantCallout != null && manualSite == null) {
            sub.append("  \u00b7  spike detected at ").append(state.plantCallout);
        }
        if (start != null && !relaxed) {
            sub.append("  \u00b7  from your position (").append(start).append(")");
        }
        if (relaxed) {
            sub.append("  \u00b7  no lineup from ").append(state.playerCallout)
                    .append(" \u2014 showing all stand spots");
        }
        gridSub.setText(sub.toString());

        cardsFlow.getChildren().clear();
        if (results.isEmpty()) {
            showEmpty("No Brimstone lineups found for this filter.\nTry MOLLY+SMOKES, another site, or turn off position matching in a moment.");
            return;
        }
        for (Lineup l : results) {
            cardsFlow.getChildren().add(new LineupCard(l, client, imageIo, MainWindow.this::openDetail));
        }
    }

    private void showEmpty(String message) {
        contentStack.getChildren().setAll(gridScroll);
        gridTitle.setText("");
        gridSub.setText("");
        cardsFlow.getChildren().clear();
        Label l = new Label(message);
        l.getStyleClass().add("empty-state");
        emptyState.getChildren().setAll(l);
        contentStack.getChildren().setAll(emptyState);
    }

    // ------------------------------------------------------------------
    // Detail view
    // ------------------------------------------------------------------

    private void openDetail(Lineup lineup) {
        long id = lineup.getId();
        io.execute(new Task<Void>() {
            LineupDetail detail;

            @Override
            protected Void call() throws Exception {
                detail = client.lineup(id);
                return null;
            }

            @Override
            protected void succeeded() {
                DetailView dv = new DetailView(detail, client, imageIo, MainWindow.this::forceSearch);
                contentStack.getChildren().setAll(dv);
            }

            @Override
            protected void failed() {
                toast("Couldn't load lineup details: " + getException().getMessage(), true);
            }
        });
    }

    // ------------------------------------------------------------------
    // Detection (F9)
    // ------------------------------------------------------------------

    /** Called on the FX thread from the F9 hotkey or the Detect button. */
    public void triggerDetection() {
        if (!detectBusy.compareAndSet(false, true)) {
            return;
        }
        try {
            doDetection();
        } finally {
            detectBusy.set(false);
        }
    }

    private void doDetection() {
        String map = state.getMap();
        if (map == null) {
            String codename = state.getMapCodename();
            if (codename != null && MapCodenames.resolve(codename) == null) {
                toast("Map codename \u201c" + codename + "\u201d unknown \u2014 confirm the map in the bar above", true);
            } else {
                toast("No map selected \u2014 pick a map on the left first", true);
            }
            return;
        }

        Rectangle region = calibratedRegion();
        if (region == null) {
            region = CalibrationDialog.show(analyzer);
            if (region != null) {
                saveCalibration(region);
            }
            if (region == null) {
                region = ScreenAnalyzer.defaultRegion();
                toast("Using default map region \u2014 recalibrate later for accuracy", false);
            }
        }

        final Rectangle rect = region;
        toast("Capturing map\u2026", false);
        io.execute(new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                java.awt.image.BufferedImage img = analyzer.captureRegion(rect);
                List<ScreenAnalyzer.Marker> markers = analyzer.analyze(img, rect);
                List<ScreenAnalyzer.Marker> reds = markers.stream().filter(ScreenAnalyzer.Marker::red).toList();
                List<ScreenAnalyzer.Marker> whites = markers.stream().filter(m -> !m.red()).toList();

                if (reds.isEmpty()) {
                    Platform.runLater(() -> toast(
                            "No spike marker found \u2014 hold the full map open (M) when you press F9", true));
                    return null;
                }

                ScreenAnalyzer.Marker spike = reds.size() == 1 ? reds.get(0)
                        : onFx(() -> ClusterPickerDialog.show(img, reds.subList(0, Math.min(6, reds.size())),
                                "Which marker is the planted spike?", false));
                if (spike == null) {
                    return null;
                }
                String spikeCallout = resolveCallout(map, spike.x(), spike.y());
                String letter = letterOf(spikeCallout);
                state.setPlant(spikeCallout != null ? spikeCallout : "unknown (" + fmt(spike.x()) + "," + fmt(spike.y()) + ")",
                        letter, spike.x(), spike.y());

                if (!whites.isEmpty()) {
                    ScreenAnalyzer.Marker player = whites.size() == 1 ? whites.get(0)
                            : onFx(() -> ClusterPickerDialog.show(img, whites.subList(0, Math.min(6, whites.size())),
                                    "Which marker are you? (white arrows \u2014 site letters are fine to skip)", true));
                    if (player != null) {
                        String pc = resolveCallout(map, player.x(), player.y());
                        if (pc != null) {
                            state.setPlayer(pc, player.x(), player.y());
                        }
                    }
                }

                Platform.runLater(() -> {
                    if (spikeCallout != null) {
                        toast("Spike: " + spikeCallout + (letter != null ? "  (" + letter + " site)" : ""), false);
                    } else {
                        toast("Spike found but its callout is undefined \u2014 try the SITE chips", true);
                    }
                    forceSearch();
                });
                return null;
            }

            @Override
            protected void failed() {
                toast("Detection failed: " + getException().getMessage(), true);
            }
        });
    }

    /** Run a dialog on the FX thread and block the calling IO thread for the answer. */
    private <T> T onFx(Supplier<T> supplier) {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> f.complete(supplier.get()));
        try {
            return f.get();
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveCallout(String map, double x, double y) {
        try {
            String c = client.callout(map, x, y);
            if (c != null) {
                return c;
            }
            // Spike markers sit inside the site; nudge outward to escape undefined zones.
            for (double d : new double[]{12, 25, 40}) {
                for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    c = client.callout(map, x + dir[0] * d, y + dir[1] * d);
                    if (c != null) {
                        return c;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String letterOf(String callout) {
        if (callout == null || callout.isBlank()) {
            return null;
        }
        char c = Character.toUpperCase(callout.trim().charAt(0));
        return (c == 'A' || c == 'B' || c == 'C') ? String.valueOf(c) : null;
    }

    private static String fmt(double v) {
        return String.format("%.0f", v);
    }

    // ------------------------------------------------------------------
    // Calibration
    // ------------------------------------------------------------------

    private void calibrate() {
        Rectangle r = CalibrationDialog.show(analyzer);
        if (r != null) {
            saveCalibration(r);
            toast("Map region saved for this screen (" + r.width + "\u00d7" + r.height + ")", false);
        }
    }

    private String calibKey() {
        var sb = Screen.getPrimary().getBounds();
        return "calib." + (int) sb.getWidth() + "x" + (int) sb.getHeight();
    }

    private Rectangle calibratedRegion() {
        String v = Prefs.get().getString(calibKey(), null);
        if (v == null) {
            return null;
        }
        try {
            String[] p = v.split(",");
            Rectangle r = new Rectangle(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]), Integer.parseInt(p[3]));
            return r.width > 50 && r.height > 50 ? r : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void saveCalibration(Rectangle r) {
        Prefs.get().put(calibKey(), r.x + "," + r.y + "," + r.width + "," + r.height);
    }

    // ------------------------------------------------------------------
    // Toast
    // ------------------------------------------------------------------

    public void toast(String message, boolean error) {
        toastLabel.setText(message);
        toastLabel.getStyleClass().setAll("toast", error ? "error" : "toast");
        toastLabel.setVisible(true);
        toastLabel.setManaged(true);
        if (toastHide == null) {
            toastHide = new javafx.animation.PauseTransition(Duration.millis(error ? 4500 : 3000));
            toastHide.setOnFinished(e -> {
                toastLabel.setVisible(false);
                toastLabel.setManaged(false);
            });
        }
        toastHide.playFromStart();
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
