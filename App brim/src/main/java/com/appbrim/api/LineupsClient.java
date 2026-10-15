package com.appbrim.api;

import com.appbrim.model.Lineup;
import com.appbrim.model.LineupDetail;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Client for the unofficial lineupsvalorant.com JSON endpoints the site's own
 * search page uses. Verified 2026-10: no auth needed, JSON in / JSON out.
 *
 * <ul>
 *   <li>POST /get_results  - search lineups (map, abilities, start, end, side, type)</li>
 *   <li>POST /get_lineup   - full detail incl. step description and image count</li>
 *   <li>POST /get_callout/ - map a 0..500 minimap x/y to a callout name ("A Site")</li>
 * </ul>
 * Images live on the CDN: https://lineupsvalorant.b-cdn.net/static/...
 *
 * <p>Requests are throttled, time out, retry once on transient failures, and search
 * results / callouts are cached in memory so flipping filters back and forth is instant
 * and the site isn't hit repeatedly. {@code -Dorbital.site=...} and {@code -Dorbital.cdn=...}
 * point the client at another host (used by the tests and the mock server).
 */
public final class LineupsClient {

    public static final String SITE = System.getProperty("orbital.site", "https://lineupsvalorant.com");
    public static final String CDN = System.getProperty("orbital.cdn", "https://lineupsvalorant.b-cdn.net");

    /** Site-side ability filter values (Brimstone). */
    public static final String ABILITY_MOLLY = "Incendiary";
    public static final String ABILITY_SMOKE = "Sky Smoke";

    /** Side filter values, matching the site's search toggles (0=All, 1=Defense, 2=Attack). */
    public static final int SIDE_ANY = 0;
    public static final int SIDE_DEFENSE = 1;
    public static final int SIDE_ATTACK = 2;

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppBrim/1.0 (lineup assistant)";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final long SEARCH_TTL_MS = 10 * 60 * 1000;
    private static final int CALLOUT_CACHE_MAX = 512;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Gson gson = new Gson();
    /**
     * Request bodies keep explicit nulls ("start": null): that is exactly what the site's own
     * page sends (and what was verified with curl). Plain Gson would silently drop the keys.
     */
    private final Gson requestGson = new GsonBuilder().serializeNulls().create();
    private long lastRequestAt;

    private record CachedSearch(long at, List<Lineup> results) { }

