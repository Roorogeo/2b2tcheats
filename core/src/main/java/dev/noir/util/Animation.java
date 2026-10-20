package dev.noir.util;

import java.util.function.DoubleSupplier;

/**
 * A value that eases toward a target over a fixed duration (ease-out cubic).
 * {@code speed} is the user's animation speed multiplier; 0 or less disables animation.
 */
public final class Animation {
    private final Clock clock;
    private final double durationMs;
    private final DoubleSupplier speed;
    private float from;
    private float to;
    private long startMs;

    public Animation(Clock clock, double durationMs, DoubleSupplier speed, float initial) {
        this.clock = clock;
        this.durationMs = durationMs;
        this.speed = speed;
        this.from = initial;
        this.to = initial;
        this.startMs = clock.millis();
    }

    public void target(float target) {
        if (target == to) return;
        from = value();
        to = target;
        startMs = clock.millis();
    }

    public void snap(float v) {
        from = v;
        to = v;
    }

    public float target() {
        return to;
    }

    public float value() {
        double s = speed.getAsDouble();
        if (s <= 0) return to;
        double dur = durationMs / s;
        double p = (clock.millis() - startMs) / dur;
        if (p >= 1.0 || dur <= 0) return to;
        if (p <= 0) return from;
        return (float) (from + (to - from) * easeOutCubic(p));
    }

    public boolean done() {
        return value() == to;
    }

    public static double easeOutCubic(double t) {
        double u = 1.0 - t;
        return 1.0 - u * u * u;
    }
}
