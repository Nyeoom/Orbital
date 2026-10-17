package com.appbrim;

import com.appbrim.detect.MapCodenames;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapCodenamesTest {

    @Test
    void verifiedCodenames() {
        assertEquals("Breeze", MapCodenames.resolve("Foxtrot"));
        assertEquals("Fracture", MapCodenames.resolve("Canyon"));
        assertEquals("Ascent", MapCodenames.resolve("Ascent"));
        assertEquals("Haven", MapCodenames.resolve("Triad"));
        assertEquals("Split", MapCodenames.resolve("Bonsai"));
        assertEquals("Icebox", MapCodenames.resolve("Port"));
        assertEquals("Bind", MapCodenames.resolve("Duality"));
        assertEquals("Pearl", MapCodenames.resolve("Pitt"));
        assertEquals("Lotus", MapCodenames.resolve("Jam"));
        assertEquals("Sunset", MapCodenames.resolve("Juliett"));
    }

    @Test
    void unknownCodenamesAreNotGuessed() {
        assertNull(MapCodenames.resolve("BrandNewMap"));
        assertNull(MapCodenames.resolve(null));
    }

    @Test
    void learnedMappingWinsAndIsRemembered() {
        assertNull(MapCodenames.resolve("TestOnlyCodename"));
        MapCodenames.learn("TestOnlyCodename", "Abyss");
        assertEquals("Abyss", MapCodenames.resolve("TestOnlyCodename"));
    }

    @Test
    void systemLevelsAreNeverMaps() {
        assertTrue(MapCodenames.isSystemLevel("MainMenuV2"));
        assertTrue(MapCodenames.isSystemLevel("CharacterSelectPersistentLevel"));
        assertTrue(MapCodenames.isSystemLevel("Poveglia"));
        assertFalse(MapCodenames.isSystemLevel("Foxtrot"));
    }
}
