package dev.noir.sim;

import dev.noir.network.PacketKind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * What a vanilla client can put on the wire in one tick, and a checker that reports
 * every tick that exceeds it.
 */
public final class VanillaBudget {
    private VanillaBudget() {}

    /** Per-tick maximum by kind for a vanilla client (one player, normal input). */
    public static final Map<PacketKind, Integer> MAX_PER_TICK = new EnumMap<>(PacketKind.class);

    static {
        MAX_PER_TICK.put(PacketKind.MOVEMENT, 1);
        MAX_PER_TICK.put(PacketKind.INTERACT, 1);
        MAX_PER_TICK.put(PacketKind.SWING, 2);
        MAX_PER_TICK.put(PacketKind.USE_ITEM, 1);
        MAX_PER_TICK.put(PacketKind.CONTAINER, 1);
        MAX_PER_TICK.put(PacketKind.HELD_ITEM, 1);
        MAX_PER_TICK.put(PacketKind.PLAYER_ACTION, 1);
        MAX_PER_TICK.put(PacketKind.CUSTOM_PAYLOAD, 0);
        MAX_PER_TICK.put(PacketKind.CHAT, 0);
    }

    /** Returns human-readable violations; empty means the wire is vanilla-shaped. */
    public static List<String> check(List<SimulatedClient.Wire> wire) {
        List<String> problems = new ArrayList<>();
        Map<Long, List<SimulatedClient.Wire>> byTick = new TreeMap<>();
        for (SimulatedClient.Wire w : wire) byTick.computeIfAbsent(w.tick(), k -> new ArrayList<>()).add(w);

        float lastYaw = Float.NaN, lastPitch = Float.NaN;
        for (Map.Entry<Long, List<SimulatedClient.Wire>> e : byTick.entrySet()) {
            long tick = e.getKey();
            List<SimulatedClient.Wire> ws = e.getValue();
            Map<PacketKind, Integer> counts = new EnumMap<>(PacketKind.class);
            for (SimulatedClient.Wire w : ws) counts.merge(w.kind(), 1, Integer::sum);
            for (Map.Entry<PacketKind, Integer> c : counts.entrySet()) {
                Integer max = MAX_PER_TICK.get(c.getKey());
                if (max != null && c.getValue() > max) {
                    problems.add("tick " + tick + ": " + c.getValue() + " " + c.getKey() + " packets (vanilla max " + max + ")");
                }
            }
            for (int i = 0; i < ws.size(); i++) {
                SimulatedClient.Wire w = ws.get(i);
                if (w.kind() == PacketKind.INTERACT) {
                    boolean swung = i + 1 < ws.size() && ws.get(i + 1).kind() == PacketKind.SWING;
                    if (!swung) problems.add("tick " + tick + ": attack without the vanilla swing right after it");
                }
                if (w.kind() == PacketKind.MOVEMENT && w.type().contains("Rot")) {
                    if (w.pitch() < -90f || w.pitch() > 90f) problems.add("tick " + tick + ": pitch " + w.pitch() + " out of range");
                    if (w.type().endsWith("$Rot") && w.yaw() == lastYaw && w.pitch() == lastPitch) {
                        problems.add("tick " + tick + ": duplicate rotation packet");
                    }
                    lastYaw = w.yaw();
                    lastPitch = w.pitch();
                }
            }
            // Vanilla handles input before the player tick, so movement is last in a tick.
            int moveIdx = -1;
            for (int i = 0; i < ws.size(); i++) if (ws.get(i).kind() == PacketKind.MOVEMENT) moveIdx = i;
            if (moveIdx >= 0 && moveIdx != ws.size() - 1) problems.add("tick " + tick + ": packets after the movement packet (out of order)");
        }
        return problems;
    }
}
