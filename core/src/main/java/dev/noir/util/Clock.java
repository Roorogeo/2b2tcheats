package dev.noir.util;

/** Millisecond time source; swappable so animations and autosave are testable. */
@FunctionalInterface
public interface Clock {
    long millis();

    Clock SYSTEM = () -> System.nanoTime() / 1_000_000L;

    /** A clock that only moves when told to. */
    final class Manual implements Clock {
        private long now;

        public Manual(long start) { this.now = start; }

        @Override public long millis() { return now; }

        public void advance(long ms) { now += ms; }

        public void set(long ms) { now = ms; }
    }
}