    private final Map<String, CachedSearch> searchCache = new LinkedHashMap<>();
    /** Value "" means "looked it up, no callout there". */
    private final Map<String, String> calloutCache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CALLOUT_CACHE_MAX;
        }
    };

    /** Search lineups (cached for a few minutes). Nullable: start, end. */
    public List<Lineup> search(String map, List<String> abilities, String start, String end,
                               int side, String type, int limit) throws IOException, InterruptedException {
        String key = String.join("|", map, String.join(",", abilities), String.valueOf(start),
                String.valueOf(end), String.valueOf(side), type, String.valueOf(limit));
        synchronized (searchCache) {
            CachedSearch hit = searchCache.get(key);
            if (hit != null && System.currentTimeMillis() - hit.at() < SEARCH_TTL_MS) {
                return new ArrayList<>(hit.results());
            }
        }
        List<Lineup> fresh = searchUncached(map, abilities, start, end, side, type, limit);
        synchronized (searchCache) {
            searchCache.put(key, new CachedSearch(System.currentTimeMillis(), List.copyOf(fresh)));
        }
        return fresh;
    }

    /** Drops cached search results so the next search goes to the site (Refresh button). */
    public void invalidateSearchCache() {
        synchronized (searchCache) {
            searchCache.clear();
        }
    }

    private List<Lineup> searchUncached(String map, List<String> abilities, String start, String end,
                                        int side, String type, int limit) throws IOException, InterruptedException {
        List<Lineup> all = new ArrayList<>();
        int pageSize = Math.min(Math.max(limit, 1), 20);
        int offset = 0;
        while (all.size() < limit) {
            JsonObject body = new JsonObject();
            body.addProperty("map", map);
            JsonArray ab = new JsonArray();
            abilities.forEach(ab::add);
            body.add("abilities", ab);
            body.addProperty("start", start);
            body.addProperty("end", end);
            body.addProperty("type", type);
            body.addProperty("side", side);
            body.addProperty("num_results", pageSize);
            body.addProperty("start_results", offset);
            body.addProperty("user_token", (String) null);

            JsonObject resp = post("get_results", body);
            Lineup[] page = resp.has("results") && resp.get("results").isJsonArray()
                    ? gson.fromJson(resp.getAsJsonArray("results"), Lineup[].class)
                    : null;
            if (page == null || page.length == 0) {
                break;
            }
            all.addAll(List.of(page));
            offset += page.length;
            if (page.length < pageSize) {
                break;
            }
        }
        return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
    }

    /** Fetch full detail for one lineup id. */
    public LineupDetail lineup(long id) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("id", id);
        body.addProperty("user_token", (String) null);
        LineupDetail d = gson.fromJson(post("get_lineup", body), LineupDetail.class);
        if (d == null) {
            throw new IOException("Empty response for lineup " + id);
        }
        if (d.getError() != null && !"none".equals(d.getError())) {
            throw new IOException("get_lineup error: " + d.getError());
        }
        return d;
    }

    /** Map a 0..500 minimap coordinate to a callout; null when the point has no callout. */
    public String callout(String map, double x, double y) throws IOException, InterruptedException {
        // The endpoint only matches integer coordinates (350.0 yields "Undefined").
        long rx = Math.round(x);
        long ry = Math.round(y);
        String key = map + ":" + rx + ":" + ry;
        synchronized (calloutCache) {
            String hit = calloutCache.get(key);
            if (hit != null) {
                return hit.isEmpty() ? null : hit;
            }
        }
        JsonObject body = new JsonObject();
        body.addProperty("map", map);
        body.addProperty("x", rx);
        body.addProperty("y", ry);
        JsonObject resp = post("get_callout/", body);
        String c = resp.has("callout") && !resp.get("callout").isJsonNull() ? resp.get("callout").getAsString() : null;
        if ("Undefined".equals(c)) {
            c = null;
        }
        synchronized (calloutCache) {
            calloutCache.put(key, c == null ? "" : c);
        }
        return c;
    }

    /** Fetch an image (through the disk cache) and decode it, webp included. */
    public BufferedImage fetchImage(String url) throws IOException, InterruptedException {
        byte[] data = ImageCache.get().get(url);
        if (data == null) {
            data = download(url);
            ImageCache.get().put(url, data);
        }
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(data));
        if (img == null) {
            throw new IOException("No decoder for image at " + url);
        }
        return img;
    }

    public byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", UA)
                .GET()
                .build();
        HttpResponse<byte[]> resp = sendWithRetry(false, () -> http.send(req, HttpResponse.BodyHandlers.ofByteArray()));
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        }
        return resp.body();
    }

    private JsonObject post(String path, JsonObject body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(SITE + "/" + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("User-Agent", UA)
                .header("Origin", SITE)
                .header("Referer", SITE + "/")
                .POST(HttpRequest.BodyPublishers.ofString(requestGson.toJson(body)))
                .build();
        HttpResponse<String> resp = sendWithRetry(true, () -> http.send(req, HttpResponse.BodyHandlers.ofString()));
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP " + resp.statusCode() + " from " + path);
        }
        JsonObject json = gson.fromJson(resp.body(), JsonObject.class);
        if (json == null) {
            throw new IOException("Empty response from " + path);
        }
        return json;
    }

    @FunctionalInterface
    private interface Send<T> {
        HttpResponse<T> send() throws IOException, InterruptedException;
    }

    /**
     * One retry after a short pause on I/O errors, 429 and 5xx; everything else is final.
     * Only the site's JSON API is throttled; the image CDN can take parallel downloads.
     */
    private <T> HttpResponse<T> sendWithRetry(boolean throttled, Send<T> send) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            if (throttled) {
                throttle();
            }
            try {
                HttpResponse<T> resp = send.send();
                int code = resp.statusCode();
                if ((code == 429 || code >= 500) && attempt == 0) {
                    Thread.sleep(700);
                    continue;
                }
                return resp;
            } catch (IOException e) {
                last = e;
                if (attempt == 0) {
                    Thread.sleep(700);
                }
            }
        }
        throw last != null ? last : new IOException("request failed");
    }

    /** Be polite to a small fan-run site: at least 250ms between requests. */
    private synchronized void throttle() throws InterruptedException {
        long wait = 250 - (System.currentTimeMillis() - lastRequestAt);
        if (wait > 0) {
            Thread.sleep(wait);
        }
        lastRequestAt = System.currentTimeMillis();
    }
}
