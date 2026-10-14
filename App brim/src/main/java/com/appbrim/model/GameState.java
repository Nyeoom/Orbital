package com.appbrim.model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Live snapshot of what we believe the game state to be, assembled from the
 * Valorant log (map / phase) and screen analysis (spike + player position).
 * Updated from background threads; listeners are invoked as-is, so UI listeners
 * must hop to the FX thread themselves.
 */
public class GameState {

    public enum Phase { OFFLINE, MENU, AGENT_SELECT, IN_MATCH }

    /** A/B/C bombsite letter, or null when unknown. */
    public volatile String plantSite;
    /** Full callout of the planted spike, e.g. "A Site". */
    public volatile String plantCallout;
    /** Callout where the player currently stands, e.g. "A Main". */
    public volatile String playerCallout;
    /** Spike position in the site's 0..500 minimap space. */
    public volatile double spikeX = -1, spikeY = -1;
    /** Player position in the same 0..500 space. */
    public volatile double playerX = -1, playerY = -1;

    private volatile Phase phase = Phase.OFFLINE;
    /** Display name of the current map ("Ascent") or null. */
    private volatile String map;
    /** Where the map value came from: "log", "manual" or null. */
    private volatile String mapSource;
    /** Internal log codename of the current map ("Foxtrot"), for confirmation UIs. */
    private volatile String mapCodename;
    /** True while the Valorant process appears to be writing to its log. */
    private volatile boolean gameRunning;

    public interface Listener {
        void onChanged(GameState state);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public void addListener(Listener l) {
        listeners.add(l);
    }

    private void fireChanged() {
        for (Listener l : listeners) {
            l.onChanged(this);
        }
    }

    public Phase getPhase() {
        return phase;
    }

    public String getMap() {
        return map;
    }

    public String getMapSource() {
        return mapSource;
    }

    public String getMapCodename() {
        return mapCodename;
    }

    public boolean isGameRunning() {
        return gameRunning;
    }

    public void setPhaseAndMap(Phase newPhase, String newMap, String codename, String source) {
        boolean phaseChanged = this.phase != newPhase;
        boolean mapChanged = !eq(this.map, newMap);
        if (!phaseChanged && !mapChanged) {
            return;
        }
        this.phase = newPhase;
        if (mapChanged) {
            this.map = newMap;
            this.mapSource = source;
            this.mapCodename = codename;
            // A different map invalidates anything we knew about the spike/plant.
            this.plantSite = null;
            this.plantCallout = null;
            this.playerCallout = null;
            this.spikeX = -1;
            this.spikeY = -1;
            this.playerX = -1;
            this.playerY = -1;
        }
        fireChanged();
    }

    public void setGameRunning(boolean running) {
        if (gameRunning != running) {
            gameRunning = running;
            fireChanged();
        }
    }

    public void setManualMap(String newMap) {
        if (!eq(this.map, newMap)) {
            this.map = newMap;
            this.mapSource = "manual";
            this.plantSite = null;
            this.plantCallout = null;
            this.playerCallout = null;
        }
        fireChanged();
    }

    public void setPlant(String callout, String siteLetter, double x, double y) {
        this.plantCallout = callout;
        this.plantSite = siteLetter;
        this.spikeX = x;
        this.spikeY = y;
        fireChanged();
    }

    public void setPlayer(String callout, double x, double y) {
        this.playerCallout = callout;
        this.playerX = x;
        this.playerY = y;
        fireChanged();
    }

    private static boolean eq(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
