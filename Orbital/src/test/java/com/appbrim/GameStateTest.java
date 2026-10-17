package com.appbrim;

import com.appbrim.model.GameState;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GameStateTest {

    @Test
    void unknownCodenameIsKeptEvenWhenComingFromTheMenu() {
        // Regression: the confirm bar needs the codename, but the map stays null.
        GameState s = new GameState();
        s.setPhaseAndMap(GameState.Phase.MENU, null, null, "log");
        s.setPhaseAndMap(GameState.Phase.IN_MATCH, null, "NewMap", "log");
        assertNull(s.getMap());
        assertEquals("NewMap", s.getMapCodename());
        assertEquals(GameState.Phase.IN_MATCH, s.getPhase());
    }

    @Test
    void changingMapForgetsRoundData() {
        GameState s = new GameState();
        s.setPhaseAndMap(GameState.Phase.IN_MATCH, "Breeze", "Foxtrot", "log");
        s.setPlant("A Site", "A", 120, 130);
        s.setPlayer("A Main", 90, 400);
        s.setPhaseAndMap(GameState.Phase.IN_MATCH, "Haven", "Triad", "log");
        assertNull(s.plantCallout);
        assertNull(s.playerCallout);
        assertEquals(-1, s.spikeX);
    }

    @Test
    void sameStateDoesNotFireListeners() {
        GameState s = new GameState();
        AtomicInteger fired = new AtomicInteger();
        s.addListener(x -> fired.incrementAndGet());
        s.setPhaseAndMap(GameState.Phase.IN_MATCH, "Breeze", "Foxtrot", "log");
        s.setPhaseAndMap(GameState.Phase.IN_MATCH, "Breeze", "Foxtrot", "log");
        assertEquals(1, fired.get());
    }

    @Test
    void clearPlantKeepsTheMap() {
        GameState s = new GameState();
        s.setPhaseAndMap(GameState.Phase.IN_MATCH, "Breeze", "Foxtrot", "log");
        s.setPlant("B Site", "B", 300, 100);
        s.clearPlant();
        assertNull(s.plantCallout);
        assertNull(s.plantSite);
        assertEquals("Breeze", s.getMap());
    }

    @Test
    void manualMapOverridesAndResets() {
        GameState s = new GameState();
        s.setPhaseAndMap(GameState.Phase.IN_MATCH, "Breeze", "Foxtrot", "log");
        s.setPlant("A Site", "A", 1, 1);
        s.setManualMap("Ascent");
        assertEquals("Ascent", s.getMap());
        assertEquals("manual", s.getMapSource());
        assertNull(s.plantCallout);
    }
}
