package dev.noir.preview;

import dev.noir.render.CameraInfo;
import dev.noir.render.WorldCanvas;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Preview stand-in for the game's world renderer: projects each quad with
 * {@link CameraInfo#project} and paints back to front (no depth buffer).
 */
public final class Java2DWorldCanvas implements WorldCanvas {
    private record Quad(double[] p, int argb, double depth) {}

    private final CameraInfo camera;
    private final List<Quad> quads = new ArrayList<>();

    public Java2DWorldCanvas(CameraInfo camera) {
        this.camera = camera;
    }

    @Override
    public CameraInfo camera() {
        return camera;
    }

    @Override
    public void quad(double x0, double y0, double z0, double x1, double y1, double z1,
                     double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
        double cx = (x0 + x1 + x2 + x3) / 4, cy = (y0 + y1 + y2 + y3) / 4, cz = (z0 + z1 + z2 + z3) / 4;
        quads.add(new Quad(new double[] {x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3}, argb, camera.distanceTo(cx, cy, cz)));
    }

    private static final double NEAR = 0.1;

    public void paint(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        quads.sort(Comparator.comparingDouble(Quad::depth).reversed());
        double[] f = camera.forward();
        for (Quad q : quads) {
            // Clip against the near plane (like the GPU does) before projecting.
            List<double[]> in = new ArrayList<>();
            for (int i = 0; i < 4; i++) in.add(new double[] {q.p[i * 3], q.p[i * 3 + 1], q.p[i * 3 + 2]});
            List<double[]> out = new ArrayList<>();
            for (int i = 0; i < in.size(); i++) {
                double[] a = in.get(i), b = in.get((i + 1) % in.size());
                double da = depth(a, f), db = depth(b, f);
                if (da >= NEAR) out.add(a);
                if ((da >= NEAR) != (db >= NEAR)) {
                    double t = (NEAR - da) / (db - da);
                    out.add(new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t});
                }
            }
            if (out.size() < 3) continue;
            Path2D.Double path = new Path2D.Double();
            for (int i = 0; i < out.size(); i++) {
                double[] p = out.get(i);
                double[] s = camera.project(p[0], p[1], p[2]);
                if (s == null) s = camera.project(p[0] + f[0] * 0.01, p[1] + f[1] * 0.01, p[2] + f[2] * 0.01);
                if (i == 0) path.moveTo(s[0], s[1]);
                else path.lineTo(s[0], s[1]);
            }
            path.closePath();
            g.setColor(new Color(q.argb, true));
            g.fill(path);
        }
    }

    private double depth(double[] p, double[] f) {
        return (p[0] - camera.x()) * f[0] + (p[1] - camera.y()) * f[1] + (p[2] - camera.z()) * f[2];
    }
}
