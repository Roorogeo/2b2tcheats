package dev.noir.network;

import dev.noir.module.Module;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Checks a packet log against what a vanilla client can send per tick. Used by the
 * in-game packet test (and available for diagnostics); the headless simulator test
 * has a stricter checker that also sees rotations and ordering.
 */
public final class PacketAudit {
    private PacketAudit() {}

    /** Per-tick maximum by kind for a vanilla client. Kinds not listed are not limited here. */
    public static final Map<PacketKind, Integer> VANILLA_MAX_PER_TICK = new EnumMap<>(PacketKind.class);

    static {
        VANILLA_MAX_PER_TICK.put(PacketKind.MOVEMENT, 1);
        VANILLA_MAX_PER_TICK.put(PacketKind.INTERACT, 1);
        VANILLA_MAX_PER_TICK.put(PacketKind.SWING, 2);
        VANILLA_MAX_PER_TICK.put(PacketKind.USE_ITEM, 1);
        VANILLA_MAX_PER_TICK.put(PacketKind.CONTAINER, 1);
        VANILLA_MAX_PER_TICK.put(PacketKind.HELD_ITEM, 1);
        VANILLA_MAX_PER_TICK.put(PacketKind.PLAYER_ACTION, 1);
    }

    /**
     * Returns problems found in {@code entries} (blocked entries never reached the wire and are
     * not counted). {@code module} is the module under test, or null to audit everything.
     */
    public static List<String> audit(List<PacketLog.Entry> entries, Module module) {
        List<String> problems = new ArrayList<>();
        Map<Long, Map<PacketKind, Integer>> perTick = new TreeMap<>();
        for (PacketLog.Entry e : entries) {
            if (e.blocked()) continue;
            perTick.computeIfAbsent(e.tick(), k -> new EnumMap<>(PacketKind.class)).merge(e.kind(), 1, Integer::sum);
            if (module != null && module.isReadOnly() && e.source().contains(module.name())) {
                problems.add("tick " + e.tick() + ": read-only module " + module.name() + " caused " + e.type());
            }
            if (e.kind() == PacketKind.CUSTOM_PAYLOAD && !e.source().equals("vanilla")) {
                problems.add("tick " + e.tick() + ": custom payload from " + e.source());
            }
        }
        for (Map.Entry<Long, Map<PacketKind, Integer>> t : perTick.entrySet()) {
            for (Map.Entry<PacketKind, Integer> c : t.getValue().entrySet()) {
                Integer max = VANILLA_MAX_PER_TICK.get(c.getKey());
                if (max != null && c.getValue() > max) {
                    problems.add("tick " + t.getKey() + ": " + c.getValue() + " " + c.getKey() + " packets (vanilla max " + max + ")");
                }
            }
        }
        return problems;
    }
}
