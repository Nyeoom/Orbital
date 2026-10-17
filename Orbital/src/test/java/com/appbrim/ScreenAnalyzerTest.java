package com.appbrim;

import com.appbrim.detect.ScreenAnalyzer;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenAnalyzerTest {

    private final ScreenAnalyzer analyzer = new ScreenAnalyzer();

    private static BufferedImage map(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(20, 30, 45));
        g.fillRect(0, 0, size, size);
        g.setColor(new Color(60, 80, 100));
        g.drawRect(size / 10, size / 10, size * 8 / 10, size * 8 / 10);
        g.dispose();
        return img;
    }

    private static void blob(BufferedImage img, Color c, int cx, int cy, int r) {
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillOval(cx - r, cy - r, 2 * r, 2 * r);
        g.dispose();
    }

    @Test
    void findsRedSpikeAndWhitePlayerInMapSpace() {
        BufferedImage img = map(500);
        blob(img, new Color(255, 70, 85), 150, 150, 12);
        blob(img, Color.WHITE, 100, 400, 10);

        List<ScreenAnalyzer.Marker> markers = analyzer.analyze(img, new Rectangle(0, 0, 500, 500));
        ScreenAnalyzer.Marker red = markers.stream().filter(ScreenAnalyzer.Marker::red).findFirst().orElseThrow();
        ScreenAnalyzer.Marker white = markers.stream().filter(m -> !m.red()).findFirst().orElseThrow();

        assertEquals(150, red.x(), 3);
        assertEquals(150, red.y(), 3);
        assertEquals(100, white.x(), 3);
        assertEquals(400, white.y(), 3);
    }

    @Test
    void coordinatesScaleToTheFiveHundredSpaceRegardlessOfCaptureSize() {
        BufferedImage img = map(1000);              // a 1440p-ish capture of the same map
        blob(img, new Color(255, 70, 85), 300, 300, 24);

        List<ScreenAnalyzer.Marker> markers = analyzer.analyze(img, new Rectangle(0, 0, 1000, 1000));
        ScreenAnalyzer.Marker red = markers.stream().filter(ScreenAnalyzer.Marker::red).findFirst().orElseThrow();
        assertEquals(150, red.x(), 3);
        assertEquals(150, red.y(), 3);
    }

    @Test
    void emptyMapHasNoMarkers() {
        assertTrue(analyzer.analyze(map(500), new Rectangle(0, 0, 500, 500)).isEmpty());
    }

    @Test
    void dimReddishTerrainIsNotASpike() {
        BufferedImage img = map(500);
        blob(img, new Color(120, 70, 70), 200, 200, 30);   // muted brown/red map art
        assertFalse(analyzer.analyze(img, new Rectangle(0, 0, 500, 500)).stream().anyMatch(ScreenAnalyzer.Marker::red));
    }

    @Test
    void speckleBelowTheSizeThresholdIsIgnored() {
        BufferedImage img = map(500);
        blob(img, new Color(255, 70, 85), 250, 250, 2);
        assertTrue(analyzer.analyze(img, new Rectangle(0, 0, 500, 500)).isEmpty());
    }

    @Test
    void severalRedBlobsAreReturnedLargestFirst() {
        BufferedImage img = map(500);
        blob(img, new Color(255, 70, 85), 100, 100, 8);
        blob(img, new Color(255, 70, 85), 300, 300, 14);
        List<ScreenAnalyzer.Marker> reds = analyzer.analyze(img, new Rectangle(0, 0, 500, 500)).stream()
                .filter(ScreenAnalyzer.Marker::red).toList();
        assertEquals(2, reds.size());
        assertTrue(reds.get(0).size() > reds.get(1).size());
        assertEquals(300, reds.get(0).x(), 3);
    }
}
