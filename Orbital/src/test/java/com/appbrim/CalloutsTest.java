package com.appbrim;

import com.appbrim.detect.Callouts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CalloutsTest {

    @Test
    void lettersFromSiteAndLetteredCallouts() {
        assertEquals("A", Callouts.siteLetter("A Site"));
        assertEquals("B", Callouts.siteLetter("B Long"));
        assertEquals("C", Callouts.siteLetter("C Main"));
        assertEquals("A", Callouts.siteLetter("A"));
        assertEquals("B", Callouts.siteLetter("  B Site"));
    }

    @Test
    void wordsThatMerelyStartWithALetterAreNotSites() {
        assertNull(Callouts.siteLetter("Arch"));
        assertNull(Callouts.siteLetter("Attackers Spawn"));
        assertNull(Callouts.siteLetter("Back Site"));
        assertNull(Callouts.siteLetter("Courtyard"));
        assertNull(Callouts.siteLetter("Mid"));
        assertNull(Callouts.siteLetter(""));
        assertNull(Callouts.siteLetter(null));
    }

    @Test
    void endFilterValue() {
        assertEquals("A Site", Callouts.endFor("A Site"));
        assertEquals("B Site", Callouts.endFor("B Garden"));
        assertNull(Callouts.endFor("unknown (12,40)"));
        assertNull(Callouts.endFor(null));
    }
}
