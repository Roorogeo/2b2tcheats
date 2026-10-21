package dev.noir.basehunt;

import java.util.List;

/**
 * Read-only access to one loaded chunk, as already sent by the server. The scanner
 * reads it from a worker thread, so implementations must only read. Block ids are
 * registry ids ("minecraft:chest") and should be interned/cached by the implementation.
 */
public interface ChunkView {
    String dimension();

    int chunkX();

    int chunkZ();

    /** Lowest block y (inclusive). */
    int minY();

    /** Highest block y (exclusive). */
    int maxY();

    /** Block id at local x/z (0..15) and absolute y. */
    String block(int localX, int y, int localZ);

    /** True if the position holds a flowing (non-source) liquid. */
    boolean flowingFluid(int localX, int y, int localZ);

    /** Block entity type ids in the chunk (captured on the game thread). */
    List<String> blockEntities();

    default ChunkKey key() {
        return new ChunkKey(dimension(), chunkX(), chunkZ());
    }
}
