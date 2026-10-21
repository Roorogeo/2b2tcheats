package dev.noir.basehunt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Single pass over a chunk producing a {@link ChunkScan}. Pure; runs on a worker thread. */
public final class ChunkScanner {
    private ChunkScanner() {}

    public static ChunkScan scan(ChunkView v, Set<String> interesting, long nowMs) {
        int minY = v.minY(), maxY = v.maxY();
        int height = maxY - minY;
        int sections = (height + 15) >> 4;
        String[] blocks = new String[16 * 16 * height];
        int flowing = 0;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    String id = v.block(x, y + minY, z);
                    blocks[index(x, y, z)] = id;
                    if (!Blocks.isAir(id) && v.flowingFluid(x, y + minY, z)) flowing++;
                }
            }
        }

        Map<String, int[]> counts = new HashMap<>();
        int[] exposedStone = new int[sections], exposedOre = new int[sections];
        int[] hiddenStone = new int[sections], hiddenOre = new int[sections];
        List<ChunkScan.Find> finds = new ArrayList<>();
        int baseX = v.chunkX() << 4, baseZ = v.chunkZ() << 4;

        for (int y = 0; y < height; y++) {
            int s = y >> 4;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    String id = blocks[index(x, y, z)];
                    if (Blocks.isAir(id)) continue;
                    counts.computeIfAbsent(id, k -> new int[sections])[s]++;
                    if (interesting.contains(id)) finds.add(new ChunkScan.Find(baseX + x, y + minY, baseZ + z, id));
                    boolean ore = Blocks.isOre(id);
                    if (ore || Blocks.HOST_STONE.contains(id)) {
                        boolean exposed = exposed(blocks, x, y, z, height);
                        if (ore) {
                            if (exposed) exposedOre[s]++;
                            else hiddenOre[s]++;
                        } else {
                            if (exposed) exposedStone[s]++;
                            else hiddenStone[s]++;
                        }
                    }
                }
            }
        }

        Map<String, Integer> be = new HashMap<>();
        for (String id : v.blockEntities()) be.merge(id, 1, Integer::sum);
        return new ChunkScan(v.key(), minY, sections, counts, exposedStone, exposedOre, hiddenStone, hiddenOre, flowing, be, finds,
            Set.copyOf(interesting), nowMs);
    }

    private static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    /**
     * Touches air sideways (a wall of a tunnel or cave). Upward-facing ground is ignored so
     * open terrain doesn't count as exposed. Neighbors outside the chunk count as unknown.
     */
    private static boolean exposed(String[] b, int x, int y, int z, int height) {
        return (x > 0 && Blocks.isAir(b[index(x - 1, y, z)]))
            || (x < 15 && Blocks.isAir(b[index(x + 1, y, z)]))
            || (z > 0 && Blocks.isAir(b[index(x, y, z - 1)]))
            || (z < 15 && Blocks.isAir(b[index(x, y, z + 1)]));
    }
}
