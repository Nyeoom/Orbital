package com.appbrim.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/** Disk cache for images fetched from the lineup CDN (webp thumbnails, minimaps, icons). */
public final class ImageCache {

    private static final Path DIR = Path.of(System.getProperty("user.home"), "AppData", "Local", "AppBrim", "cache");

    private static ImageCache instance;

    public static synchronized ImageCache get() {
        if (instance == null) {
            instance = new ImageCache();
        }
        return instance;
    }

    private ImageCache() {
        try {
            Files.createDirectories(DIR);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create cache dir " + DIR, e);
        }
    }

    /** Returns cached bytes for the URL, or null on a miss. */
    public byte[] get(String url) {
        Path f = fileFor(url);
        try {
            return Files.exists(f) ? Files.readAllBytes(f) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public void put(String url, byte[] data) {
        try {
            Files.write(fileFor(url), data);
        } catch (IOException e) {
            // Cache failures are non-fatal.
        }
    }

    private Path fileFor(String url) {
        return DIR.resolve(sha256(url) + ".bin");
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
