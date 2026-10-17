package com.appbrim;

import com.appbrim.api.LineupsClient;
import com.appbrim.model.Lineup;
import com.appbrim.model.LineupDetail;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real client against {@link MockSite} (the site is configured via system properties). */
class LineupsClientTest {

    private static MockSite site;
    private LineupsClient client;

    @BeforeAll
    static void start() throws Exception {
        site = new MockSite(0);
        System.setProperty("orbital.site", site.url());
        System.setProperty("orbital.cdn", site.url());
    }

    @AfterAll
    static void stop() {
        site.close();
    }

    @BeforeEach
    void fresh() {
        // SITE/CDN are read when the class loads, so the first test class to touch LineupsClient wins;
        // fail loudly if another test loaded it before MockSite was configured.
        assertEquals(site.url(), LineupsClient.SITE, "LineupsClient loaded before the mock was configured");
        client = new LineupsClient();
        site.resultsCalls.set(0);
        site.calloutCalls.set(0);
        site.failNextResults = 0;
    }

    @Test
    void searchFiltersByEndAndStart() throws Exception {
        List<Lineup> all = client.search("Breeze", List.of("Incendiary"), null, "A Site", 0, "lineup", 60);
        assertEquals(MockSite.STARTS.length * 3, all.size());
        assertTrue(all.stream().allMatch(l -> "A Site".equals(l.getEnd())));

        List<Lineup> fromMain = client.search("Breeze", List.of("Incendiary"), "A Main", "A Site", 0, "lineup", 60);
        assertEquals(3, fromMain.size());
        assertTrue(fromMain.stream().allMatch(l -> "A Main".equals(l.getStart())));
    }

    @Test
    void requestsCarryExplicitNullsLikeTheSitesOwnPage() throws Exception {
        // The verified curl payloads used "start": null / "end": null. Plain Gson silently drops
        // null members, which a stricter server could reject, so the client must keep them.
        client.search("Breeze", List.of("Incendiary"), null, null, 0, "lineup", 5);
        String body = site.lastResultsBody;
        assertTrue(body.contains("\"start\":null"), body);
        assertTrue(body.contains("\"end\":null"), body);
        assertTrue(body.contains("\"user_token\":null"), body);
    }

    @Test
    void allSitesWhenEndIsNull() throws Exception {
        List<Lineup> all = client.search("Breeze", List.of("Incendiary"), null, null, 0, "lineup", 100);
        assertEquals(MockSite.STARTS.length * 3 * 2, all.size());
    }

    @Test
    void pagesThroughMoreThanOnePageAndHonoursTheLimit() throws Exception {
        List<Lineup> some = client.search("Breeze", List.of("Incendiary", "Sky Smoke"), null, "A Site", 0, "lineup", 25);
        assertEquals(25, some.size());
        assertTrue(site.resultsCalls.get() >= 2, "20 per page => at least two requests");
    }

    @Test
    void parsedFieldsArePopulated() throws Exception {
        Lineup l = client.search("Breeze", List.of("Incendiary"), "A Main", "A Site", 0, "lineup", 5).get(0);
        assertNotNull(l.getTitle());
        assertEquals("Breeze", l.getMap());
        assertEquals(3, l.getNumImages());
        assertEquals("Incendiary", l.getAbilitiesRaw());
        assertTrue(l.getId() > 0);
    }

    @Test
    void repeatedSearchesComeFromTheCacheUntilInvalidated() throws Exception {
        client.search("Breeze", List.of("Incendiary"), null, "B Site", 0, "lineup", 10);
        int calls = site.resultsCalls.get();
        client.search("Breeze", List.of("Incendiary"), null, "B Site", 0, "lineup", 10);
        assertEquals(calls, site.resultsCalls.get());
        client.invalidateSearchCache();
        client.search("Breeze", List.of("Incendiary"), null, "B Site", 0, "lineup", 10);
        assertTrue(site.resultsCalls.get() > calls);
    }

    @Test
    void transientServerErrorIsRetriedOnce() throws Exception {
        site.failNextResults = 1;
        List<Lineup> r = client.search("Breeze", List.of("Incendiary"), "Mid", "A Site", 0, "lineup", 10);
        assertEquals(3, r.size());
    }

    @Test
    void persistentServerErrorSurfacesAsIOException() {
        site.failNextResults = 5;
        assertThrows(IOException.class,
                () -> client.search("Breeze", List.of("Incendiary"), "Spawn", "B Site", 0, "lineup", 10));
    }

    @Test
    void calloutsResolveAndAreCached() throws Exception {
        assertEquals("A Site", client.callout("Breeze", 150, 150));
        assertEquals("B Site", client.callout("Breeze", 400, 100));
        int calls = site.calloutCalls.get();
        assertEquals("A Site", client.callout("Breeze", 150.4, 149.6));    // rounds to the same integer point
        assertEquals(calls, site.calloutCalls.get());
    }

    @Test
    void undefinedCalloutIsNull() throws Exception {
        assertNull(client.callout("Breeze", 400, 400));
        assertNull(client.callout("Breeze", 400, 400));      // cached negative result
    }

    @Test
    void lineupDetailParses() throws Exception {
        LineupDetail d = client.lineup(4242);
        assertEquals(4242, d.getLineupId());
        assertEquals(3, d.getImageCount());
        assertEquals("mock_user", d.getUsername());
        assertEquals(4, com.appbrim.model.StepText.split(d.getDescription()).size());
    }

    @Test
    void imagesDownloadAndDecode() throws Exception {
        var img = client.fetchImage(LineupsClient.CDN + "/static/lineup_images_thumbnail/77/1.webp");
        assertEquals(320, img.getWidth());
        var again = client.fetchImage(LineupsClient.CDN + "/static/lineup_images_thumbnail/77/1.webp");   // from disk
        assertEquals(img.getHeight(), again.getHeight());
    }
}
