package com.appbrim.detect;

import com.appbrim.Prefs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates Valorant's internal map codenames (as written to ShooterGame.log,
 * e.g. "Foxtrot" for Ascent) into the display names lineupsvalorant.com expects.
 * Learned corrections are stored in prefs so unknown/new maps only need to be
 * confirmed once.
 */
public final class MapCodenames {

    /** Built-in codename -> display name. Confidence-free guesses still get an entry. */
    private static final Map<String, String> BUILTIN = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("Foxtrot", "Ascent"),
            Map.entry("Duality", "Bind"),
            Map.entry("Ascent", "Haven"),      // yes: Haven's internal name really is "Ascent"
            Map.entry("Portals", "Split"),
            Map.entry("Canyon", "Breeze"),
            Map.entry("Port", "Icebox"),
            Map.entry("Polaris", "Fracture"),
            Map.entry("Cascades", "Pearl"),
            Map.entry("Cassia", "Lotus"),
            Map.entry("Fossil", "Sunset"),
            Map.entry("Coxel", "Abyss"),
            Map.entry("Drift", "Corrode"),
            Map.entry("Summit", "Summit")
    ));

    /** Non-playable "maps" seen in the log that must never surface as maps. */
    public static boolean isSystemLevel(String codename) {
        if (codename == null) {
            return true;
        }
        return switch (codename) {
            case "Init", "Login", "MainMenuV2", "CharacterSelectPersistentLevel", "LoadingScreen", "Menubar" -> true;
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

    /** True when the built-in mapping for this codename is a guess, not community-verified. */
    public static boolean isGuess(String codename) {
        return codename != null && switch (codename) {
            case "Coxel", "Drift", "Summit" -> true;
            default -> false;
        };
    }

    /** Remember a user-confirmed mapping (also fixes wrong built-in guesses). */
    public static void learn(String codename, String displayName) {
        Prefs.get().put("codename." + codename, displayName);
    }
}
