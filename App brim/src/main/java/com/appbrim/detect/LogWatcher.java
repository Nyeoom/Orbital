package com.appbrim.detect;

import com.appbrim.model.GameState;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tails Valorant's ShooterGame.log to derive the live phase and map.
 * The game writes lines like:
 * <pre>
 * LogMapLoadModel: Update: [Map Name: Foxtrot | Changed: TRUE] [Local World: TRUE | Changed: TRUE]
 *   [Match Setup: FALSE | Changed: FALSE] ... [URL: 192.207.0.1:7107/Game/Maps/Foxtrot/Foxtrot?...BombGameMode...]
 * </pre>
 * "Foxtrot" is Ascent's internal codename; MapCodenames translates. This is the same
 * passive log-reading technique commercial companion apps use - nothing is injected
 * into or read from the game process.
 */
public final class LogWatcher {

    public static final Path LOG_PATH = Path.of(System.getProperty("user.home"),
            "AppData", "Local", "Valorant", "Saved", "Logs", "ShooterGame.log");

    private static final Pattern MAP_LINE = Pattern.compile(
            "LogMapLoadModel: Update: \\[Map Name: (\\w+) \\|.*?\\[Local World: (\\w+).*?\\[Match Setup: (\\w+).*?\\[Map Complete: (\\w+).*?\\[URL: (\\S*)");

    private static final long STALE_AFTER_MS = 5 * 60 * 1000;

    private final GameState state;
    private Thread thread;
    private volatile boolean running;

    public LogWatcher(GameState state) {
        this.state = state;
    }

    public void start() {
        running = true;
        thread = new Thread(this::run, "valorant-log-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void run() {
        long filePtr = 0;
        long lastAppendAt = 0;
        String currentCodename = null;

        // Start at end of file: history is irrelevant, only live sessions matter.
        if (Files.exists(LOG_PATH)) {
            try {
                filePtr = Files.size(LOG_PATH);
                lastAppendAt = Files.getLastModifiedTime(LOG_PATH).toMillis();
            } catch (IOException ignored) {
            }
        }

        while (running) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            }

            if (!Files.exists(LOG_PATH)) {
                state.setGameRunning(false);
                continue;
            }

            try {
                long len = Files.size(LOG_PATH);
                if (len < filePtr) {
                    // Log was rotated: Valorant restarted. Reopen from the beginning.
                    filePtr = 0;
                    currentCodename = null;
                }

                long mtime = Files.getLastModifiedTime(LOG_PATH).toMillis();
                if (mtime > lastAppendAt) {
                    lastAppendAt = mtime;
                }
                state.setGameRunning(System.currentTimeMillis() - lastAppendAt < STALE_AFTER_MS);

                if (len > filePtr) {
                    try (RandomAccessFile raf = new RandomAccessFile(LOG_PATH.toFile(), "r")) {
                        raf.seek(filePtr);
                        byte[] buf = new byte[(int) (len - filePtr)];
                        raf.readFully(buf);
                        filePtr = len;
                        for (String line : new String(buf, StandardCharsets.UTF_8).split("\n")) {
                            currentCodename = parse(line, currentCodename);
                        }
                    }
                } else if (!state.isGameRunning() && state.getPhase() != GameState.Phase.OFFLINE) {
                    // No fresh log output for a long while: game closed.
                    currentCodename = null;
                    state.setPhaseAndMap(GameState.Phase.OFFLINE, null, null, null);
                }
            } catch (IOException ignored) {
                // File locked mid-rotation etc.; retry next tick.
            }
        }
    }

    private String parse(String line, String currentCodename) {
        Matcher m = MAP_LINE.matcher(line);
        if (!m.find()) {
            return currentCodename;
        }
        String name = m.group(1);
        boolean localWorld = "TRUE".equals(m.group(2));
        String url = m.groupCount() >= 5 ? m.group(5) : "";

        if (MapCodenames.isSystemLevel(name)) {
            currentCodename = null;
            state.setPhaseAndMap(switch (name) {
                case "MainMenuV2" -> GameState.Phase.MENU;
                case "CharacterSelectPersistentLevel" -> GameState.Phase.AGENT_SELECT;
                default -> GameState.Phase.MENU;
            }, null, null, "log");
            return currentCodename;
        }

        // A playable map. Only trust it once we actually travel into the world.
        if (localWorld || url.contains("/Game/Maps/" + name + "/" + name)) {
            currentCodename = name;
            String display = MapCodenames.resolve(name);
            state.setPhaseAndMap(GameState.Phase.IN_MATCH, display, name, "log");
        }
        return currentCodename;
    }
}
