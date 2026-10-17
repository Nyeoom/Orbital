package com.appbrim;

import com.appbrim.detect.LogWatcher;
import com.appbrim.model.GameState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogWatcherTest {

    static String mapLine(String name, boolean local) {
        return "[2026.10.15-02.10.05:100][  0]LogMapLoadModel: Update: [Map Name: " + name + " | Changed: TRUE] "
                + "[Local World: " + (local ? "TRUE" : "FALSE") + " | Changed: TRUE] [Match Setup: TRUE | Changed: TRUE] "
                + "[Map Complete: TRUE | Changed: TRUE] [URL: 192.207.0.1:7107/Game/Maps/" + name + "/" + name
                + "?Game=/Game/GameModes/Bomb/BombGameMode.BombGameMode_C]\n";
    }

    private LogWatcher watcher;

    @AfterEach
    void stop() {
        if (watcher != null) {
            watcher.stop();
        }
    }

    private static void await(BooleanSupplier cond, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < end) {
            if (cond.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("timed out waiting for: " + what);
    }

    @Test
    void parsesMapLines() {
        LogWatcher.MapEvent ev = LogWatcher.parseMapLine(mapLine("Foxtrot", true));
        assertNotNull(ev);
        assertEquals("Foxtrot", ev.name());
        assertTrue(ev.localWorld());
        assertTrue(ev.url().contains("/Game/Maps/Foxtrot/Foxtrot"));
    }

    @Test
    void ignoresUnrelatedLines() {
        assertNull(LogWatcher.parseMapLine("[2026.10.15][  0]LogNet: something else"));
        assertNull(LogWatcher.parseMapLine(null));
        assertNull(LogWatcher.parseMapLine("LogMapLoadModel: garbage"));
    }

    @Test
    void actionableMeansSystemLevelOrRealTravel() {
        assertTrue(LogWatcher.isActionable(LogWatcher.parseMapLine(mapLine("MainMenuV2", false))));
        assertTrue(LogWatcher.isActionable(LogWatcher.parseMapLine(mapLine("Foxtrot", true))));
        // Not local world, but the URL is the map itself (travelling in):
        assertTrue(LogWatcher.isActionable(LogWatcher.parseMapLine(mapLine("Foxtrot", false))));
        assertFalse(LogWatcher.isActionable(new LogWatcher.MapEvent("Foxtrot", false, "")));
    }

    @Test
    void startingMidMatchPicksUpTheCurrentMap(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("ShooterGame.log");
        Files.writeString(log, mapLine("MainMenuV2", true) + mapLine("Foxtrot", true) + "[junk]\n");
        GameState state = new GameState();
        watcher = new LogWatcher(state, log, 30);
        watcher.start();
        await(() -> "Breeze".equals(state.getMap()), "Breeze detected from log history");
        assertEquals(GameState.Phase.IN_MATCH, state.getPhase());
        assertTrue(state.isGameRunning());
    }

    @Test
    void followsNewLinesAndReturnsToMenu(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("ShooterGame.log");
        Files.writeString(log, mapLine("MainMenuV2", true));
        GameState state = new GameState();
        watcher = new LogWatcher(state, log, 30);
        watcher.start();
        await(() -> state.getPhase() == GameState.Phase.MENU, "menu");

        Files.writeString(log, mapLine("Canyon", true), StandardOpenOption.APPEND);
        await(() -> "Fracture".equals(state.getMap()), "Fracture");

        Files.writeString(log, mapLine("MainMenuV2", true), StandardOpenOption.APPEND);
        await(() -> state.getMap() == null && state.getPhase() == GameState.Phase.MENU, "back to menu");
    }

    @Test
    void unknownMapExposesCodenameForConfirmation(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("ShooterGame.log");
        Files.writeString(log, mapLine("MainMenuV2", true));
        GameState state = new GameState();
        watcher = new LogWatcher(state, log, 30);
        watcher.start();
        await(() -> state.getPhase() == GameState.Phase.MENU, "menu");

        Files.writeString(log, mapLine("SomeFutureMap", true), StandardOpenOption.APPEND);
        await(() -> "SomeFutureMap".equals(state.getMapCodename()), "codename");
        assertNull(state.getMap());
        assertEquals(GameState.Phase.IN_MATCH, state.getPhase());
    }

    @Test
    void logAppearingLaterIsReadFromTheTop(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("ShooterGame.log");   // does not exist yet: game not started
        GameState state = new GameState();
        watcher = new LogWatcher(state, log, 30);
        watcher.start();
        Thread.sleep(150);
        assertFalse(state.isGameRunning());
        Files.writeString(log, mapLine("Triad", true));
        await(() -> "Haven".equals(state.getMap()), "Haven");
    }

    @Test
    void replacedLogIsDetectedAsRestart(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("ShooterGame.log");
        Files.writeString(log, mapLine("MainMenuV2", true) + mapLine("Foxtrot", true) + "x".repeat(400) + "\n");
        GameState state = new GameState();
        watcher = new LogWatcher(state, log, 30);
        watcher.start();
        await(() -> "Breeze".equals(state.getMap()), "Breeze");

        // Valorant restarts: the old log is replaced by a short new one.
        Files.writeString(log, mapLine("MainMenuV2", true));
        await(() -> state.getMap() == null, "map cleared after restart");
    }

    @Test
    void staleLogFromAnOldSessionIsNotTreatedAsLive(@TempDir Path dir) throws Exception {
        Path log = dir.resolve("ShooterGame.log");
        Files.writeString(log, mapLine("Foxtrot", true));
        Files.setLastModifiedTime(log, java.nio.file.attribute.FileTime.fromMillis(
                System.currentTimeMillis() - 60 * 60 * 1000));
        GameState state = new GameState();
        watcher = new LogWatcher(state, log, 30);
        watcher.start();
        Thread.sleep(300);
        assertFalse(state.isGameRunning());
        assertNull(state.getMap());
    }
}
