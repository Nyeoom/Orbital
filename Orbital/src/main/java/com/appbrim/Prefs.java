package com.appbrim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/** Small JSON-backed preferences store kept next to the cache in %LOCALAPPDATA%. */
public final class Prefs {

    private static final Path DIR = Path.of(System.getProperty("user.home"), "AppData", "Local", "AppBrim");
    private static final Path FILE = DIR.resolve("prefs.json");

    private final Map<String, Object> values = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static Prefs instance;

    public static synchronized Prefs get() {
        if (instance == null) {
            instance = new Prefs();
        }
        return instance;
    }

    private Prefs() {
        try {
            if (Files.exists(FILE)) {
                Type t = new TypeToken<Map<String, Object>>() { }.getType();
                Map<String, Object> loaded = gson.fromJson(Files.readString(FILE), t);
                if (loaded != null) {
                    values.putAll(loaded);
                }
            }
        } catch (Exception e) {
            // Corrupt prefs should never brick the app.
        }
    }

    public synchronized String getString(String key, String def) {
        Object v = values.get(key);
        return v == null ? def : String.valueOf(v);
    }

    public synchronized boolean getBool(String key, boolean def) {
        Object v = values.get(key);
        return v instanceof Boolean b ? b : def;
    }

    public synchronized int getInt(String key, int def) {
        Object v = values.get(key);
        return v instanceof Number n ? n.intValue() : def;
    }

    public synchronized double getDouble(String key, double def) {
        Object v = values.get(key);
        return v instanceof Number n ? n.doubleValue() : def;
    }

    public synchronized void put(String key, Object value) {
        values.put(key, value);
        save();
    }

    /** Removes a key if present; used to invalidate learned codenames. */
    public synchronized void remove(String key) {
        if (values.remove(key) != null) {
            save();
        }
    }

    /** Written via a temp file + move so a crash mid-write can't corrupt the settings. */
    private void save() {
        try {
            Files.createDirectories(DIR);
            Path tmp = DIR.resolve("prefs.json.tmp");
            Files.writeString(tmp, gson.toJson(values));
            try {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // Best effort only.
        }
    }
}
