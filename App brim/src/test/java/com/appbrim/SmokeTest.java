package com.appbrim;

import com.appbrim.api.LineupsClient;
import com.appbrim.model.Lineup;
import com.appbrim.ui.DetailView;

import java.util.List;

/** Plain-main smoke test against the live lineupsvalorant.com endpoints. */
public final class SmokeTest {

    public static void main(String[] args) throws Exception {
        LineupsClient client = new LineupsClient();

        List<Lineup> results = client.search("Ascent", List.of("Incendiary"), null, "A Site",
                LineupsClient.SIDE_ANY, "lineup", 25);
        System.out.println("search: " + results.size() + " results, first="
                + results.get(0).getTitle() + " (id=" + results.get(0).getId() + ", score=" + results.get(0).getScore() + ")");

        var detail = client.lineup(results.get(0).getId());
        System.out.println("detail: imgs=" + detail.getImageCount()
                + " steps=" + DetailView.splitSteps(detail.getDescription()).size()
                + " by=" + detail.getUsername());

        System.out.println("callout(150,80)=" + client.callout("Ascent", 150, 80));
        System.out.println("callout(350,350)=" + client.callout("Ascent", 350, 350));
        System.out.println("callout(10,10)=" + client.callout("Ascent", 10, 10));

        var img = client.fetchImage(LineupsClient.CDN + "/static/lineup_images_thumbnail/"
                + results.get(0).getId() + "/" + results.get(0).getNumImages() + ".webp");
        System.out.println("webp decode: " + img.getWidth() + "x" + img.getHeight());

        // Cached second read must come from disk instantly.
        long t0 = System.nanoTime();
        client.fetchImage(LineupsClient.CDN + "/static/lineup_images_thumbnail/"
                + results.get(0).getId() + "/" + results.get(0).getNumImages() + ".webp");
        System.out.println("cache read: " + (System.nanoTime() - t0) / 1_000_000 + " ms");

        // Haven end-to-end: C Site with a start filter.
        List<Lineup> haven = client.search("Haven", List.of("Incendiary"), "C Long", "C Site",
                LineupsClient.SIDE_ANY, "lineup", 60);
        System.out.println("haven C Long->C Site: " + haven.size() + " results");

        // Side filter probe (values guessed from the site's toggles).
        for (int s : new int[]{0, 1, 2}) {
            List<Lineup> r = client.search("Ascent", List.of("Incendiary"), null, "A Site", s, "lineup", 60);
            System.out.println("side=" + s + " -> " + r.size() + " results");
        }

        System.out.println("SMOKE OK");
    }

    private SmokeTest() {
    }
}
