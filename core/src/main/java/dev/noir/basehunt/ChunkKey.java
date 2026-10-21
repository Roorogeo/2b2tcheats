package dev.noir.basehunt;

/** A chunk position in a dimension. */
public record ChunkKey(String dimension, int x, int z) {
    public static ChunkKey of(String dimension, int blockX, int blockZ, boolean fromBlock) {
        return fromBlock ? new ChunkKey(dimension, blockX >> 4, blockZ >> 4) : new ChunkKey(dimension, blockX, blockZ);
    }

    public ChunkKey offset(int dx, int dz) {
        return new ChunkKey(dimension, x + dx, z + dz);
    }

    /** Center block coordinates. */
    public int centerX() {
        return (x << 4) + 8;
    }

    public int centerZ() {
        return (z << 4) + 8;
    }

    public double distanceSq(double blockX, double blockZ) {
        double dx = centerX() - blockX, dz = centerZ() - blockZ;
        return dx * dx + dz * dz;
    }
}
