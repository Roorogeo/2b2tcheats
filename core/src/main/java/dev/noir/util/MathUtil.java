package dev.noir.util;

public final class MathUtil {
    private MathUtil() {}

    public static float clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    public static double clamp(double v, double min, double max) {
        return v < min ? min : Math.min(v, max);
    }

    public static int clamp(int v, int min, int max) {
        return v < min ? min : Math.min(v, max);
    }

    /** Wraps an angle to [-180, 180). */
    public static float wrapDegrees(float deg) {
        float d = deg % 360f;
        if (d >= 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    public static double wrapDegrees(double deg) {
        double d = deg % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Rounds {@code v} to the nearest multiple of {@code step} offset from {@code origin}. */
    public static double snap(double v, double origin, double step) {
        if (step <= 0) return v;
        return origin + Math.round((v - origin) / step) * step;
    }

    /** Number of decimals needed to display multiples of {@code step}. */
    public static int decimalsFor(double step) {
        if (step <= 0) return 2;
        int decimals = 0;
        double s = step;
        while (decimals < 6 && Math.abs(s - Math.rint(s)) > 1e-9) {
            s *= 10;
            decimals++;
        }
        return decimals;
    }
}
