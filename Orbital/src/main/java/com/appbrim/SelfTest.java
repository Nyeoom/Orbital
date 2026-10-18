package com.appbrim;

import com.appbrim.api.LineupsClient;
import com.appbrim.hotkey.GlobalHotkey;
import com.appbrim.model.Lineup;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Screen;

import javax.imageio.ImageIO;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@code Orbital.exe --selftest [report.txt]}: checks, inside the real packaged runtime, the
 * things that cannot be unit-tested and tend to break only in a trimmed jpackage build -
 * JavaFX natives, the WebP decoder, TLS to the lineup site, and a real search + image fetch.
 *
 * <p>Lines starting with PASS/FAIL are required (exit code 1 on FAIL); "info"/"WARN" lines are
 * live-site checks that depend on the network and are reported but never fail the run.
 */
public final class SelfTest {

    @FunctionalInterface
    private interface Step {
        String run() throws Exception;
    }

    public static int run(Path reportFile) {
        List<String> out = new ArrayList<>();
        boolean ok = true;

        out.add("Orbital self-test");
        out.add("java " + System.getProperty("java.version") + " on " + System.getProperty("os.name")
                + " " + System.getProperty("os.arch") + ", site " + LineupsClient.SITE);

        ok &= step(out, "javafx toolkit", true, SelfTest::javafx);
        ok &= step(out, "webp decoder registered", true, () -> {
            if (!ImageIO.getImageReadersByFormatName("webp").hasNext()) {
                throw new IllegalStateException("no ImageIO reader for webp");
            }
            return "ImageIO has a webp reader";
        });
        ok &= step(out, "https handshake", true, SelfTest::tls);

        LineupsClient client = new LineupsClient();
        AtomicReference<Lineup> first = new AtomicReference<>();
        step(out, "live search (Ascent, A Site, molly)", false, () -> {
            List<Lineup> r = client.search("Ascent", List.of(LineupsClient.ABILITY_MOLLY), null, "A Site",
                    LineupsClient.SIDE_ANY, "lineup", 3);
            if (r.isEmpty()) {
                throw new IllegalStateException("search returned no lineups");
            }
            first.set(r.get(0));
            return r.size() + " lineups, first: \"" + r.get(0).getTitle() + "\" (id " + r.get(0).getId() + ")";
        });
        step(out, "live thumbnail download + webp decode", false, () -> {
            Lineup l = first.get();
            if (l == null) {
                throw new IllegalStateException("skipped, no lineup from the search");
            }
            var img = client.fetchImage(LineupsClient.CDN + "/static/lineup_images_thumbnail/" + l.getId()
                    + "/" + Math.max(1, l.getNumImages()) + ".webp");
            return img.getWidth() + "x" + img.getHeight() + " px";
        });
        step(out, "live callout lookup", false, () -> {
            String c = client.callout("Ascent", 130, 100);
            return c == null ? "no callout at that point (endpoint reachable)" : c;
        });
        step(out, "global hotkey (F9) registration", false, () ->
                GlobalHotkey.register(0x78, () -> { }) ? "registered" : "not registered (no Windows desktop?)");

        out.add(ok ? "RESULT: PASS" : "RESULT: FAIL");
        String text = String.join(System.lineSeparator(), out) + System.lineSeparator();
        System.out.print(text);
        if (reportFile != null) {
            try {
                Files.writeString(reportFile, text, StandardCharsets.UTF_8);
            } catch (Exception e) {
                System.err.println("Could not write " + reportFile + ": " + e);
            }
        }
        return ok ? 0 : 1;
    }

    private static boolean step(List<String> out, String name, boolean required, Step s) {
        try {
            out.add((required ? "PASS " : "info ") + name + ": " + s.run());
            return true;
        } catch (Throwable t) {
            out.add((required ? "FAIL " : "WARN ") + name + ": " + t);
            return !required;
        }
    }

    /** Starts the JavaFX toolkit (loads the native pipeline) and builds a styled scene. */
    private static String javafx() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        if (!started.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("toolkit did not start within 30s");
        }
        CountDownLatch built = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<String> screen = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                Scene scene = new Scene(new Label("selftest"), 100, 50);
                var css = SelfTest.class.getResource("/theme.css");
                if (css == null) {
                    throw new IllegalStateException("theme.css missing from the package");
                }
                scene.getStylesheets().add(css.toExternalForm());
                var b = Screen.getPrimary().getBounds();
                screen.set((int) b.getWidth() + "x" + (int) b.getHeight());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                built.countDown();
            }
        });
        if (!built.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FX thread did not respond");
        }
        if (failure.get() != null) {
            throw new IllegalStateException(failure.get());
        }
        Platform.exit();
        return "toolkit started, scene + stylesheet ok, primary screen " + screen.get();
    }

    /** Any HTTP response counts: the point is that TLS (cipher suites, trust store) works. */
    private static String tls() throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        HttpResponse<Void> r = http.send(
                HttpRequest.newBuilder(URI.create(LineupsClient.SITE + "/")).timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "Mozilla/5.0 Orbital-selftest").GET().build(),
                HttpResponse.BodyHandlers.discarding());
        return "HTTP " + r.statusCode() + " from " + LineupsClient.SITE;
    }

    private SelfTest() {
    }
}
