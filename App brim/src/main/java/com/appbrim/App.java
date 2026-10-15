package com.appbrim;

import com.appbrim.api.LineupsClient;
import com.appbrim.detect.LogWatcher;
import com.appbrim.detect.ScreenAnalyzer;
import com.appbrim.model.GameState;
import com.appbrim.ui.MainWindow;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Orbital - Brimstone post-plant lineup assistant.
 *
 * Follows the running Valorant game from its local log (map + phase), and when
 * the player presses F9 with the full map open, screenshots the planted-spike
 * marker, resolves its callout via lineupsvalorant.com, and shows matching
 * Brimstone lineups - preferring ones thrown from the player's current spot.
 */
public class App extends Application {

    /** VK_F9 */
    private static final int F9 = 0x78;

    private static HostServices hostServices;

    private final GameState state = new GameState();
    private final LineupsClient client = new LineupsClient();
    private final ScreenAnalyzer analyzer = new ScreenAnalyzer();

    private ExecutorService io;
    private ExecutorService imageIo;
    private LogWatcher watcher;
    private MainWindow ui;

    @Override
    public void start(Stage stage) {
        hostServices = getHostServices();

        io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "appbrim-io");
            t.setDaemon(true);
            return t;
        });
        imageIo = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "appbrim-image-io");
            t.setDaemon(true);
            return t;
        });

        ui = new MainWindow(state, client, analyzer, io, imageIo);
        stage.setScene(ui.build(stage));
        stage.show();

        watcher = new LogWatcher(state);
        watcher.start();

        boolean hotkeyOk = com.appbrim.hotkey.GlobalHotkey.register(F9, () ->
                Platform.runLater(ui::triggerDetection));
        if (!hotkeyOk) {
            ui.toast("Global F9 hotkey unavailable \u2014 use the DETECT button", true);
        } else {
            ui.toast("Watching for Valorant\u2026 press F9 with the full map (M) open in-game", false);
        }

        loadStageIcon(stage);
        imageIo.execute(() -> com.appbrim.api.ImageCache.get().prune());

        stage.setOnCloseRequest(e -> {
            ui.saveWindowState();
            shutdown();
        });
        stage.setOnHidden(e -> shutdown());
    }

    /** Bundled icon: no network needed, shows up in the taskbar immediately. */
    private void loadStageIcon(Stage stage) {
        try (java.io.InputStream in = App.class.getResourceAsStream("/icon.png")) {
            if (in != null) {
                stage.getIcons().add(new Image(in));
            }
        } catch (java.io.IOException ignored) {
            // The default Java icon is an acceptable fallback.
        }
    }

    private void shutdown() {
        if (watcher != null) {
            watcher.stop();
        }
        io.shutdownNow();
        imageIo.shutdownNow();
        try {
            io.awaitTermination(1, TimeUnit.SECONDS);
            imageIo.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static HostServices hostServices() {
        return hostServices;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
