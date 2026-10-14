package com.appbrim.api;

import com.appbrim.model.Lineup;
import com.appbrim.model.LineupDetail;
import com.google.gson.Gson;
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
import java.util.List;

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
 */
public final class LineupsClient {

    public static final String SITE = "https://lineupsvalorant.com";
    public static final String CDN = "https://lineupsvalorant.b-cdn.net";

    /** Site-side ability filter values (Brimstone). */
    public static final String ABILITY_MOLLY = "Incendiary";
    public static final String ABILITY_SMOKE = "Sky Smoke";

    /** Side filter values, matching the site's search toggles (0=All, 1=Defense, 2=Attack). */
    public static final int SIDE_ANY = 0;
    public static final int SIDE_DEFENSE = 1;
    public static final int SIDE_ATTACK = 2;

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppBrim/1.0 (lineup assistant)";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final Gson gson = new Gson();
    private long lastRequestAt;

    /** Search lineups. All filter params nullable except map/abilities. */
    public List<Lineup> search(String map, List<String> abilities, String start, String end,
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
            Lineup[] page = gson.fromJson(resp.getAsJsonArray("results"), Lineup[].class);
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
        if (d != null && d.getError() != null && !"none".equals(d.getError())) {
            throw new IOException("get_lineup error: " + d.getError());
        }
        return d;
    }

    /** Map a 0..500 minimap coordinate to a callout; null when the point has no callout. */
    public String callout(String map, double x, double y) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("map", map);
        // The endpoint only matches integer coordinates (350.0 yields "Undefined").
        body.addProperty("x", Math.round(x));
        body.addProperty("y", Math.round(y));
        JsonObject resp = post("get_callout/", body);
        String c = resp.has("callout") && !resp.get("callout").isJsonNull() ? resp.get("callout").getAsString() : null;
        return "Undefined".equals(c) ? null : c;
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
                .header("User-Agent", UA)
                .GET()
                .build();
        throttle();
        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP " + resp.statusCode() + " for " + url);
        }
        return resp.body();
    }

    private JsonObject post(String path, JsonObject body) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(SITE + "/" + path))
                .header("Content-Type", "application/json")
                .header("User-Agent", UA)
                .header("Origin", SITE)
                .header("Referer", SITE + "/")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        throttle();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("HTTP " + resp.statusCode() + " from " + path + ": " + resp.body());
        }
        return gson.fromJson(resp.body(), JsonObject.class);
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
