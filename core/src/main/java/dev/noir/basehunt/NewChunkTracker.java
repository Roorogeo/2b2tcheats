package dev.noir.basehunt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classifies loaded chunks as new (generated just now) or old (generated earlier).
 *
 * <ul>
 *   <li><b>Liquid flow</b>: freshly generated terrain still has liquids settling, so the server
 *       sends flowing-liquid block updates right after the chunk loads → <i>new</i>. A chunk whose
 *       data already contains settled flowing liquid when it arrives was generated before → <i>old</i>.</li>
 *   <li><b>Version</b>: overworld chunks with blocks below y=0 but no deepslate/tuff there predate
 *       1.18; nether chunks of plain netherrack with none of the 1.16 blocks predate 1.16 → <i>old</i>.</li>
 * </ul>
 * Read-only: uses block updates and chunk data the server already sent.
 */
public final class NewChunkTracker {
    public enum State { UNKNOWN, NEW, OLD }

    public enum Reason { NONE, LIQUID_FLOW, SETTLED_LIQUID, VERSION }

    public record Verdict(State state, Reason reason) {
        public static final Verdict UNKNOWN = new Verdict(State.UNKNOWN, Reason.NONE);
    }

    private final Map<ChunkKey, Long> loadedAt = new ConcurrentHashMap<>();
    private final Map<ChunkKey, Verdict> verdicts = new ConcurrentHashMap<>();
    private long windowMs = 4000;
    private boolean liquid = true;
    private boolean version = true;

    public void configure(boolean liquidMethod, boolean versionMethod, long windowMs) {
        this.liquid = liquidMethod;
        this.version = versionMethod;
        this.windowMs = windowMs;
    }

    public void onChunkLoad(ChunkKey key, long nowMs) {
        loadedAt.put(key, nowMs);
    }

    /** A flowing-liquid block update arrived for this chunk. */
    public void onFlowingUpdate(ChunkKey key, long nowMs) {
        if (!liquid) return;
        Long at = loadedAt.get(key);
        if (at != null && nowMs - at <= windowMs) verdicts.put(key, new Verdict(State.NEW, Reason.LIQUID_FLOW));
    }

    /** Called when the chunk's scan is available. Never overrides a liquid-flow NEW verdict. */
    public void onScan(ChunkScan scan) {
        Verdict current = verdicts.get(scan.key);
        if (current != null && current.state() == State.NEW) return;
        if (version && isOldVersion(scan)) {
            verdicts.put(scan.key, new Verdict(State.OLD, Reason.VERSION));
        } else if (liquid && scan.flowingFluids > 0) {
            verdicts.put(scan.key, new Verdict(State.OLD, Reason.SETTLED_LIQUID));
        }
    }

    public static boolean isOldVersion(ChunkScan s) {
        String dim = s.key.dimension();
        if (dim.equals("overworld") && s.minY < 0) {
            int below = 0;
            for (Map.Entry<String, int[]> e : s.counts.entrySet()) below += sumBelowZero(s, e.getValue());
            if (below < 256) return false;
            int marker = 0;
            for (String id : Blocks.OVERWORLD_118) {
                int[] c = s.counts.get(id);
                if (c != null) marker += sumBelowZero(s, c);
            }
            return marker == 0;
        }
        if (dim.equals("the_nether")) {
            if (s.count("minecraft:netherrack") < 2000) return false;
            for (String id : Blocks.NETHER_116) if (s.count(id) > 0) return false;
            return true;
        }
        return false;
    }

    private static int sumBelowZero(ChunkScan s, int[] perSection) {
        int n = 0;
        int to = s.sectionTo(-1);
        for (int i = 0; i < to; i++) n += perSection[i];
        return n;
    }

    public Verdict verdict(ChunkKey key) {
        return verdicts.getOrDefault(key, Verdict.UNKNOWN);
    }

    public Map<ChunkKey, Verdict> all() {
        return verdicts;
    }

    public void clear() {
        loadedAt.clear();
        verdicts.clear();
    }
}
