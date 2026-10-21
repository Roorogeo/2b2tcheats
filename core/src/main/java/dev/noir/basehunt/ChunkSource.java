package dev.noir.basehunt;

import java.util.List;

/** The client's currently loaded chunks. Implemented by the game layer. */
public interface ChunkSource {
    String dimension();

    List<ChunkView> loaded();

    /** The loaded chunk at chunk coords, or null. */
    ChunkView get(int chunkX, int chunkZ);

    ChunkSource NONE = new ChunkSource() {
        @Override public String dimension() { return "overworld"; }
        @Override public List<ChunkView> loaded() { return List.of(); }
        @Override public ChunkView get(int chunkX, int chunkZ) { return null; }
    };
}
