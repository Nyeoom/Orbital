package com.appbrim.ui;

import com.appbrim.Prefs;
import com.appbrim.api.LineupsClient;
import com.appbrim.detect.Callouts;
import com.appbrim.detect.MapCodenames;
import com.appbrim.detect.ScreenAnalyzer;
import com.appbrim.model.Favorites;
import com.appbrim.model.GameState;
import com.appbrim.model.Lineup;
import com.appbrim.model.LineupDetail;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** The whole main window: sidebar, toolbar, lineup grid, detail view, saved lineups, compact mode, toasts. */
public final class MainWindow {

    public static final List<String> MAPS = List.of(
            "Ascent", "Bind", "Breeze", "Corrode", "Fracture", "Haven", "Icebox",
            "Lotus", "Pearl", "Split", "Sunset", "Abyss", "Summit");

    private static final int RESULT_LIMIT = 60;
    private static final String EMPTY_WAITING =
            "Waiting for a Valorant match\u2026\nPick a map manually on the left if you want lineups now.";

    private final GameState state;
    private final LineupsClient client;
    private final ScreenAnalyzer analyzer;
    private final ExecutorService io;
    private final ExecutorService imageIo;
    private final Favorites favorites = new Favorites();

    private Stage stage;
    private final Map<String, Button> mapButtons = new LinkedHashMap<>();
    private Label gameV, phaseV, mapV, spikeV, youV;
    private Label gridTitle, gridSub;
    private FlowPane cardsFlow;
    private StackPane contentStack;
    private ScrollPane gridScroll;
    private VBox emptyState;
    private HBox confirmBar;
    private Label statusStrip;
    private Label toastLabel;
    private VBox sidebar;
    private Button savedBtn;
    private Button compactBtn;
    private Button filtersBtn;
    private final List<Node> filterNodes = new ArrayList<>();
    private boolean filtersOpen;
    private CheckBox onTop;
    private PauseTransition toastHide;
    private PauseTransition searchDebounce;

    /** What the content area shows when no detail view is open. */
    private Node baseNode;
    private DetailView detailView;

    private boolean compact;
    private boolean showingSaved;

    // Search filters (persisted where it makes sense)
    private String abilityChip = Prefs.get().getString("ability", "MOLLY");   // MOLLY | SMOKES | BOTH
    private String sideChip = Prefs.get().getString("side", "ANY");           // ANY | ATK | DEF
    private boolean fromMyPosition = Prefs.get().getBool("fromMe", true);
    private String manualSite;                                                // "A"/"B"/"C" or null (= follow detection)

    private String lastSearchedMap, lastSearchedEnd, lastSearchedStart, lastSearchedAbilities, lastSideKey;
    private boolean lastSearchOk;
    private final AtomicInteger searchSeq = new AtomicInteger();

    private final AtomicBoolean detectBusy = new AtomicBoolean(false);

    public MainWindow(GameState state, LineupsClient client, ScreenAnalyzer analyzer,
                      ExecutorService io, ExecutorService imageIo) {
        this.state = state;
        this.client = client;
        this.analyzer = analyzer;
        this.io = io;
        this.imageIo = imageIo;
        state.addListener(s -> Platform.runLater(this::onStateChanged));
        favorites.addListener(() -> Platform.runLater(this::updateSavedButton));
    }

