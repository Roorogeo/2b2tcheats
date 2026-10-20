package dev.noir.network;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Ring buffer of outgoing packets with attribution, shown in the GUI's Packet Log panel. */
public final class PacketLog {
    public record Entry(long tick, String type, PacketKind kind, String source, boolean blocked, String reason) {}

    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private int capacity;
    private boolean paused;
    private long totalLogged;

    public PacketLog(int capacity) {
        this.capacity = Math.max(16, capacity);
    }

    public synchronized void add(Entry e) {
        totalLogged++;
        if (paused) return;
        entries.addLast(e);
        while (entries.size() > capacity) entries.removeFirst();
    }

    public synchronized List<Entry> snapshot() {
        return new ArrayList<>(entries);
    }

    public synchronized void clear() {
        entries.clear();
    }

    public synchronized void setCapacity(int capacity) {
        this.capacity = Math.max(16, capacity);
        while (entries.size() > this.capacity) entries.removeFirst();
    }

    public synchronized boolean paused() {
        return paused;
    }

    public synchronized void setPaused(boolean paused) {
        this.paused = paused;
    }

    public synchronized long totalLogged() {
        return totalLogged;
    }
}
