package dev.noir.network;

/**
 * Estimates server TPS from the spacing of world time updates (the server sends one
 * every 20 ticks). Read-only: it only observes packets the server already sent.
 */
public final class TpsTracker {
    private static final int WINDOW = 8;
    private final double[] samples = new double[WINDOW];
    private int count;
    private int index;
    private long lastNanos;

    public synchronized void onTimeUpdate() {
        long now = System.nanoTime();
        if (lastNanos != 0) {
            double seconds = (now - lastNanos) / 1e9;
            if (seconds > 0.05) {
                samples[index] = Math.min(20.0, 20.0 / seconds);
                index = (index + 1) % WINDOW;
                count = Math.min(WINDOW, count + 1);
            }
        }
        lastNanos = now;
    }

    public synchronized double tps() {
        if (count == 0) return -1;
        double sum = 0;
        for (int i = 0; i < count; i++) sum += samples[i];
        return sum / count;
    }

    public synchronized void reset() {
        count = 0;
        index = 0;
        lastNanos = 0;
    }
}
