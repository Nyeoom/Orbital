package com.appbrim;

import com.appbrim.model.StepText;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StepTextTest {

    @Test
    void splitsBreaksAndStripsNumbering() {
        assertEquals(List.of("Stand here", "Aim there", "Throw"),
                StepText.split("1. Stand here<br>2) Aim there<br />3. Throw"));
    }

    @Test
    void decodesEntitiesIncludingDoubleEncodedBreaks() {
        assertEquals(List.of("Jump & throw", "It's \"easy\"", "Next"),
                StepText.split("Jump &amp; throw&lt;br&gt;It&#39;s &quot;easy&quot;<br>Next"));
    }

    @Test
    void stripsTagsAndHandlesParagraphs() {
        assertEquals(List.of("One", "Two"), StepText.split("<p>One</p><p><b>Two</b></p>"));
    }

    @Test
    void numericHexEntities() {
        assertEquals(List.of("A\u2192B"), StepText.split("A&#x2192;B"));
    }

    @Test
    void blankInputGivesPlaceholder() {
        assertEquals(List.of("No step description provided for this lineup."), StepText.split(null));
        assertEquals(List.of("No step description provided for this lineup."), StepText.split("   "));
    }
}
