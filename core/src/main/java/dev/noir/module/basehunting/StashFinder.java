package dev.noir.module.basehunting;

import dev.noir.basehunt.BaseHunt;
import dev.noir.basehunt.Blocks;
import dev.noir.basehunt.ChunkKey;
import dev.noir.basehunt.ChunkScan;
import dev.noir.event.Events;
import dev.noir.module.settings.BlockListSetting;
import dev.noir.module.settings.ColorSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.render.CameraInfo;
import dev.noir.render.RenderOptions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Chunks holding at least a set number of storage blocks. */
public final class StashFinder extends BaseHuntModule {
    private final NumberSetting threshold = number("Storage Threshold", 8, 1, 512, 1, "Storage blocks in one chunk needed to count as a stash.");
    private final BlockListSetting storage = blocks("Storage Blocks", 0xFFFFFFFF, "Blocks counted as storage.",
        Blocks.STORAGE.toArray(String[]::new));
    private final NumberSetting minY = number("Min Y", -64, -64, 320, 1, "Lowest Y counted.");
    private final NumberSetting maxY = number("Max Y", 320, -64, 320, 1, "Highest Y counted.");
    private final ColorSetting color = color("Color", 0xFFFFFFFF, "Stash color.");
    private final NumberSetting distance = number("Render Distance", 32, 2, 128, 1, "Chunks farther than this aren't drawn.").unit(" ch");
    private final RenderOptions render = renderOptions("Render", true);
    private final Alerts alerts = alerts("stash", true, true);

    private final Map<ChunkKey, Integer> stashes = new ConcurrentHashMap<>();

    public StashFinder() {
        super("StashFinder", "Finds chunks with many storage blocks.");
        on(BaseHunt.ScanDone.class, e -> evaluate(e.scan()));
        on(Events.RenderWorld3D.class, this::draw);
        on(Events.TickPost.class, e -> core().hud().publishCount("StashFinder", count()));
        threshold.onChange(v -> rescore());
        storage.onChange(v -> rescore());
    }

    @Override
    protected void onEnable() {
        rescore();
    }

    @Override
    protected void onDisable() {
        core().hud().clearCount("StashFinder");
    }

    @Override
    public String info() {
        return Integer.toString(count());
    }

    private int count() {
        String dim = hunt().dimension();
        int n = 0;
        for (ChunkKey k : stashes.keySet()) if (k.dimension().equals(dim)) n++;
        return n;
    }

    private void rescore() {
        stashes.clear();
        if (core() == null) return;
        for (ChunkScan s : hunt().scans().all()) evaluate(s);
    }

    private void evaluate(ChunkScan s) {
        int n = s.countAny(storage.ids(), minY.getInt(), maxY.getInt());
        if (n >= threshold.getInt()) {
            stashes.put(s.key, n);
            reportOnce(s.key, alerts, "Stash", s.key.centerX(), 64, s.key.centerZ(), color.argb(), n + " storage blocks");
        } else {
            stashes.remove(s.key);
        }
    }

    public Map<ChunkKey, Integer> stashes() {
        return stashes;
    }

    private void draw(Events.RenderWorld3D e) {
        CameraInfo cam = e.canvas().camera();
        String dim = hunt().dimension();
        for (ChunkKey k : stashes.keySet()) {
            if (!k.dimension().equals(dim) || !inRange(k, cam.x(), cam.z(), distance.getInt())) continue;
            double x0 = k.x() << 4, z0 = k.z() << 4;
            render.box(e.canvas(), x0, minY.getDouble(), z0, x0 + 16, maxY.getDouble(), z0 + 16, color.argb());
            render.tracer(e.canvas(), x0 + 8, cam.y(), z0 + 8, color.argb());
        }
    }
}
