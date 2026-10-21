package dev.noir.basehunt;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the base-hunting modules need from one chunk, computed once off-thread.
 * Counts are kept per 16-block section so Y-range settings can change without rescanning.
 */
public final class ChunkScan {
    /** A block of interest (from the Search list, portals or storage). */
    public record Find(int x, int y, int z, String id) {}

    public final ChunkKey key;
    public final int minY;
    public final int sections;
    public final Map<String, int[]> counts;
    public final int[] exposedStone;
    public final int[] exposedOre;
    public final int[] hiddenStone;
    public final int[] hiddenOre;
    public final int flowingFluids;
    public final Map<String, Integer> blockEntities;
    public final List<Find> finds;
    public final Set<String> interestingAtScan;
    public final long scannedAtMs;

    ChunkScan(ChunkKey key, int minY, int sections, Map<String, int[]> counts, int[] exposedStone, int[] exposedOre,
              int[] hiddenStone, int[] hiddenOre, int flowingFluids, Map<String, Integer> blockEntities, List<Find> finds,
              Set<String> interestingAtScan, long scannedAtMs) {
        this.key = key;
        this.minY = minY;
        this.sections = sections;
        this.counts = Collections.unmodifiableMap(counts);
        this.exposedStone = exposedStone;
        this.exposedOre = exposedOre;
        this.hiddenStone = hiddenStone;
        this.hiddenOre = hiddenOre;
        this.flowingFluids = flowingFluids;
        this.blockEntities = Collections.unmodifiableMap(blockEntities);
        this.finds = Collections.unmodifiableList(finds);
        this.interestingAtScan = interestingAtScan;
        this.scannedAtMs = scannedAtMs;
    }

    /** Section index range [from, to) covering block y in [yMin, yMax]. */
    public int sectionFrom(int yMin) {
        return Math.max(0, Math.min(sections, (yMin - minY) >> 4));
    }

    public int sectionTo(int yMax) {
        return Math.max(0, Math.min(sections, ((yMax - minY) >> 4) + 1));
    }

    /** Count of {@code id} with y in [yMin, yMax] (section granularity). */
    public int count(String id, int yMin, int yMax) {
        int[] c = counts.get(id);
        if (c == null) return 0;
        int n = 0;
        for (int s = sectionFrom(yMin); s < sectionTo(yMax); s++) n += c[s];
        return n;
    }

    public int count(String id) {
        int[] c = counts.get(id);
        if (c == null) return 0;
        int n = 0;
        for (int v : c) n += v;
        return n;
    }

    public int countAny(Set<String> ids, int yMin, int yMax) {
        int n = 0;
        for (String id : ids) n += count(id, yMin, yMax);
        return n;
    }

    public int sum(int[] perSection, int yMin, int yMax) {
        int n = 0;
        for (int s = sectionFrom(yMin); s < sectionTo(yMax); s++) n += perSection[s];
        return n;
    }

    public int blockEntityTotal() {
        int n = 0;
        for (int v : blockEntities.values()) n += v;
        return n;
    }
}
