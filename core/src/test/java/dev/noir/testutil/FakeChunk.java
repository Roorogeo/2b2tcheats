package dev.noir.testutil;

import dev.noir.basehunt.ChunkView;

import java.util.ArrayList;
import java.util.List;

/** A chunk for tests: stone up to {@code ground}, air above, then edited. */
public final class FakeChunk implements ChunkView {
    private final String dimension;
    private final int cx, cz, minY, maxY;
    private final String[] blocks;
    private final boolean[] flowing;
    private final List<String> blockEntities = new ArrayList<>();

    public FakeChunk(String dimension, int cx, int cz, int minY, int maxY, int ground, String fill) {
        this.dimension = dimension;
        this.cx = cx;
        this.cz = cz;
        this.minY = minY;
        this.maxY = maxY;
        int h = maxY - minY;
        this.blocks = new String[16 * 16 * h];
        this.flowing = new boolean[blocks.length];
        for (int y = minY; y < maxY; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    blocks[i(x, y, z)] = y < ground ? fill : "minecraft:air";
                }
            }
        }
    }

    /** Overworld chunk: deepslate below 0, stone 0..62, air above. */
    public static FakeChunk overworld(int cx, int cz) {
        FakeChunk c = new FakeChunk("overworld", cx, cz, -64, 320, 63, "minecraft:stone");
        c.fill(0, -64, 0, 15, -1, 15, "minecraft:deepslate");
        return c;
    }

    private int i(int x, int y, int z) {
        return ((y - minY) << 8) | (z << 4) | x;
    }

    public FakeChunk set(int x, int y, int z, String id) {
        blocks[i(x, y, z)] = id.contains(":") ? id : "minecraft:" + id;
        return this;
    }

    public FakeChunk fill(int x0, int y0, int z0, int x1, int y1, int z1, String id) {
        for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) for (int x = x0; x <= x1; x++) set(x, y, z, id);
        return this;
    }

    public FakeChunk flowing(int x, int y, int z) {
        set(x, y, z, "water");
        flowing[i(x, y, z)] = true;
        return this;
    }

    public FakeChunk blockEntity(String id) {
        blockEntities.add(id);
        return this;
    }

    @Override public String dimension() { return dimension; }
    @Override public int chunkX() { return cx; }
    @Override public int chunkZ() { return cz; }
    @Override public int minY() { return minY; }
    @Override public int maxY() { return maxY; }
    @Override public String block(int x, int y, int z) { return blocks[i(x, y, z)]; }
    @Override public boolean flowingFluid(int x, int y, int z) { return flowing[i(x, y, z)]; }
    @Override public List<String> blockEntities() { return blockEntities; }
}