    public Scene build(Stage stage) {
        this.stage = stage;

        BorderPane root = new BorderPane();
        sidebar = buildSidebar();
        root.setLeft(sidebar);
        BorderPane center = new BorderPane();
        center.setCenter(buildCenter());
        root.setCenter(center);

        // Confirm bar (unknown map codename) sits above the toolbar when visible.
        confirmBar = buildConfirmBar();
        confirmBar.setVisible(false);
        confirmBar.setManaged(false);
        statusStrip = new Label();
        statusStrip.getStyleClass().add("status-strip");
        statusStrip.setMaxWidth(Double.MAX_VALUE);
        statusStrip.setVisible(false);
        statusStrip.setManaged(false);
        VBox topBox = new VBox(confirmBar, statusStrip, buildToolbar());
        center.setTop(topBox);

        StackPane overlay = new StackPane(root);
        toastLabel = new Label();
        toastLabel.getStyleClass().add("toast");
        toastLabel.setVisible(false);
        toastLabel.setManaged(false);
        StackPane.setAlignment(toastLabel, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toastLabel, new Insets(0, 0, 18, 0));
        overlay.getChildren().add(toastLabel);

        Scene scene = new Scene(overlay, 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/theme.css").toExternalForm());
        stage.setTitle("Orbital");
        stage.setOpacity(clamp(Prefs.get().getDouble("opacity", 1.0), 0.5, 1.0));

        boolean startCompact = Prefs.get().getBool("compact", false);
        if (startCompact) {
            applyCompact(true);
        } else {
            applyMinSizes(false);
            restoreBounds("win.", 1280, 800);
        }
        updateSavedButton();
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

        Label stateLabel = new Label("LIVE GAME STATE");
        stateLabel.getStyleClass().add("section-label");
        gameV = statusValue();
        phaseV = statusValue();
        mapV = statusValue();
        spikeV = statusValue();
        youV = statusValue();

        Button clearPlant = new Button("\u2715");
        clearPlant.getStyleClass().add("mini-btn");
        clearPlant.setTooltip(new Tooltip("New round? Forget the detected spike and your position."));
        clearPlant.setOnAction(e -> {
            state.clearPlant();
            toast("Cleared \u2014 press F9 after the next plant", false);
        });
        HBox spikeRow = statusRow("Spike", spikeV);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        spikeRow.getChildren().addAll(spacer, clearPlant);

        VBox statusCard = new VBox(6,
                statusRow("Game", gameV), statusRow("Phase", phaseV),
                statusRow("Map", mapV), spikeRow, statusRow("You", youV));
        statusCard.getStyleClass().add("status-card");

        savedBtn = new Button();
        savedBtn.getStyleClass().add("map-btn");
        savedBtn.setMaxWidth(Double.MAX_VALUE);
        savedBtn.setOnAction(e -> toggleSaved());

        Label mapsLabel = new Label("MAPS");
        mapsLabel.getStyleClass().add("section-label");

        VBox mapList = new VBox(2);
        for (String m : MAPS) {
            Button b = new Button(m);
            b.getStyleClass().add("map-btn");
            b.setMaxWidth(Double.MAX_VALUE);
            Region dot = new Region();
            dot.getStyleClass().add("dot");
            b.setGraphic(dot);
            b.setOnAction(e -> {
                leaveDetail();
                showingSaved = false;
                state.setManualMap(m);
                updateSavedButton();
            });
            mapButtons.put(m, b);
            mapList.getChildren().add(b);
        }
        ScrollPane mapScroll = new ScrollPane(mapList);
        mapScroll.getStyleClass().add("content-scroll");
        mapScroll.setFitToWidth(true);
        mapScroll.setPrefHeight(330);
        VBox.setVgrow(mapScroll, Priority.ALWAYS);

        Button calibrate = new Button("Calibrate map region");
        calibrate.getStyleClass().addAll("btn", "ghost");
        calibrate.setOnAction(e -> calibrate());

        onTop = new CheckBox("Always on top");
        onTop.getStyleClass().add("check-item");
        boolean initialTop = Prefs.get().getBool("alwaysOnTop", false);
        onTop.setSelected(initialTop);
        stage.setAlwaysOnTop(initialTop);
        onTop.selectedProperty().addListener((obs, o, n) -> {
            stage.setAlwaysOnTop(n || compact);
            Prefs.get().put("alwaysOnTop", n);
        });

        Label opacityLabel = new Label("OPACITY");
        opacityLabel.getStyleClass().add("chip-label");
        Slider opacity = new Slider(0.5, 1.0, clamp(Prefs.get().getDouble("opacity", 1.0), 0.5, 1.0));
        opacity.getStyleClass().add("opacity-slider");
        opacity.valueProperty().addListener((o, a, v) -> stage.setOpacity(v.doubleValue()));
        opacity.setOnMouseReleased(e -> Prefs.get().put("opacity", opacity.getValue()));
        HBox.setHgrow(opacity, Priority.ALWAYS);
        HBox opacityRow = new HBox(10, opacityLabel, opacity);
        opacityRow.setAlignment(Pos.CENTER_LEFT);

        Label hotkeyHint = new Label("F9 in-game (full map open) \u2192 detect spike & position");
        hotkeyHint.getStyleClass().add("brand-sub");
        hotkeyHint.setWrapText(true);
        // Wrapped labels in a VBox otherwise get their height from the unwrapped text and truncate.
        hotkeyHint.setPrefWidth(214);
        hotkeyHint.setMaxWidth(214);
        hotkeyHint.setMinHeight(Region.USE_PREF_SIZE);

        VBox box = new VBox(12, brand, sub, stateLabel, statusCard, savedBtn, mapsLabel, mapScroll,
                calibrate, onTop, opacityRow, hotkeyHint);
        box.getStyleClass().add("sidebar");
        return box;
    }

    private Label statusValue() {
        Label l = new Label("\u2014");
        l.getStyleClass().add("status-v");
        return l;
    }

    private HBox statusRow(String key, Label value) {
        Label k = new Label(key.toUpperCase());
        k.getStyleClass().add("status-k");
        HBox row = new HBox(8, k, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // ------------------------------------------------------------------
    // Toolbar
    // ------------------------------------------------------------------

    private FlowPane buildToolbar() {
        FlowPane bar = new FlowPane(12, 8);
        bar.getStyleClass().add("toolbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setRowValignment(VPos.CENTER);

        Button detect = new Button("\u25c9  DETECT SPIKE   [F9]");
        detect.getStyleClass().add("btn");
        detect.setOnAction(e -> triggerDetection());

        Button refresh = new Button("Refresh");
        refresh.getStyleClass().addAll("btn", "ghost");
        refresh.setOnAction(e -> {
            client.invalidateSearchCache();
            forceSearch();
        });

        compactBtn = new Button();
        compactBtn.getStyleClass().addAll("btn", "ghost");
        compactBtn.setTooltip(new Tooltip("Small always-on-top window for playing with Orbital next to the game"));
        compactBtn.setOnAction(e -> setCompact(!compact));
        compactBtn.setText(compact ? "Expand" : "Compact");

        filterNodes.clear();
        filterNodes.add(chipGroup("SITE", new String[]{"AUTO", "A", "B", "C"}, "AUTO", v ->
                manualSite = "AUTO".equals(v) ? null : v));
        filterNodes.add(chipGroup("ABILITY", new String[]{"MOLLY", "SMOKES", "BOTH"}, abilityChip, v -> {
            abilityChip = v;
            Prefs.get().put("ability", v);
        }));
        filterNodes.add(chipGroup("SIDE", new String[]{"ANY", "ATK", "DEF"}, sideChip, v -> {
            sideChip = v;
            Prefs.get().put("side", v);
        }));
        filterNodes.add(chipGroup("FROM ME", new String[]{"ON", "OFF"}, fromMyPosition ? "ON" : "OFF", v -> {
            fromMyPosition = "ON".equals(v);
            Prefs.get().put("fromMe", fromMyPosition);
        }));

        // Compact mode hides the filter chips (they eat a third of a small window) behind this toggle.
        filtersBtn = new Button("Filters \u25be");
        filtersBtn.getStyleClass().addAll("btn", "ghost");
        filtersBtn.setOnAction(e -> {
            filtersOpen = !filtersOpen;
            applyFilterVisibility();
        });

        bar.getChildren().addAll(filterNodes);
        bar.getChildren().addAll(detect, refresh, filtersBtn, compactBtn);
        applyFilterVisibility();
        return bar;
    }

    private void applyFilterVisibility() {
        boolean show = !compact || filtersOpen;
        for (Node n : filterNodes) {
            n.setVisible(show);
            n.setManaged(show);
        }
        if (filtersBtn != null) {
            filtersBtn.setVisible(compact);
            filtersBtn.setManaged(compact);
            filtersBtn.setText(filtersOpen ? "Filters \u25b4" : "Filters \u25be");
        }
    }

    private HBox chipGroup(String name, String[] options, String initial, Consumer<String> onChange) {
        Label label = new Label(name);
        label.getStyleClass().add("chip-label");
        HBox group = new HBox(2);
        group.getStyleClass().add("chip-group");
        // Unknown persisted value (older prefs) falls back to the first option.
        String start = List.of(options).contains(initial) ? initial : options[0];
        for (String opt : options) {
            Button b = new Button(opt);
            b.getStyleClass().add("chip");
            if (opt.equals(start)) {
                b.getStyleClass().add("active");
            }
            b.setOnAction(e -> {
                group.getChildren().forEach(n -> n.getStyleClass().remove("active"));
                b.getStyleClass().add("active");
                onChange.accept(opt);
                // Touching a filter means "show me search results" again.
                if (showingSaved) {
                    showingSaved = false;
                    updateSavedButton();
                }
                leaveDetail();
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
        gridTitle.setWrapText(true);
        gridSub = new Label("Pick a map on the left, or just start a game \u2014 I'll follow along from the Valorant log.");
        gridSub.getStyleClass().add("grid-sub");
        gridSub.setWrapText(true);

        cardsFlow = new FlowPane(14, 14);
        cardsFlow.getStyleClass().add("cards-flow");

        VBox gridBox = new VBox(2, gridTitle, gridSub, cardsFlow);
        gridScroll = new ScrollPane(gridBox);
        gridScroll.getStyleClass().add("content-scroll");
        gridScroll.setFitToWidth(true);

        emptyState = new VBox(14);
        emptyState.setAlignment(Pos.CENTER);
        contentStack = new StackPane(gridScroll);
        baseNode = gridScroll;
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
    // Content switching (grid / empty state / detail)
    // ------------------------------------------------------------------

    /** Sets what the content area shows, unless a detail view currently has the stage. */
    private void showBase(Node node) {
        baseNode = node;
        if (detailView == null) {
            contentStack.getChildren().setAll(node);
        }
    }

    /** Closes the detail view (if open) and returns to the grid. */
    private void leaveDetail() {
        if (detailView == null) {
            return;
        }
        detailView = null;
        if (showingSaved) {
            showSaved();
        } else {
            for (Node n : cardsFlow.getChildren()) {
                if (n instanceof LineupCard c) {
                    c.refreshStar();
                }
            }
            contentStack.getChildren().setAll(baseNode);
        }
    }

    private void showEmpty(String message, boolean withRetry) {
        gridTitle.setText("");
        gridSub.setText("");
        cardsFlow.getChildren().clear();
        Label l = new Label(message);
        l.getStyleClass().add("empty-state");
        l.setWrapText(true);
        l.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        emptyState.getChildren().setAll(l);
        if (withRetry) {
            Button retry = new Button("Try again");
            retry.getStyleClass().add("btn");
            retry.setOnAction(e -> {
                client.invalidateSearchCache();
                forceSearch();
            });
            emptyState.getChildren().add(retry);
        }
        showBase(emptyState);
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
        statusStrip.setText((map == null ? "No map" : map)
                + "   \u00b7   spike: " + (state.plantCallout == null ? "\u2014" : state.plantCallout)
                + "   \u00b7   you: " + (state.playerCallout == null ? "\u2014" : state.playerCallout));

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

        if (showingSaved) {
            return;     // the Saved view isn't driven by game state
        }
        if (map != null) {
            scheduleSearch();
        } else {
            lastSearchedMap = null;
            lastSearchOk = false;
            searchSeq.incrementAndGet();
            showEmpty(EMPTY_WAITING, false);
        }
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    private void scheduleSearch() {
        if (searchDebounce == null) {
            searchDebounce = new PauseTransition(Duration.millis(300));
            searchDebounce.setOnFinished(e -> runSearch());
        }
        searchDebounce.playFromStart();
    }

    /** Runs the search even if filters didn't change (e.g. Refresh button). */
    public void forceSearch() {
        showingSaved = false;
        updateSavedButton();
        lastSearchedMap = null;
        runSearch();
    }

    private String currentEnd() {
        if (manualSite != null) {
            return manualSite + " Site";
        }
        return Callouts.endFor(state.plantCallout);
    }

    private List<String> currentAbilities() {
        return switch (abilityChip) {
            case "SMOKES" -> List.of(LineupsClient.ABILITY_SMOKE);
            case "BOTH" -> List.of(LineupsClient.ABILITY_MOLLY, LineupsClient.ABILITY_SMOKE);
            default -> List.of(LineupsClient.ABILITY_MOLLY);
        };
    }

    private int currentSide() {
        return switch (sideChip) {
            case "ATK" -> LineupsClient.SIDE_ATTACK;
            case "DEF" -> LineupsClient.SIDE_DEFENSE;
            default -> LineupsClient.SIDE_ANY;
        };
    }

    private record Outcome(List<Lineup> results, boolean relaxed) { }

    private void runSearch() {
        if (showingSaved) {
            return;
        }
        String map = state.getMap();
        if (map == null) {
            showEmpty(EMPTY_WAITING, false);
            return;
        }
        String end = currentEnd();
        String start = fromMyPosition ? state.playerCallout : null;
        List<String> abilities = currentAbilities();
        String abilitiesKey = String.join(",", abilities);
        int side = currentSide();
        String sideKey = side + "|" + (manualSite == null ? "auto" : manualSite) + "|" + fromMyPosition;

        // Skip duplicate refreshes unless explicitly forced.
        if (lastSearchOk && map.equals(lastSearchedMap) && eq(end, lastSearchedEnd) && eq(start, lastSearchedStart)
                && abilitiesKey.equals(lastSearchedAbilities) && sideKey.equals(lastSideKey)) {
            return;
        }
        lastSearchedMap = map;
        lastSearchedEnd = end;
        lastSearchedStart = start;
        lastSearchedAbilities = abilitiesKey;
        lastSideKey = sideKey;
        lastSearchOk = false;

        // Any newer search (or a state reset) makes older in-flight results stale.
        final int seq = searchSeq.incrementAndGet();
        gridTitle.setText("Searching\u2026");
        gridSub.setText("");
        cardsFlow.getChildren().clear();
        showBase(gridScroll);

        Task<Outcome> task = new Task<>() {
            @Override
            protected Outcome call() throws Exception {
                List<Lineup> results = new ArrayList<>(
                        client.search(map, abilities, start, end, side, "lineup", RESULT_LIMIT));
                boolean relaxed = false;
                if (results.isEmpty() && start != null) {
                    results = new ArrayList<>(client.search(map, abilities, null, end, side, "lineup", RESULT_LIMIT));
                    relaxed = true;
                }
                results.sort(Comparator.comparingLong(Lineup::getScore).reversed());
                return new Outcome(results, relaxed);
            }
        };
        task.setOnSucceeded(e -> {
            if (seq != searchSeq.get()) {
                return;
            }
            lastSearchOk = true;
            populateResults(task.getValue(), map, end, start);
        });
        task.setOnFailed(e -> {
            if (seq != searchSeq.get()) {
                return;
            }
            lastSearchedMap = null;     // let the next trigger retry
            Throwable ex = task.getException();
            showEmpty("Couldn't reach lineupsvalorant.com\n"
                    + (ex == null || ex.getMessage() == null ? String.valueOf(ex) : ex.getMessage()), true);
        });
        io.execute(task);
    }

    private void populateResults(Outcome outcome, String map, String end, String start) {
        List<Lineup> results = outcome.results();
        boolean relaxed = outcome.relaxed();
        gridTitle.setText("Brimstone \u2014 " + map + (end != null ? "  \u2192  " + end : "  (all sites)"));
        StringBuilder sub = new StringBuilder();
        sub.append(results.size()).append(results.size() == 1 ? " lineup" : " lineups");
        if (end != null && state.plantCallout != null && manualSite == null) {
            sub.append("  \u00b7  spike detected at ").append(state.plantCallout);
        }
        if (start != null && !relaxed) {
            sub.append("  \u00b7  from your position (").append(start).append(")");
        }
        if (relaxed) {
            sub.append("  \u00b7  none from ").append(state.playerCallout)
                    .append(" \u2014 showing every stand spot");
        }
        gridSub.setText(sub.toString());

        cardsFlow.getChildren().clear();
        if (results.isEmpty()) {
            showEmpty("No Brimstone lineups found for this filter.\n"
                    + "Try MOLLY + SMOKES, another site, or turn FROM ME off.", false);
            return;
        }
        for (Lineup l : results) {
            cardsFlow.getChildren().add(new LineupCard(l, client, imageIo, favorites, false, this::openDetail));
        }
        showBase(gridScroll);
    }

    // ------------------------------------------------------------------
    // Saved lineups
    // ------------------------------------------------------------------

    private void toggleSaved() {
        leaveDetail();
        showingSaved = !showingSaved;
        if (showingSaved) {
            showSaved();
        } else {
            lastSearchedMap = null;
            lastSearchOk = false;
            onStateChanged();
        }
        updateSavedButton();
    }

    private void showSaved() {
        searchSeq.incrementAndGet();      // drop any in-flight search
        List<Lineup> saved = favorites.all();
        cardsFlow.getChildren().clear();
        if (saved.isEmpty()) {
            showEmpty("No saved lineups yet.\nStar a lineup (\u2606 on a card, or Save in its detail view) to keep it here.", false);
            return;
        }
        gridTitle.setText("\u2605  Saved lineups");
        gridSub.setText(saved.size() + (saved.size() == 1 ? " lineup" : " lineups") + "  \u00b7  from every map");
        for (Lineup l : saved) {
            cardsFlow.getChildren().add(new LineupCard(l, client, imageIo, favorites, true, this::openDetail));
        }
        showBase(gridScroll);
    }

    private void updateSavedButton() {
        if (savedBtn == null) {
            return;
        }
        savedBtn.setText("\u2605  Saved (" + favorites.size() + ")");
        savedBtn.getStyleClass().remove("active");
        if (showingSaved) {
            savedBtn.getStyleClass().add("active");
        }
    }

    // ------------------------------------------------------------------
    // Detail view
    // ------------------------------------------------------------------

    private void openDetail(Lineup lineup) {
        long id = lineup.getId();
        Task<LineupDetail> task = new Task<>() {
            @Override
            protected LineupDetail call() throws Exception {
                return client.lineup(id);
            }
        };
        task.setOnSucceeded(e -> {
            detailView = new DetailView(lineup, task.getValue(), client, imageIo, favorites, this::leaveDetail);
            contentStack.getChildren().setAll(detailView);
        });
        task.setOnFailed(e -> toast("Couldn't load lineup details: " + task.getException().getMessage(), true));
        io.execute(task);
    }

    // ------------------------------------------------------------------
    // Detection (F9)
    // ------------------------------------------------------------------

    /** Called on the FX thread from the F9 hotkey or the Detect button. */
    public void triggerDetection() {
        if (!detectBusy.compareAndSet(false, true)) {
            return;
        }
        boolean started = false;
        try {
            started = doDetection();
        } catch (RuntimeException ex) {
            toast("Detection failed: " + ex.getMessage(), true);
        } finally {
            if (!started) {
                detectBusy.set(false);
            }
        }
    }

    /** @return true when a background task was started (it releases the busy flag itself). */
    private boolean doDetection() {
        String map = state.getMap();
        if (map == null) {
            String codename = state.getMapCodename();
            if (codename != null && MapCodenames.resolve(codename) == null) {
                toast("Map codename \u201c" + codename + "\u201d unknown \u2014 confirm the map in the bar above", true);
            } else {
                toast("No map selected \u2014 pick a map on the left first", true);
            }
            return false;
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
                java.awt.image.BufferedImage img = captureWithoutOurWindow(rect);
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
                String letter = Callouts.siteLetter(spikeCallout);
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
                    leaveDetail();
                    forceSearch();
                });
                return null;
            }

            @Override
            protected void failed() {
                toast("Detection failed: " + getException().getMessage(), true);
            }

            @Override
            protected void done() {
                detectBusy.set(false);
            }
        });
        return true;
    }

    /**
     * Screenshots the region. When Orbital's own window overlaps it (compact overlay on top of
     * the game), the window is hidden for a moment first, otherwise our red buttons would be
     * "detected" as spike markers.
     */
    private java.awt.image.BufferedImage captureWithoutOurWindow(Rectangle rect) throws Exception {
        Boolean hit = onFx(() -> {
            Rectangle2D me = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
            Rectangle2D target = new Rectangle2D(rect.x, rect.y, rect.width, rect.height);
            boolean overlap = stage.isShowing() && !stage.isIconified() && me.intersects(target);
            if (overlap) {
                stage.setOpacity(0);
            }
            return overlap;
        });
        boolean overlaps = hit != null && hit;
        try {
            if (overlaps) {
                Thread.sleep(220);      // let the compositor repaint without us
            }
            return analyzer.captureRegion(rect);
        } finally {
            if (overlaps) {
                Platform.runLater(() -> stage.setOpacity(clamp(Prefs.get().getDouble("opacity", 1.0), 0.5, 1.0)));
            }
        }
    }

    /** Run a dialog on the FX thread and block the calling IO thread for the answer. */
    private <T> T onFx(Supplier<T> supplier) {
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                f.complete(supplier.get());
            } catch (RuntimeException ex) {
                f.completeExceptionally(ex);
            }
        });
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
            // Callout lookups are best effort.
        }
        return null;
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
    // Compact mode + window state
    // ------------------------------------------------------------------

    private void setCompact(boolean on) {
        if (on == compact) {
            return;
        }
        saveBounds(compact ? "compact." : "win.");
        leaveDetail();
        applyCompact(on);
    }

    private void applyCompact(boolean on) {
        compact = on;
        Prefs.get().put("compact", on);
        sidebar.setVisible(!on);
        sidebar.setManaged(!on);
        statusStrip.setVisible(on);
        statusStrip.setManaged(on);
        compactBtn.setText(on ? "Expand" : "Compact");
        applyFilterVisibility();
        applyMinSizes(on);
        restoreBounds(on ? "compact." : "win.", on ? 480 : 1280, on ? 780 : 800);
        stage.setAlwaysOnTop(on || onTop.isSelected());
    }

    private void applyMinSizes(boolean compactMode) {
        stage.setMinWidth(compactMode ? 400 : 1000);
        stage.setMinHeight(compactMode ? 420 : 640);
    }

    private void saveBounds(String prefix) {
        if (stage == null || !stage.isShowing() || stage.isIconified()) {
            return;
        }
        Prefs p = Prefs.get();
        p.put(prefix + "x", stage.getX());
        p.put(prefix + "y", stage.getY());
        p.put(prefix + "w", stage.getWidth());
        p.put(prefix + "h", stage.getHeight());
    }

    private void restoreBounds(String prefix, double defW, double defH) {
        Prefs p = Prefs.get();
        double w = Math.max(stage.getMinWidth(), p.getDouble(prefix + "w", defW));
        double h = Math.max(stage.getMinHeight(), p.getDouble(prefix + "h", defH));
        stage.setWidth(w);
        stage.setHeight(h);
        double x = p.getDouble(prefix + "x", Double.NaN);
        double y = p.getDouble(prefix + "y", Double.NaN);
        // Only restore a position whose top-left corner is still on a connected screen.
        if (!Double.isNaN(x) && !Double.isNaN(y) && !Screen.getScreensForRectangle(x, y, 1, 1).isEmpty()) {
            stage.setX(x);
            stage.setY(y);
        }
    }

    /** Remember window size/position for next launch (called when the app closes). */
    public void saveWindowState() {
        saveBounds(compact ? "compact." : "win.");
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
            toastHide = new PauseTransition(Duration.millis(error ? 4500 : 3000));
            toastHide.setOnFinished(e -> {
                toastLabel.setVisible(false);
                toastLabel.setManaged(false);
            });
        }
        toastHide.setDuration(Duration.millis(error ? 4500 : 3000));
        toastHide.playFromStart();
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
