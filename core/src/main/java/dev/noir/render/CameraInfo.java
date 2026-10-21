package dev.noir.render;

/**
 * Camera state for one frame. Yaw/pitch use Minecraft's convention: yaw 0 looks +Z,
 * yaw 90 looks -X, pitch 90 looks down.
 */
public record CameraInfo(double x, double y, double z, float yaw, float pitch, double fovDegrees, int screenWidthPx, int screenHeightPx) {
    public static final CameraInfo NONE = new CameraInfo(0, 0, 0, 0, 0, 70, 1920, 1080);

    public double distanceTo(double px, double py, double pz) {
        double dx = px - x, dy = py - y, dz = pz - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** World units covered by one screen pixel at {@code distance} (vertical FOV). */
    public double worldPerPixel(double distance) {
        return 2.0 * Math.max(distance, 0.05) * Math.tan(Math.toRadians(fovDegrees) / 2.0) / Math.max(1, screenHeightPx);
    }

    /** Unit look vector. */
    public double[] forward() {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        return new double[] {-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)};
    }

    /**
     * Projects a world point to screen pixels {x, y}, or null if it is behind the camera.
     * Perspective projection with vertical FOV {@link #fovDegrees}.
     */
    public double[] project(double px, double py, double pz) {
        double[] f = forward();
        double yr = Math.toRadians(yaw);
        // Right vector (horizontal) and up = right x forward.
        double rx = -Math.cos(yr), ry = 0, rz = -Math.sin(yr);
        double ux = ry * f[2] - rz * f[1], uy = rz * f[0] - rx * f[2], uz = rx * f[1] - ry * f[0];
        double dx = px - x, dy = py - y, dz = pz - z;
        double depth = dx * f[0] + dy * f[1] + dz * f[2];
        if (depth <= 0.05) return null;
        double right = dx * rx + dy * ry + dz * rz;
        double up = dx * ux + dy * uy + dz * uz;
        double t = Math.tan(Math.toRadians(fovDegrees) / 2.0);
        double aspect = screenWidthPx / (double) screenHeightPx;
        double ndcX = right / (depth * t * aspect);
        double ndcY = up / (depth * t);
        return new double[] {(ndcX + 1) / 2 * screenWidthPx, (1 - ndcY) / 2 * screenHeightPx};
    }
}
