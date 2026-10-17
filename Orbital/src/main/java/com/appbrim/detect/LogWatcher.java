package com.appbrim.detect;

import com.appbrim.model.GameState;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tails Valorant's ShooterGame.log to derive the live phase and map.
 * The game writes lines like:
 * <pre>
 * LogMapLoadModel: Update: [Map Name: Foxtrot | Changed: TRUE] [Local World: TRUE | Changed: TRUE]
 *   [Match Setup: FALSE | Changed: FALSE] ... [URL: 192.207.0.1:7107/Game/Maps/Foxtrot/Foxtrot?...BombGameMode...]
 * </pre>
 * "Foxtrot" is Breeze's internal codename; {@link MapCodenames} translates. This is the same
 * passive log-reading technique commercial companion apps use - nothing is injected
 * into or read from the game process.
 *
 * <p>On startup the tail of the existing log is scanned once, so launching Orbital in the
 * middle of a match still picks up the current map.
 */
public final class LogWatcher {

    public static final Path LOG_PATH = Path.of(System.getProperty("user.home"),
            "AppData", "Local", "Valorant", "Saved", "Logs", "ShooterGame.log");

    private static final Pattern MAP_LINE = Pattern.compile(
            "LogMapLoadModel: Update: \\[Map Name: (\\w+) \\|.*?\\[Local World: (\\w+).*?\\[Match Setup: (\\w+).*?\\[Map Complete: (\\w+).*?\\[URL: (\\S*)");

    private static final long STALE_AFTER_MS = 5 * 60 * 1000;
    /** How much of the end of an existing log to scan when we start up. */
    private static final long INITIAL_SCAN_BYTES = 8L * 1024 * 1024;

    /** One parsed LogMapLoadModel line. */
    public record MapEvent(String name, boolean localWorld, String url) { }

    private final GameState state;
    private final Path logPath;
    private final long pollMs;
    private Thread thread;
    private volatile boolean running;

    public LogWatcher(GameState state) {
        this(state, LOG_PATH, 1000);
    }

    /** Test hook: custom log file and polling interval. */
    public LogWatcher(GameState state, Path logPath, long pollMs) {
        this.state = state;
        this.logPath = logPath;
        this.pollMs = pollMs;
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

    // ------------------------------------------------------------------
    // Parsing (pure, unit-tested)
    // ------------------------------------------------------------------

    /** Parses one log line; returns null when it isn't a map-load line. */
    public static MapEvent parseMapLine(String line) {
        if (line == null || !line.contains("LogMapLoadModel")) {
            return null;
        }
        Matcher m = MAP_LINE.matcher(line);
        if (!m.find()) {
            return null;
        }
        return new MapEvent(m.group(1), "TRUE".equals(m.group(2)), m.group(5));
    }

    /**
     * Whether this event describes a state we should act on: any system level
     * (menu / agent select), or a playable map we have actually travelled into.
     */
    public static boolean isActionable(MapEvent ev) {
        if (MapCodenames.isSystemLevel(ev.name())) {
            return true;
        }
        return ev.localWorld() || ev.url().contains("/Game/Maps/" + ev.name() + "/" + ev.name());
    }

    /** Pushes an actionable event into the game state. */
    public static void apply(GameState state, MapEvent ev) {
        String name = ev.name();
        if (MapCodenames.isSystemLevel(name)) {
            GameState.Phase phase = switch (name) {
                case "CharacterSelectPersistentLevel" -> GameState.Phase.AGENT_SELECT;
                default -> GameState.Phase.MENU;
            };
            state.setPhaseAndMap(phase, null, null, "log");
            return;
        }
        state.setPhaseAndMap(GameState.Phase.IN_MATCH, MapCodenames.resolve(name), name, "log");
    }

    // ------------------------------------------------------------------
    // Watching
    // ------------------------------------------------------------------

    private void run() {
        long filePtr = 0;
        long lastModified = 0;
        FileTime created = null;

        // History matters only for the *current* state: scan the tail once.
        if (Files.exists(logPath)) {
            try {
                BasicFileAttributes attrs = Files.readAttributes(logPath, BasicFileAttributes.class);
                filePtr = attrs.size();
                lastModified = attrs.lastModifiedTime().toMillis();
                created = attrs.creationTime();
                boolean fresh = System.currentTimeMillis() - lastModified < STALE_AFTER_MS;
                state.setGameRunning(fresh);
                if (fresh) {
                    MapEvent last = lastActionableEvent(logPath, attrs.size());
                    if (last != null) {
                        apply(state, last);
                    }
                }
            } catch (IOException ignored) {
                // Fall through to normal polling.
            }
        }

        while (running) {
            try {
                Thread.sleep(pollMs);
            } catch (InterruptedException e) {
                return;
            }

            if (!Files.exists(logPath)) {
                state.setGameRunning(false);
                continue;
            }

            try {
                BasicFileAttributes attrs = Files.readAttributes(logPath, BasicFileAttributes.class);
                long len = attrs.size();
                boolean rotated = len < filePtr
                        || (created != null && !created.equals(attrs.creationTime()));
                created = attrs.creationTime();
                if (rotated) {
                    // Valorant restarted and replaced the log: read the new file from the top.
                    filePtr = 0;
                    state.setPhaseAndMap(GameState.Phase.MENU, null, null, "log");
                }

                lastModified = Math.max(lastModified, attrs.lastModifiedTime().toMillis());
                state.setGameRunning(System.currentTimeMillis() - lastModified < STALE_AFTER_MS);

                if (len > filePtr) {
                    try (RandomAccessFile raf = new RandomAccessFile(logPath.toFile(), "r")) {
                        raf.seek(filePtr);
                        byte[] buf = new byte[(int) Math.min(len - filePtr, Integer.MAX_VALUE)];
                        raf.readFully(buf);
                        filePtr += buf.length;
                        for (String line : new String(buf, StandardCharsets.UTF_8).split("\n")) {
                            MapEvent ev = parseMapLine(line);
                            if (ev != null && isActionable(ev)) {
                                apply(state, ev);
                            }
                        }
                    }
                } else if (!state.isGameRunning() && state.getPhase() != GameState.Phase.OFFLINE) {
                    // No fresh log output for a long while: game closed.
                    state.setPhaseAndMap(GameState.Phase.OFFLINE, null, null, null);
                }
            } catch (IOException ignored) {
                // File locked mid-rotation etc.; retry next tick.
            }
        }
    }

    /** Last state-changing map event in the final {@link #INITIAL_SCAN_BYTES} of the log. */
    static MapEvent lastActionableEvent(Path file, long length) throws IOException {
        long from = Math.max(0, length - INITIAL_SCAN_BYTES);
        byte[] buf;
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            raf.seek(from);
            buf = new byte[(int) (length - from)];
            raf.readFully(buf);
        }
        String text = new String(buf, StandardCharsets.UTF_8);
        MapEvent last = null;
        // Walk lines from the end of the text; the first actionable one wins.
        int end = text.length();
        while (end > 0) {
            int start = text.lastIndexOf('\n', end - 1);
            String line = text.substring(start + 1, end);
            MapEvent ev = parseMapLine(line);
            if (ev != null && isActionable(ev)) {
                last = ev;
                break;
            }
            end = start;
            if (start < 0) {
                break;
            }
        }
        return last;
    }
}
