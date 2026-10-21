package dev.noir.basehunt;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link ChunkView} over a client chunk. Block entity ids are captured on the game thread
 * at construction; block reads happen on the scanner thread and only read.
 */
public final class McChunkView implements ChunkView {
    private static final Map<Block, String> IDS = new IdentityHashMap<>();

    private final LevelChunk chunk;
    private final String dimension;
    private final int cx;
    private final int cz;
    private final int minY;
    private final int maxY;
    private final List<String> blockEntities;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    public McChunkView(ClientLevel level, LevelChunk chunk) {
        this.chunk = chunk;
        this.dimension = dimensionName(level);
        this.cx = chunk.getPos().x();
        this.cz = chunk.getPos().z();
        this.minY = level.getMinY();
        this.maxY = level.getMinY() + level.getHeight();
        List<String> be = new ArrayList<>();
        for (BlockEntity e : chunk.getBlockEntities().values()) be.add(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(e.getType()).toString());
        this.blockEntities = be;
    }

    public static String dimensionName(Level level) {
        if (level.dimension() == Level.NETHER) return "the_nether";
        if (level.dimension() == Level.END) return "the_end";
        if (level.dimension() == Level.OVERWORLD) return "overworld";
        return "custom";
    }

    public static String id(BlockState state) {
        Block b = state.getBlock();
        synchronized (IDS) {
            String id = IDS.get(b);
            if (id == null) {
                id = BuiltInRegistries.BLOCK.getKey(b).toString().intern();
                IDS.put(b, id);
            }
            return id;
        }
    }

    public static boolean flowing(BlockState state) {
        FluidState f = state.getFluidState();
        return !f.isEmpty() && !f.isSource();
    }

    @Override public String dimension() { return dimension; }
    @Override public int chunkX() { return cx; }
    @Override public int chunkZ() { return cz; }
    @Override public int minY() { return minY; }
    @Override public int maxY() { return maxY; }

    @Override
    public String block(int localX, int y, int localZ) {
        return id(chunk.getBlockState(pos.set((cx << 4) + localX, y, (cz << 4) + localZ)));
    }

    @Override
    public boolean flowingFluid(int localX, int y, int localZ) {
        return flowing(chunk.getBlockState(pos.set((cx << 4) + localX, y, (cz << 4) + localZ)));
    }

    @Override
    public List<String> blockEntities() {
        return blockEntities;
    }

    public LevelChunk chunk() {
        return chunk;
    }
}
