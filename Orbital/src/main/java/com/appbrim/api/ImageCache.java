package com.appbrim.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Disk cache for images fetched from the lineup CDN (webp thumbnails, minimaps, icons). */
public final class ImageCache {

    private static final Path DIR = Path.of(System.getProperty("user.home"), "AppData", "Local", "AppBrim", "cache");
    private static final long MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000;
    private static final long MAX_BYTES = 300L * 1024 * 1024;

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
            if (!Files.exists(f)) {
                return null;
            }
            byte[] data = Files.readAllBytes(f);
            // Touch so pruning keeps what is actually being used.
            Files.setLastModifiedTime(f, FileTime.fromMillis(System.currentTimeMillis()));
            return data;
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

    /** Deletes entries unused for 30 days, then oldest-first until under 300 MB. Run in the background. */
    public void prune() {
        try (Stream<Path> files = Files.list(DIR)) {
            long now = System.currentTimeMillis();
            List<Path> remaining = new java.util.ArrayList<>();
            for (Path p : (Iterable<Path>) files::iterator) {
                if (!p.getFileName().toString().endsWith(".bin")) {
                    continue;
                }
                if (now - Files.getLastModifiedTime(p).toMillis() > MAX_AGE_MS) {
                    Files.deleteIfExists(p);
                } else {
                    remaining.add(p);
                }
            }
            remaining.sort(Comparator.comparingLong(p -> {
                try {
                    return Files.getLastModifiedTime(p).toMillis();
                } catch (IOException e) {
                    return 0L;
                }
            }));
            long total = 0;
            for (Path p : remaining) {
                total += Files.size(p);
            }
            for (Path p : remaining) {
                if (total <= MAX_BYTES) {
                    break;
                }
                long size = Files.size(p);
                Files.deleteIfExists(p);
                total -= size;
            }
        } catch (IOException e) {
            // Pruning is housekeeping only.
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
