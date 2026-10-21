package dev.noir.basehunt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds old chunks that form paths through new terrain — what a player travelling
 * earlier leaves behind. Old chunks are grouped into 8-connected components; a
 * component is a trail when it is long and thin (principal-axis length vs. width)
 * and mostly surrounded by new chunks.
 */
public final class TrailDetector {
    private TrailDetector() {}

    public record Trail(List<ChunkKey> chunks, double length, double width) {}

    public static List<Trail> detect(Set<ChunkKey> oldChunks, Set<ChunkKey> newChunks, int minLength, double maxWidth,
                                     double minNewNeighborRatio) {
        List<Trail> trails = new ArrayList<>();
        Set<ChunkKey> seen = new HashSet<>();
        for (ChunkKey start : oldChunks) {
            if (!seen.add(start)) continue;
            List<ChunkKey> comp = new ArrayList<>();
            ArrayDeque<ChunkKey> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                ChunkKey k = queue.poll();
                comp.add(k);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        ChunkKey n = k.offset(dx, dz);
                        if (oldChunks.contains(n) && seen.add(n)) queue.add(n);
                    }
                }
            }
            if (comp.size() < minLength) continue;

            double[] shape = principalExtent(comp);
            double length = shape[0], width = shape[1];
            if (length + 1 < minLength || width > maxWidth) continue;

            int boundary = 0, boundaryNew = 0;
            Set<ChunkKey> compSet = new HashSet<>(comp);
            Set<ChunkKey> counted = new HashSet<>();
            for (ChunkKey k : comp) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        ChunkKey n = k.offset(dx, dz);
                        if (compSet.contains(n) || !counted.add(n)) continue;
                        if (newChunks.contains(n)) {
                            boundary++;
                            boundaryNew++;
                        } else if (oldChunks.contains(n)) {
                            boundary++;
                        }
                    }
                }
            }
            if (boundary == 0 || boundaryNew / (double) boundary < minNewNeighborRatio) continue;
            trails.add(new Trail(comp, length + 1, width + 1));
        }
        return trails;
    }

    /** {extent along the principal axis, extent across it}, in chunks. */
    static double[] principalExtent(List<ChunkKey> pts) {
        double mx = 0, mz = 0;
        for (ChunkKey k : pts) {
            mx += k.x();
            mz += k.z();
        }
        mx /= pts.size();
        mz /= pts.size();
        double sxx = 0, szz = 0, sxz = 0;
        for (ChunkKey k : pts) {
            double dx = k.x() - mx, dz = k.z() - mz;
            sxx += dx * dx;
            szz += dz * dz;
            sxz += dx * dz;
        }
        double angle = 0.5 * Math.atan2(2 * sxz, sxx - szz);
        double ax = Math.cos(angle), az = Math.sin(angle);
        double minA = Double.MAX_VALUE, maxA = -Double.MAX_VALUE, minB = Double.MAX_VALUE, maxB = -Double.MAX_VALUE;
        for (ChunkKey k : pts) {
            double dx = k.x() - mx, dz = k.z() - mz;
            double a = dx * ax + dz * az;
            double b = -dx * az + dz * ax;
            minA = Math.min(minA, a);
            maxA = Math.max(maxA, a);
            minB = Math.min(minB, b);
            maxB = Math.max(maxB, b);
        }
        return new double[] {maxA - minA, maxB - minB};
    }
}
