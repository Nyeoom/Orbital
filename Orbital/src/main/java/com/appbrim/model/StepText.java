package com.appbrim.model;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns the lineup site's HTML-ish step description into plain steps. */
public final class StepText {

    private static final Pattern BREAKS = Pattern.compile("(?i)<\\s*br\\s*/?\\s*>|</\\s*(p|div|li|ul|ol)\\s*>|<\\s*li\\s*>");
    private static final Pattern TAGS = Pattern.compile("<[^>]+>");
    private static final Pattern NUMERIC_ENTITY = Pattern.compile("&#(x?)([0-9a-fA-F]+);");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*\\d+\\s*[.)]\\s*");

    public static List<String> split(String description) {
        List<String> out = new ArrayList<>();
        if (description == null || description.isBlank()) {
            out.add("No step description provided for this lineup.");
            return out;
        }
        String text = BREAKS.matcher(description).replaceAll("\n");
        // Entities first (the site sometimes double-encodes tags as &lt;br&gt;), then strip tags.
        text = decodeEntities(text);
        text = BREAKS.matcher(text).replaceAll("\n");
        text = TAGS.matcher(text).replaceAll("");
        for (String part : text.split("\\R")) {
            String p = LEADING_NUMBER.matcher(part).replaceFirst("").trim();
            if (!p.isEmpty()) {
                out.add(p);
            }
        }
        if (out.isEmpty()) {
            out.add(text.trim().isEmpty() ? "No step description provided for this lineup." : text.trim());
        }
        return out;
    }

    static String decodeEntities(String s) {
        s = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&nbsp;", " ");
        Matcher m = NUMERIC_ENTITY.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            try {
                int cp = Integer.parseInt(m.group(2), m.group(1).isEmpty() ? 10 : 16);
                m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(cp))));
            } catch (RuntimeException e) {
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
            }
        }
        m.appendTail(sb);
        // &amp; last so "&amp;lt;" stays a literal "&lt;".
        return sb.toString().replace("&amp;", "&");
    }

    private StepText() {
    }
}
