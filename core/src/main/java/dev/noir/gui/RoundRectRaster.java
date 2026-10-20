package dev.noir.gui;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rasterizes anti-aliased rounded rectangles (filled or outlined) into a small set of
 * axis-aligned rectangles with per-rectangle coverage. Backends only need "fill a pixel
 * rectangle", which Minecraft's GUI renderer does cheaply.
 *
 * <p>Coverage uses the signed distance to the rounded box sampled at pixel centers.
 * Rows are run-length encoded and merged vertically, so a panel's interior becomes
 * one rectangle and only the corners produce many small ones.
 */
public final class RoundRectRaster {
    private RoundRectRaster() {}

    private static final int CACHE_SIZE = 768;
    private static final Map<Key, int[]> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, int[]> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private record Key(int w, int h, int r100, int t100) {}

    /**
     * Returns packed quintuples {x0, y0, x1, y1, alpha255} relative to the rectangle's
     * top-left. {@code thickness <= 0} means filled.
     */
    public static int[] rects(int w, int h, float radius, float thickness) {
        if (w <= 0 || h <= 0) return new int[0];
        Key key = new Key(w, h, Math.round(radius * 100), Math.round(Math.max(0, thickness) * 100));
        synchronized (CACHE) {
            int[] cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        int[] computed = rasterize(w, h, radius, thickness);
        synchronized (CACHE) {
            CACHE.put(key, computed);
        }
        return computed;
    }

    /** Coverage 0..1 of the pixel centered at (px, py), with coordinates relative to the rect's center. */
    public static float coverage(float px, float py, float hw, float hh, float r, float t) {
        float d = sdf(px, py, hw, hh, r);
        float outer = clamp01(0.5f - d);
        if (t <= 0) return outer;
        return outer - clamp01(0.5f - (d + t));
    }

    static float sdf(float px, float py, float hw, float hh, float r) {
        float qx = Math.abs(px) - (hw - r);
        float qy = Math.abs(py) - (hh - r);
        float ox = Math.max(qx, 0f), oy = Math.max(qy, 0f);
        return (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0f) - r;
    }

    private static int[] rasterize(int w, int h, float radius, float thickness) {
        float r = Math.max(0f, Math.min(radius, Math.min(w, h) / 2f));
        float t = thickness <= 0 ? 0 : Math.min(thickness, Math.min(w, h) / 2f);
        float hw = w / 2f, hh = h / 2f;
        int edge = Math.min((int) Math.ceil(r + t) + 2, (w + 1) / 2);
        int midStart = edge, midEnd = w - edge;

        IntList out = new IntList();
        // Open rectangles from the previous row keyed by (x0, x1, alpha).
        Map<Long, int[]> open = new HashMap<>();
        Map<Long, int[]> next = new HashMap<>();
        IntList row = new IntList();

        for (int y = 0; y < h; y++) {
            float py = y + 0.5f - hh;
            row.clear();
            int runStart = -1, runAlpha = 0;
            int xEndLeft = Math.min(midStart, w);
            for (int x = 0; x < xEndLeft; x++) {
                int a = alpha(x + 0.5f - hw, py, hw, hh, r, t);
                if (a != runAlpha) {
                    if (runAlpha > 0) row.add(runStart, x, runAlpha);
                    runStart = x;
                    runAlpha = a;
                }
            }
            if (midEnd > midStart) {
                int a = alpha(0f, py, hw, hh, r, t);
                if (a != runAlpha) {
                    if (runAlpha > 0) row.add(runStart, midStart, runAlpha);
                    runStart = midStart;
                    runAlpha = a;
                }
            }
            for (int x = Math.max(midEnd, midStart); x < w; x++) {
                int a = alpha(x + 0.5f - hw, py, hw, hh, r, t);
                if (a != runAlpha) {
                    if (runAlpha > 0) row.add(runStart, x, runAlpha);
                    runStart = x;
                    runAlpha = a;
                }
            }
            if (runAlpha > 0) row.add(runStart, w, runAlpha);

            next.clear();
            for (int i = 0; i < row.size; i += 3) {
                int x0 = row.data[i], x1 = row.data[i + 1], a = row.data[i + 2];
                long k = ((long) x0 << 40) | ((long) x1 << 16) | a;
                int[] rect = open.remove(k);
                if (rect != null) rect[3] = y + 1;
                else rect = new int[] {x0, y, x1, y + 1, a};
                next.put(k, rect);
            }
            for (int[] closed : open.values()) out.add(closed);
            Map<Long, int[]> swap = open;
            open = next;
            next = swap;
        }
        for (int[] closed : open.values()) out.add(closed);
        return out.toArray();
    }

    private static int alpha(float px, float py, float hw, float hh, float r, float t) {
        return Math.round(coverage(px, py, hw, hh, r, t) * 255f);
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : Math.min(v, 1f);
    }

    private static final class IntList {
        int[] data = new int[64];
        int size;

        void add(int a, int b, int c) {
            ensure(3);
            data[size++] = a;
            data[size++] = b;
            data[size++] = c;
        }

        void add(int[] rect) {
            ensure(5);
            System.arraycopy(rect, 0, data, size, 5);
            size += 5;
        }

        void clear() {
            size = 0;
        }

        private void ensure(int n) {
            if (size + n > data.length) data = Arrays.copyOf(data, Math.max(data.length * 2, size + n));
        }

        int[] toArray() {
            return Arrays.copyOf(data, size);
        }
    }
}
