package com.appbrim.model;

import com.appbrim.Prefs;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lineups the user starred. Whole lineup rows are stored (not just ids) so the
 * Saved view works instantly and offline.
 */
public final class Favorites {

    private static final String KEY = "favorites";

    private final Gson gson = new Gson();
    private final Map<Long, Lineup> byId = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public Favorites() {
        String json = Prefs.get().getString(KEY, null);
        if (json == null || json.isBlank()) {
            return;
        }
        try {
            Lineup[] saved = gson.fromJson(json, Lineup[].class);
            if (saved != null) {
                for (Lineup l : saved) {
                    if (l != null && l.getId() != 0) {
                        byId.put(l.getId(), l);
                    }
                }
            }
        } catch (Exception ignored) {
            // A damaged favorites blob just means an empty list.
        }
    }

    public synchronized boolean contains(long id) {
        return byId.containsKey(id);
    }

    public synchronized int size() {
        return byId.size();
    }

    /** Most recently saved first. */
    public synchronized List<Lineup> all() {
        List<Lineup> out = new ArrayList<>(byId.values());
        Collections.reverse(out);
        return out;
    }

    /** Adds or removes the lineup. Returns true when it is now a favorite. */
    public boolean toggle(Lineup lineup) {
        boolean now;
        List<Runnable> toFire;
        synchronized (this) {
            if (byId.remove(lineup.getId()) != null) {
                now = false;
            } else {
                byId.put(lineup.getId(), lineup);
                now = true;
            }
            Prefs.get().put(KEY, gson.toJson(byId.values()));
            toFire = new ArrayList<>(listeners);
        }
        toFire.forEach(Runnable::run);
        return now;
    }

    public synchronized void addListener(Runnable r) {
        listeners.add(r);
    }
}
