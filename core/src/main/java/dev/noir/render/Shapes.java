package dev.noir.render;

import dev.noir.util.Colors;

/**
 * Shape builders over {@link WorldCanvas#quad}. Line widths are in screen pixels and
 * converted to world units at each shape's distance, so a 2px outline looks 2px wide
 * near and far.
 */
public final class Shapes {
    private Shapes() {}

    /** Filled axis-aligned box, faces visible from inside and outside. */
    public static void fillBox(WorldCanvas c, double x0, double y0, double z0, double x1, double y1, double z1, int argb) {
        if (Colors.alpha(argb) == 0) return;
        c.quad2(x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, argb); // north
        c.quad2(x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, argb); // south
        c.quad2(x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, argb); // west
        c.quad2(x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, argb); // east
        c.quad2(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, argb); // top
        c.quad2(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, argb); // bottom
    }

    /** Box outline: 12 edges drawn as thin camera-facing ribbons. */
    public static void outlineBox(WorldCanvas c, double x0, double y0, double z0, double x1, double y1, double z1, int argb, float widthPx) {
        if (Colors.alpha(argb) == 0 || widthPx <= 0) return;
        double[][] v = {
            {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1},
            {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
        int[][] e = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] edge : e) {
            double[] a = v[edge[0]], b = v[edge[1]];
            line(c, a[0], a[1], a[2], b[0], b[1], b[2], argb, widthPx);
        }
    }

    /**
     * A line as a ribbon that faces the camera. Each end gets the width that is
     * {@code widthPx} pixels at its own distance, so the line looks equally wide along its length.
     */
    public static void line(WorldCanvas c, double ax, double ay, double az, double bx, double by, double bz, int argb, float widthPx) {
        if (Colors.alpha(argb) == 0 || widthPx <= 0) return;
        CameraInfo cam = c.camera();
        double mx = (ax + bx) / 2, my = (ay + by) / 2, mz = (az + bz) / 2;
        double halfA = cam.worldPerPixel(cam.distanceTo(ax, ay, az)) * widthPx / 2;
        double halfB = cam.worldPerPixel(cam.distanceTo(bx, by, bz)) * widthPx / 2;
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double vx = mx - cam.x(), vy = my - cam.y(), vz = mz - cam.z();
        // Side vector = dir x view, so the ribbon is perpendicular to both the line and the view ray.
        double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-9) return;
        sx /= len;
        sy /= len;
        sz /= len;
        c.quad2(ax - sx * halfA, ay - sy * halfA, az - sz * halfA, bx - sx * halfB, by - sy * halfB, bz - sz * halfB,
            bx + sx * halfB, by + sy * halfB, bz + sz * halfB, ax + sx * halfA, ay + sy * halfA, az + sz * halfA, argb);
    }

    /** Tracer from just in front of the camera to a point. */
    public static void tracer(WorldCanvas c, double tx, double ty, double tz, int argb, float widthPx, TracerOrigin origin) {
        CameraInfo cam = c.camera();
        double[] f = cam.forward();
        double ox = cam.x() + f[0] * 0.5, oy = cam.y() + f[1] * 0.5, oz = cam.z() + f[2] * 0.5;
        if (origin == TracerOrigin.FEET) oy -= 1.5;
        line(c, ox, oy, oz, tx, ty, tz, argb, widthPx);
    }

    public enum TracerOrigin {
        CROSSHAIR("Crosshair"),
        FEET("Feet");

        private final String label;

        TracerOrigin(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    /** Horizontal square at height {@code y} (both sides) with an optional outline. */
    public static void flatSquare(WorldCanvas c, double x0, double z0, double x1, double z1, double y, int fill, int outline, float widthPx) {
        if (Colors.alpha(fill) > 0) c.quad2(x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, fill);
        if (Colors.alpha(outline) > 0 && widthPx > 0) {
            line(c, x0, y, z0, x1, y, z0, outline, widthPx);
            line(c, x1, y, z0, x1, y, z1, outline, widthPx);
            line(c, x1, y, z1, x0, y, z1, outline, widthPx);
            line(c, x0, y, z1, x0, y, z0, outline, widthPx);
        }
    }

    /** A vertical beam (thin column) at a block position. */
    public static void beam(WorldCanvas c, double x, double z, double y0, double y1, int argb, double thickness) {
        double h = thickness / 2;
        fillBox(c, x + 0.5 - h, y0, z + 0.5 - h, x + 0.5 + h, y1, z + 0.5 + h, argb);
    }
}
