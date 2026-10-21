package dev.noir.module.basehunting;

import dev.noir.basehunt.BaseHunt;
import dev.noir.basehunt.Blocks;
import dev.noir.basehunt.ChunkKey;
import dev.noir.basehunt.ChunkScan;
import dev.noir.basehunt.SusScorer;
import dev.noir.event.Events;
import dev.noir.module.settings.BlockListSetting;
import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.ColorSetting;
import dev.noir.module.settings.EnumSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.render.CameraInfo;
import dev.noir.render.RenderOptions;
import dev.noir.render.Shapes;
import dev.noir.util.Colors;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Scores every loaded chunk for signs of players and highlights suspicious ones.
 * See {@link SusScorer} for the checks. Scores are recomputed from cached scans when
 * settings change, so tuning weights never rescans.
 */
public final class SusChunk extends BaseHuntModule {
    public enum Style {
        FLAT("Flat"),
        COLUMN("Column");

        private final String label;

        Style(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    private final NumberSetting threshold = number("Score Threshold", 10, 1, 200, 0.5, "Chunks scoring at least this are suspicious.");
    private final NumberSetting minY = number("Min Y", -64, -64, 320, 1, "Lowest Y counted by the block checks.");
    private final NumberSetting maxY = number("Max Y", 320, -64, 320, 1, "Highest Y counted by the block checks.");
    private final NumberSetting distance = number("Render Distance", 32, 2, 128, 1, "Chunks farther than this aren't drawn.").unit(" ch");
    private final BoolSetting clearCache = bool("Clear Cache", false, "Turn on to forget all scores and rescan loaded chunks.");

    private final BoolSetting storage;
    private final NumberSetting storageWeight;
    private final NumberSetting storageAllowance;
    private final BoolSetting placed;
    private final NumberSetting placedWeight;
    private final NumberSetting placedCap;
    private final BlockListSetting placedBlocks;
    private final BoolSetting blockEntities;
    private final NumberSetting blockEntityWeight;
    private final NumberSetting blockEntityAllowance;
    private final BoolSetting ores;
    private final NumberSetting oreWeight;
    private final NumberSetting minExposed;
    private final NumberSetting oreFactor;
    private final BoolSetting portals;
    private final NumberSetting portalWeight;

    private final EnumSetting<Style> style;
    private final NumberSetting renderHeight;
    private final BoolSetting colorByScore;
    private final ColorSetting color;
    private final ColorSetting highColor;
    private final RenderOptions render;
    private final Alerts alerts;

    private final Map<ChunkKey, SusScorer.Result> results = new ConcurrentHashMap<>();
    private final Map<ChunkKey, ChunkScan> scans = new ConcurrentHashMap<>();
    private boolean dirty;

    public SusChunk() {
        super("SusChunk", "Scores loaded chunks for signs of players (storage, placed blocks, mined walls, portals) and highlights suspicious ones.");

        group("Storage", "Storage blocks beyond what generates naturally.");
        storage = bool("Storage Check", true, "Count chests, barrels, shulkers, ender chests, hoppers, droppers and dispensers.");
        storageWeight = number("Storage Weight", 2, 0, 20, 0.5, "Points per storage block over the allowance.").showWhen(storage);
        storageAllowance = number("Storage Allowance", 2, 0, 32, 1, "Storage blocks a chunk may have naturally (dungeons, mineshafts).").showWhen(storage);

        group("Player-Placed Blocks", "Blocks that rarely or never generate naturally.");
        placed = bool("Placed Check", true, "Score the blocks in the list below.");
        placedWeight = number("Placed Weight", 1, 0, 10, 0.25, "Points per listed block.").showWhen(placed);
        placedCap = number("Per-Block Cap", 16, 1, 256, 1, "Each block type counts at most this many times, so one big wall doesn't dominate.").showWhen(placed);
        placedBlocks = blocks("Placed Blocks", 0xFFFFFFFF, "Blocks counted as player-placed.", Blocks.defaultPlacedBlocks()).showWhen(placed);

        group("Block Entities", "Unusually many block entities.");
        blockEntities = bool("Block Entity Check", true, "Score the chunk's total block entity count.");
        blockEntityWeight = number("Block Entity Weight", 0.5, 0, 10, 0.25, "Points per block entity over the allowance.").showWhen(blockEntities);
        blockEntityAllowance = number("Block Entity Allowance", 4, 0, 64, 1, "Block entities a chunk may have naturally.").showWhen(blockEntities);

        group("Missing Ores", "Mined-out walls: exposed stone with far less ore than the chunk's buried stone.");
        ores = bool("Missing Ore Check", true, "Compare the ore ratio of exposed stone with buried stone.");
        oreWeight = number("Ore Weight", 1, 0, 10, 0.25, "Up to 10 x this many points for a fully mined-out chunk.").showWhen(ores);
        minExposed = number("Min Exposed Stone", 96, 16, 2048, 16, "Exposed stone needed before the check applies.").showWhen(ores);
        oreFactor = number("Ore Ratio Factor", 0.35, 0.05, 1, 0.05, "Suspicious when exposed ore ratio is below this fraction of the buried ratio.").showWhen(ores);

        group("Portals", "Nether and end portals.");
        portals = bool("Portal Check", true, "Score portal blocks.");
        portalWeight = number("Portal Weight", 5, 0, 50, 0.5, "Points per portal.").showWhen(portals);

        group("Display", "How suspicious chunks look.");
        style = mode("Style", Style.FLAT, "Flat: a square at Render Height. Column: a box over the whole Y range.");
        renderHeight = number("Render Height", 64, -64, 320, 1, "Height of the flat square.").visibleWhen(() -> style.get() == Style.FLAT);
        colorByScore = bool("Color By Score", true, "Blend from Color to High Color as the score rises.");
        color = color("Color", 0xFFA3A3A3, "Color at the threshold.");
        highColor = color("High Color", 0xFFFFFFFF, "Color at 3x the threshold.").showWhen(colorByScore);
        render = renderOptions("Render", false);
        alerts = alerts("suspicious chunk", true, false);

        onAnySettingChange(() -> dirty = true);
        clearCache.onChange(v -> {
            if (!v) return;
            clearCache.set(false);
            results.clear();
            scans.clear();
            forgetAlerts();
            hunt().scans().clear();
            hunt().scans().rescanLoaded();
        });

        on(BaseHunt.ScanDone.class, e -> {
            scans.put(e.scan().key, e.scan());
            evaluate(e.scan());
        });
        on(Events.TickPost.class, e -> {
            if (dirty) {
                dirty = false;
                results.clear();
                for (ChunkScan s : scans.values()) evaluate(s);
            }
            core().hud().publishCount("SusChunk", hits());
        });
        on(Events.RenderWorld3D.class, e -> draw(e));
    }

    @Override
    protected void onEnable() {
        for (ChunkScan s : hunt().scans().all()) {
            scans.put(s.key, s);
            evaluate(s);
        }
    }

    @Override
    protected void onDisable() {
        core().hud().clearCount("SusChunk");
    }

    @Override
    public String info() {
        return Integer.toString(hits());
    }

    public SusScorer.Config config() {
        return new SusScorer.Config(minY.getInt(), maxY.getInt(),
            storage.get(), storageWeight.getDouble(), storageAllowance.getInt(),
            placed.get(), placedWeight.getDouble(), placedCap.getInt(), placedBlocks.ids(),
            blockEntities.get(), blockEntityWeight.getDouble(), blockEntityAllowance.getInt(),
            ores.get(), oreWeight.getDouble(), minExposed.getInt(), oreFactor.getDouble(),
            portals.get(), portalWeight.getDouble());
    }

    private void evaluate(ChunkScan s) {
        SusScorer.Result r = SusScorer.score(s, config());
        results.put(s.key, r);
        if (r.score() >= threshold.getDouble()) {
            reportOnce(s.key, alerts, "Suspicious chunk", s.key.centerX(), 64, s.key.centerZ(), color.argb(),
                String.format(java.util.Locale.ROOT, "score %.1f: %s", r.score(), r.summary()));
        }
    }

    public SusScorer.Result result(ChunkKey key) {
        return results.get(key);
    }

    public int hits() {
        String dim = hunt().dimension();
        int n = 0;
        for (Map.Entry<ChunkKey, SusScorer.Result> e : results.entrySet()) {
            if (e.getKey().dimension().equals(dim) && e.getValue().score() >= threshold.getDouble()) n++;
        }
        return n;
    }

    private int colorFor(double score) {
        if (!colorByScore.get()) return color.argb();
        double t = (score - threshold.getDouble()) / (threshold.getDouble() * 2);
        return Colors.lerp(color.argb(), highColor.argb(), (float) Math.max(0, Math.min(1, t)));
    }

    private void draw(Events.RenderWorld3D e) {
        CameraInfo cam = e.canvas().camera();
        String dim = hunt().dimension();
        for (Map.Entry<ChunkKey, SusScorer.Result> entry : results.entrySet()) {
            ChunkKey k = entry.getKey();
            double score = entry.getValue().score();
            if (score < threshold.getDouble() || !k.dimension().equals(dim) || !inRange(k, cam.x(), cam.z(), distance.getInt())) continue;
            int c = colorFor(score);
            double x0 = k.x() << 4, z0 = k.z() << 4;
            if (style.get() == Style.FLAT) {
                Shapes.flatSquare(e.canvas(), x0, z0, x0 + 16, z0 + 16, renderHeight.getDouble(), render.fillColor(c), render.outlineColor(c),
                    render.lineWidth.getFloat());
            } else {
                render.box(e.canvas(), x0, minY.getDouble(), z0, x0 + 16, maxY.getDouble(), z0 + 16, c);
            }
            render.tracer(e.canvas(), x0 + 8, renderHeight.getDouble(), z0 + 8, c);
        }
    }
}
