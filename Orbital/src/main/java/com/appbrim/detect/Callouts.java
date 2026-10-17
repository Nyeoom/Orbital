package com.appbrim.detect;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure helpers for interpreting callout names returned by lineupsvalorant.com. */
public final class Callouts {

    /** "A", "A Site", "B Long", "C Main" ... but not "Arch", "Attackers Spawn" or "Back Site". */
    private static final Pattern LETTER = Pattern.compile("^\\s*([ABC])(?=\\s|$)");

    /** Site letter (A/B/C) a callout belongs to, or null when it isn't a lettered callout. */
    public static String siteLetter(String callout) {
        if (callout == null) {
            return null;
        }
        Matcher m = LETTER.matcher(callout);
        return m.find() ? m.group(1) : null;
    }

    /** The lineup site's "end" filter value for a planted-spike callout ("A Site"), or null. */
    public static String endFor(String plantCallout) {
        String letter = siteLetter(plantCallout);
        return letter == null ? null : letter + " Site";
    }

    private Callouts() {
    }
}
