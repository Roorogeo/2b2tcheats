package dev.noir.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoundRectRasterTest {
    private static double area(int[] rects) {
        double a = 0;
        for (int i = 0; i < rects.length; i += 5) {
            a += (double) (rects[i + 2] - rects[i]) * (rects[i + 3] - rects[i + 1]) * rects[i + 4] / 255.0;
        }
        return a;
    }

    @Test
    void filledAreaMatchesGeometry() {
        int w = 240, h = 120;
        float r = 16;
        double expected = w * h - (4 - Math.PI) * r * r;
        assertEquals(expected, area(RoundRectRaster.rects(w, h, r, 0)), expected * 0.005);
    }

    @Test
    void outlineAreaIsPerimeterTimesThickness() {
        int w = 200, h = 80;
        float r = 10, t = 1;
        double perimeter = 2 * (w + h) - (8 - 2 * Math.PI) * r;
        assertEquals(perimeter * t, area(RoundRectRaster.rects(w, h, r, t)), perimeter * 0.03);
    }

    @Test
    void interiorIsOneRect() {
        int[] rects = RoundRectRaster.rects(300, 400, 16, 0);
        boolean big = false;
        for (int i = 0; i < rects.length; i += 5) {
            if (rects[i + 2] - rects[i] >= 250 && rects[i + 3] - rects[i + 1] >= 350) big = true;
        }
        assertTrue(big, "interior should merge into one rectangle");
        assertTrue(rects.length / 5 < 400, "too many rects: " + rects.length / 5);
    }

    @Test
    void rectsStayInBoundsAndDoNotOverlap() {
        int w = 37, h = 23;
        int[] rects = RoundRectRaster.rects(w, h, 10, 0);
        int[][] cover = new int[h][w];
        for (int i = 0; i < rects.length; i += 5) {
            assertTrue(rects[i] >= 0 && rects[i + 2] <= w && rects[i + 1] >= 0 && rects[i + 3] <= h);
            for (int y = rects[i + 1]; y < rects[i + 3]; y++) for (int x = rects[i]; x < rects[i + 2]; x++) cover[y][x]++;
        }
        for (int[] row : cover) for (int c : row) assertTrue(c <= 1, "overlapping coverage");
        assertEquals(1, cover[h / 2][w / 2]);
        assertEquals(0, cover[0][0], "corner pixel must be transparent");
    }

    @Test
    void cornersAreSymmetric() {
        int w = 50, h = 50;
        float hw = w / 2f, hh = h / 2f;
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                float a = RoundRectRaster.coverage(x + 0.5f - hw, y + 0.5f - hh, hw, hh, 12, 0);
                float b = RoundRectRaster.coverage((w - 1 - x) + 0.5f - hw, (h - 1 - y) + 0.5f - hh, hw, hh, 12, 0);
                assertEquals(a, b, 1e-5);
            }
        }
    }
}
