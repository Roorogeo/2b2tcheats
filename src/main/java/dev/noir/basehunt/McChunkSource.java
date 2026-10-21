package dev.noir.basehunt;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks loaded chunks from Fabric's chunk load/unload events. */
public final class McChunkSource implements ChunkSource {
    private final Minecraft mc;
    private final Map<Long, McChunkView> chunks = new ConcurrentHashMap<>();

    public McChunkSource(Minecraft mc) {
        this.mc = mc;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    public void onLoad(McChunkView view) {
        chunks.put(key(view.chunkX(), view.chunkZ()), view);
    }

    public void onUnload(int x, int z) {
        chunks.remove(key(x, z));
    }

    public void clear() {
        chunks.clear();
    }

    @Override
    public String dimension() {
        return mc.level == null ? "overworld" : McChunkView.dimensionName(mc.level);
    }

    @Override
    public List<ChunkView> loaded() {
        return new ArrayList<>(chunks.values());
    }

    @Override
    public ChunkView get(int chunkX, int chunkZ) {
        return chunks.get(key(chunkX, chunkZ));
    }
}
