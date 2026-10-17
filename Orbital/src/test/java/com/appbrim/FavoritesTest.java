package com.appbrim;

import com.appbrim.model.Favorites;
import com.appbrim.model.Lineup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FavoritesTest {

    private static Lineup lineup(long id) {
        return new Lineup(id, "Lineup " + id, "Breeze", "A Main", "A Site", "Incendiary", 3, 10);
    }

    @BeforeEach
    void clean() {
        Prefs.get().remove("favorites");
    }

    @Test
    void toggleAddsAndRemoves() {
        Favorites f = new Favorites();
        assertTrue(f.toggle(lineup(1)));
        assertTrue(f.contains(1));
        assertEquals(1, f.size());
        assertFalse(f.toggle(lineup(1)));
        assertFalse(f.contains(1));
    }

    @Test
    void newestFirstAndPersistedAcrossInstances() {
        Favorites f = new Favorites();
        f.toggle(lineup(1));
        f.toggle(lineup(2));
        f.toggle(lineup(3));

        Favorites reloaded = new Favorites();
        assertEquals(3, reloaded.size());
        assertEquals(3, reloaded.all().get(0).getId());
        assertEquals("Lineup 1", reloaded.all().get(2).getTitle());
        assertEquals("A Main", reloaded.all().get(0).getStart());
    }

    @Test
    void listenersAreNotified() {
        Favorites f = new Favorites();
        AtomicInteger n = new AtomicInteger();
        f.addListener(n::incrementAndGet);
        f.toggle(lineup(5));
        f.toggle(lineup(5));
        assertEquals(2, n.get());
    }

    @Test
    void damagedStoredDataMeansEmptyNotACrash() {
        Prefs.get().put("favorites", "{not json");
        assertEquals(0, new Favorites().size());
    }
}
