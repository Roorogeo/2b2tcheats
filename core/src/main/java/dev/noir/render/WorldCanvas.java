package dev.noir.render;

/**
 * 3D drawing in world coordinates. The game layer only implements {@link #quad};
 * every shape (boxes, outlines, lines, beams) is built from quads by {@link Shapes},
 * so all geometry is platform independent and testable.
 */
public interface WorldCanvas {
    CameraInfo camera();

    /** A single-sided quad (counter-clockwise when seen from the front) in world coordinates. */
    void quad(double x0, double y0, double z0, double x1, double y1, double z1,
              double x2, double y2, double z2, double x3, double y3, double z3, int argb);

    /** Both windings, visible from either side. */
    default void quad2(double x0, double y0, double z0, double x1, double y1, double z1,
                       double x2, double y2, double z2, double x3, double y3, double z3, int argb) {
        quad(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, argb);
        quad(x3, y3, z3, x2, y2, z2, x1, y1, z1, x0, y0, z0, argb);
    }
}
