package com.appbrim.detect;

import com.appbrim.Prefs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates Valorant's internal map codenames (as written to ShooterGame.log,
 * e.g. "Foxtrot" for Breeze) into the display names lineupsvalorant.com expects.
 * Learned corrections are stored in prefs so unknown/new maps only need to be
 * confirmed once.
 */
public final class MapCodenames {

    /**
     * Built-in codename -> display name. Only community-verified mappings live here.
     * Codenames not listed (e.g. newer maps) trigger the "which map is this?" bar once
     * and are then remembered in prefs - better than silently guessing wrong.
     */
    private static final Map<String, String> BUILTIN = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("Ascent", "Ascent"),
            Map.entry("Duality", "Bind"),
            Map.entry("Triad", "Haven"),
            Map.entry("Bonsai", "Split"),
            Map.entry("Port", "Icebox"),
            Map.entry("Foxtrot", "Breeze"),
            Map.entry("Canyon", "Fracture"),
            Map.entry("Pitt", "Pearl"),
            Map.entry("Jam", "Lotus"),
            Map.entry("Juliett", "Sunset")
    ));

    /** Non-playable "maps" seen in the log that must never surface as maps. */
    public static boolean isSystemLevel(String codename) {
        if (codename == null) {
            return true;
        }
        return switch (codename) {
            case "Init", "Login", "MainMenuV2", "CharacterSelectPersistentLevel", "LoadingScreen", "Menubar",
                 "Poveglia" /* The Range */ -> true;
            default -> false;
        };
    }

    /**
     * Resolve a codename to a display name.
     *
     * @return the display name, or null when unknown (caller should ask the user)
     */
    public static String resolve(String codename) {
        if (codename == null) {
            return null;
        }
        String learned = Prefs.get().getString("codename." + codename, null);
        if (learned != null && !learned.isBlank()) {
            return learned;
        }
        return BUILTIN.get(codename);
    }

    /** Remember a user-confirmed mapping (also fixes wrong built-in guesses). */
    public static void learn(String codename, String displayName) {
        Prefs.get().put("codename." + codename, displayName);
    }
}
