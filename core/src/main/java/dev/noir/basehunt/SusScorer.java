package dev.noir.basehunt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Scores a scanned chunk for signs of players. Each check is independent, has its own
 * toggle and weight, and contributes points plus a human-readable reason.
 *
 * <ul>
 *   <li><b>Storage</b>: storage blocks beyond a natural allowance (dungeons, mineshafts and
 *       villages have a few) — {@code weight × excess}.</li>
 *   <li><b>Player-placed blocks</b>: each listed block (obsidian, beds, shulkers, ender chests,
 *       crafting stations, signs, rails, glass, wool, ...) — {@code weight × min(count, cap)} per block id.</li>
 *   <li><b>Block entities</b>: total block entities beyond an allowance — {@code weight × excess}.</li>
 *   <li><b>Missing ores</b>: in stone that touches air sideways (tunnel and cave walls) the ore ratio is compared
 *       with the ratio in the chunk's unexposed stone. Strip-mined areas have walls with almost no
 *       ore left. If the exposed ratio is below {@code factor × expected}, adds up to {@code weight × 10}.</li>
 *   <li><b>Portals</b>: nether/end portal blocks — {@code weight × number of 6-block portal surfaces} (at least 1).</li>
 * </ul>
 * Only blocks within the Y range count (section granularity). Block entities count the whole chunk.
 */
public final class SusScorer {
    private SusScorer() {}

    public record Config(
        int minY, int maxY,
        boolean storage, double storageWeight, int storageAllowance,
        boolean placed, double placedWeight, int placedCap, Set<String> placedBlocks,
        boolean blockEntities, double blockEntityWeight, int blockEntityAllowance,
        boolean ores, double oreWeight, int minExposedStone, double oreRatioFactor,
        boolean portals, double portalWeight) {}

    public record Result(double score, List<String> reasons) {
        public String summary() {
            return String.join(", ", reasons);
        }
    }

    public static Result score(ChunkScan s, Config c) {
        double score = 0;
        List<String> reasons = new ArrayList<>();

        if (c.storage()) {
            int n = s.countAny(Blocks.STORAGE, c.minY(), c.maxY());
            int excess = n - c.storageAllowance();
            if (excess > 0) {
                score += excess * c.storageWeight();
                reasons.add(n + " storage");
            }
        }

        if (c.placed()) {
            int total = 0;
            double pts = 0;
            for (String id : c.placedBlocks()) {
                int n = s.count(id, c.minY(), c.maxY());
                if (n == 0) continue;
                total += n;
                pts += Math.min(n, c.placedCap()) * c.placedWeight();
            }
            if (pts > 0) {
                score += pts;
                reasons.add(total + " placed blocks");
            }
        }

        if (c.blockEntities()) {
            int n = s.blockEntityTotal();
            int excess = n - c.blockEntityAllowance();
            if (excess > 0) {
                score += excess * c.blockEntityWeight();
                reasons.add(n + " block entities");
            }
        }

        if (c.ores()) {
            int expStone = s.sum(s.exposedStone, c.minY(), c.maxY());
            int expOre = s.sum(s.exposedOre, c.minY(), c.maxY());
            int hidStone = s.sum(s.hiddenStone, c.minY(), c.maxY());
            int hidOre = s.sum(s.hiddenOre, c.minY(), c.maxY());
            if (expStone >= c.minExposedStone() && hidStone >= 200 && hidOre > 0) {
                double expected = hidOre / (double) (hidStone + hidOre);
                double observed = expOre / (double) (expStone + expOre);
                double limit = expected * c.oreRatioFactor();
                if (observed < limit) {
                    double deficit = 1.0 - observed / limit;
                    score += deficit * 10 * c.oreWeight();
                    reasons.add(String.format(Locale.ROOT, "mined walls (%d exposed, %.1f%% ore vs %.1f%%)",
                        expStone, observed * 100, expected * 100));
                }
            }
        }

        if (c.portals()) {
            int n = s.countAny(Blocks.PORTALS, c.minY(), c.maxY());
            if (n > 0) {
                score += Math.max(1, Math.ceil(n / 6.0)) * c.portalWeight();
                reasons.add(n + " portal blocks");
            }
        }
        return new Result(score, reasons);
    }
}
