package dev.noir.hud;

import java.util.List;
import java.util.Map;

/** Game state the HUD shows. Implemented by the Minecraft layer; read-only. */
public interface HudData {
    int fps();

    /** Round-trip latency in ms as reported by the server's player list, or -1. */
    int ping();

    /** Estimated server TPS from world time updates, or -1 while unknown. */
    double tps();

    double x();

    double y();

    double z();

    /** "overworld", "the_nether", "the_end" or a custom dimension path. */
    String dimension();

    /** Armor from head to feet; entries may be empty. */
    List<ArmorPiece> armor();

    int totems();

    /** Named hit counters published by modules (e.g. "SusChunk" -> 3). */
    Map<String, Integer> hitCounters();

    record ArmorPiece(Object stack, String name, int damage, int maxDamage) {
        public boolean empty() {
            return stack == null;
        }

        public float durability() {
            return maxDamage <= 0 ? 1f : 1f - damage / (float) maxDamage;
        }
    }
}
