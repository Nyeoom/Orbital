package com.appbrim.detect;

import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Finds the planted-spike marker (red) and the player marker (white arrow) in a
 * screenshot of Valorant's full-map overlay (the map shown while M is held).
 * Pure pixel classification - no OCR, no game memory access.
 */
public final class ScreenAnalyzer {

    /** One detected blob on the map overlay. */
    public record Marker(double x, double y, double size, Rectangle bounds, boolean red) { }

    /** Default full-map overlay region guess: centered square, 70% of screen height. */
    public static Rectangle defaultRegion() {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Rectangle screen = gd.getDefaultConfiguration().getBounds();
        int side = (int) (Math.min(screen.width, screen.height) * 0.70);
        int x = screen.x + (screen.width - side) / 2;
        int y = screen.y + (screen.height - side) / 2;
        return new Rectangle(x, y, side, side);
    }

    public BufferedImage captureScreen() throws AWTException {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Rectangle screen = gd.getDefaultConfiguration().getBounds();
        Robot robot = new Robot(gd);
        return robot.createScreenCapture(screen);
    }

    /** Capture only the given region of the primary screen. */
    public BufferedImage captureRegion(Rectangle region) throws AWTException {
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        Robot robot = new Robot(gd);
        Rectangle screen = gd.getDefaultConfiguration().getBounds();
        Rectangle clip = region.intersection(screen);
        if (clip.isEmpty()) {
            throw new IllegalArgumentException("Calibrated region is outside the screen: " + region);
        }
        return robot.createScreenCapture(clip);
    }

    /**
     * Detect red (spike) and white (player/other) blobs inside the region of the
     * screenshot. Coordinates are converted to the site's 0..500 minimap space.
     */
    public List<Marker> analyze(BufferedImage img, Rectangle regionInScreen) {
        int w = img.getWidth();
        int h = img.getHeight();
        // Sample every other pixel: markers are big enough to survive, and this
        // keeps the flood fill snappy on 1440p+ captures.
        int step = 2;
        int mw = w / step;
        int mh = h / step;
        int[] rgb = new int[mw * mh];
        int idx = 0;
        for (int y = 0; y < mh; y++) {
            for (int x = 0; x < mw; x++) {
                rgb[idx++] = img.getRGB(x * step, y * step);
            }
        }

        boolean[] redMask = new boolean[rgb.length];
        boolean[] whiteMask = new boolean[rgb.length];
        for (int i = 0; i < rgb.length; i++) {
            int c = rgb[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            if (r >= 140 && r - g >= 60 && r - b >= 60) {
                redMask[i] = true;
            } else if (r >= 205 && g >= 205 && b >= 205) {
                int max = Math.max(r, Math.max(g, b));
                int min = Math.min(r, Math.min(g, b));
                if (max - min <= 45) {
                    whiteMask[i] = true;
                }
            }
        }

        List<Marker> markers = new ArrayList<>();
        collectClusters(redMask, mw, mh, step, true, markers);
        collectClusters(whiteMask, mw, mh, step, false, markers);

        // Convert centroids from region pixels to 0..500 map space.
        List<Marker> out = new ArrayList<>();
        for (Marker m : markers) {
            double mx = clamp500((m.x() / w) * 500.0);
            double my = clamp500((m.y() / h) * 500.0);
            out.add(new Marker(mx, my, m.size(), m.bounds(), m.red()));
        }
        out.sort((a, b) -> Long.compare((long) b.size(), (long) a.size()));
        return out;
    }

    private void collectClusters(boolean[] mask, int mw, int mh, int step, boolean red,
                                 List<Marker> out) {
        boolean[] visited = new boolean[mask.length];
        Deque<Integer> stack = new ArrayDeque<>();
        int minSize = red ? 10 : 14;   // sampled pixels; real area is step^2 times this
        for (int i = 0; i < mask.length; i++) {
            if (!mask[i] || visited[i]) {
                continue;
            }
            stack.push(i);
            visited[i] = true;
            long count = 0;
            double sx = 0, sy = 0;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = -1, maxY = -1;
            while (!stack.isEmpty()) {
                int p = stack.pop();
                int px = p % mw;
                int py = p / mw;
                count++;
                sx += px;
                sy += py;
                minX = Math.min(minX, px);
                maxX = Math.max(maxX, px);
                minY = Math.min(minY, py);
                maxY = Math.max(maxY, py);
                if (px > 0 && mask[p - 1] && !visited[p - 1]) { visited[p - 1] = true; stack.push(p - 1); }
                if (px < mw - 1 && mask[p + 1] && !visited[p + 1]) { visited[p + 1] = true; stack.push(p + 1); }
                if (py > 0 && mask[p - mw] && !visited[p - mw]) { visited[p - mw] = true; stack.push(p - mw); }
                if (py < mh - 1 && mask[p + mw] && !visited[p + mw]) { visited[p + mw] = true; stack.push(p + mw); }
            }
            if (count >= minSize) {
                double cx = (sx / count + 0.5) * step;
                double cy = (sy / count + 0.5) * step;
                Rectangle bounds = new Rectangle(minX * step, minY * step,
                        (maxX - minX + 1) * step, (maxY - minY + 1) * step);
                out.add(new Marker(cx, cy, count, bounds, red));
            }
        }
    }

    private static double clamp500(double v) {
        return Math.max(0, Math.min(500, v));
    }
}
