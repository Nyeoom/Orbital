package com.appbrim;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stand-in for lineupsvalorant.com used by the tests and for driving the GUI offline:
 * same endpoints and JSON shapes (get_results, get_lineup, get_callout/) plus generated
 * images on the "CDN" paths. Run with {@code -Dorbital.site=http://127.0.0.1:8099
 * -Dorbital.cdn=http://127.0.0.1:8099} to point Orbital at it.
 *
 * <p>Callout geometry (0..500 space): x &lt; 250 &amp;&amp; y &lt; 250 = "A Site", x &gt;= 250 &amp;&amp; y &lt; 250 = "B Site",
 * x &lt; 250 &amp;&amp; y &gt;= 250 = "A Main", the rest is undefined.
 */
public final class MockSite implements AutoCloseable {

    public static final String[] STARTS = {"A Main", "A Link", "B Window", "Mid", "Spawn"};

    private final HttpServer server;
    private final Gson gson = new Gson();
    public final AtomicInteger resultsCalls = new AtomicInteger();
    public final AtomicInteger calloutCalls = new AtomicInteger();
    /** When > 0, the next N get_results calls answer 500 (to test retry / error UI). */
    public volatile int failNextResults;

    public MockSite(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/get_results", ex -> {
            resultsCalls.incrementAndGet();
            if (failNextResults > 0) {
                failNextResults--;
                respond(ex, 500, "text/plain", "boom".getBytes(StandardCharsets.UTF_8));
                return;
            }
            JsonObject req = body(ex);
            respondJson(ex, results(req));
        });
        server.createContext("/get_lineup", ex -> respondJson(ex, lineup(body(ex).get("id").getAsLong())));
        server.createContext("/get_callout/", ex -> {
            calloutCalls.incrementAndGet();
            JsonObject req = body(ex);
            JsonObject out = new JsonObject();
            out.addProperty("callout", callout(req.get("x").getAsInt(), req.get("y").getAsInt()));
            respondJson(ex, out);
        });
        server.createContext("/static/", ex -> {
            String[] parts = ex.getRequestURI().getPath().split("/");
            // /static/<kind>/<id>/<n>.webp
            long id = Long.parseLong(parts[3]);
            int n = Integer.parseInt(parts[4].replace(".webp", ""));
            boolean thumb = parts[2].contains("thumbnail");
            respond(ex, 200, "image/png", image(id, n, thumb ? 320 : 1280, thumb ? 180 : 720));
        });
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String url() {
        return "http://127.0.0.1:" + port();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // ------------------------------------------------------------------

    static String callout(int x, int y) {
        if (x < 250 && y < 250) {
            return "A Site";
        }
        if (x >= 250 && y < 250) {
            return "B Site";
        }
        if (x < 250) {
            return "A Main";
        }
        return "Undefined";
    }

    private JsonObject results(JsonObject req) {
        String map = req.get("map").getAsString();
        String end = str(req, "end");
        String start = str(req, "start");
        List<String> abilities = new ArrayList<>();
        for (var a : req.getAsJsonArray("abilities")) {
            abilities.add(a.getAsString());
        }
        int num = req.get("num_results").getAsInt();
        int off = req.get("start_results").getAsInt();

        List<JsonObject> all = new ArrayList<>();
        String[] ends = end != null ? new String[]{end} : new String[]{"A Site", "B Site"};
        long id = 1000;
        for (String e : ends) {
            for (String ab : abilities) {
                for (String s : STARTS) {
                    // A few lineups per (start, ability, end) so paging has something to do.
                    for (int k = 1; k <= 3; k++) {
                        id++;
                        if (start != null && !start.equals(s)) {
                            continue;
                        }
                        JsonObject o = new JsonObject();
                        o.addProperty("id", idOf(map, e, ab, s, k));
                        o.addProperty("title", ab + " " + s + " to " + e + " #" + k);
                        o.addProperty("map", map);
                        o.addProperty("start", s);
                        o.addProperty("end", e);
                        o.addProperty("abilities", ab);
                        o.addProperty("num_images", 3);
                        o.addProperty("score", 100 - k * 7 + s.length());
                        o.addProperty("unlisted", 0);
                        o.addProperty("type", "lineup");
                        all.add(o);
                    }
                }
            }
        }
        JsonArray page = new JsonArray();
        for (int i = off; i < Math.min(all.size(), off + num); i++) {
            page.add(all.get(i));
        }
        JsonObject out = new JsonObject();
        out.add("results", page);
        return out;
    }

    /** Missing or null both mean "no filter" - the real site accepts either. */
    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static long idOf(String map, String end, String ab, String start, int k) {
        return Math.abs((map + end + ab + start + k).hashCode()) % 900000L + 1000;
    }

    private JsonObject lineup(long id) {
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("lineup_id", id);
        o.addProperty("title", "Mock lineup " + id);
        o.addProperty("map", "Breeze");
        o.addProperty("start", "A Main");
        o.addProperty("end", "A Site");
        o.addProperty("description",
                "1. Stand at the corner of the box<br>2. Aim at the top-left edge of the tower<br>"
                        + "3. Jump-throw with right click &amp; hold<br>&lt;br&gt;4. Lands on default plant");
        o.addProperty("username", "mock_user");
        o.addProperty("images", 3);
        o.addProperty("views", 1234);
        o.addProperty("like_count", 56);
        o.addProperty("error", "none");
        return o;
    }

    private static byte[] image(long id, int n, int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float hue = (id % 360) / 360f;
        g.setPaint(new java.awt.GradientPaint(0, 0, Color.getHSBColor(hue, 0.55f, 0.55f),
                w, h, Color.getHSBColor((hue + 0.15f) % 1f, 0.7f, 0.25f)));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(255, 255, 255, 60));
        g.fillOval(w / 2 - h / 3 + n * 30, h / 2 - h / 3, 2 * h / 3, 2 * h / 3);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, h / 6));
        g.drawString("lineup " + id, w / 20, h / 4);
        g.drawString("image " + n, w / 20, h / 2 + h / 10);
        g.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        return bos.toByteArray();
    }

    private JsonObject body(HttpExchange ex) throws IOException {
        return gson.fromJson(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), JsonObject.class);
    }

    private void respondJson(HttpExchange ex, JsonObject o) throws IOException {
        respond(ex, 200, "application/json", gson.toJson(o).getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange ex, int code, String type, byte[] data) throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(code, data.length);
        ex.getResponseBody().write(data);
        ex.close();
    }

    /** Dev entry point: {@code java -cp ... com.appbrim.MockSite 8099}. */
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8099;
        MockSite m = new MockSite(port);
        System.out.println("MockSite listening on " + m.url());
        Thread.currentThread().join();
    }
}
