package dev.noir.module.basehunting;

import dev.noir.basehunt.BaseHunt;
import dev.noir.basehunt.ChunkScan;
import dev.noir.event.Events;
import dev.noir.module.settings.BlockListSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.render.CameraInfo;
import dev.noir.render.RenderOptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Block ESP for a custom block list, with per-block colors. */
public final class Search extends BaseHuntModule implements BaseHunt.BlockInterest {
    private final BlockListSetting blocks = blocks("Blocks", 0xFFFFFFFF, "Blocks to find. Each entry has its own color.",
        "spawner", "ender_chest", "shulker_box", "beacon", "end_portal_frame");
    private final NumberSetting maxDistance = number("Max Distance", 128, 8, 1024, 8, "Finds farther than this aren't drawn.").unit("m");
    private final NumberSetting maxCount = number("Max Render Count", 400, 10, 5000, 10, "Draw at most this many of the nearest finds.");
    private final RenderOptions render = renderOptions("Render", false);

    private volatile List<ChunkScan.Find> visible = List.of();

    public Search() {
        super("Search", "Highlights blocks from a custom list in loaded chunks.");
        blocks.onChange(v -> {
            if (isEnabled()) hunt().scans().rescanLoaded();
        });
        on(Events.RenderWorld3D.class, this::draw);
        on(Events.TickPost.class, e -> core().hud().publishCount("Search", visible.size()));
    }

    @Override
    public Set<String> interestingBlocks() {
        return blocks.ids();
    }

    @Override
    protected void onDisable() {
        visible = List.of();
        core().hud().clearCount("Search");
    }

    @Override
    public String info() {
        return Integer.toString(visible.size());
    }

    /** Nearest matching finds within range in the current dimension. */
    public List<ChunkScan.Find> nearest(CameraInfo cam) {
        String dim = hunt().dimension();
        Set<String> ids = blocks.ids();
        double max = maxDistance.getDouble();
        List<ChunkScan.Find> out = new ArrayList<>();
        for (ChunkScan s : hunt().scans().all()) {
            if (!s.key.dimension().equals(dim)) continue;
            double cdx = s.key.centerX() - cam.x(), cdz = s.key.centerZ() - cam.z();
            if (Math.sqrt(cdx * cdx + cdz * cdz) > max + 23) continue;
            for (ChunkScan.Find f : s.finds) {
                if (ids.contains(f.id()) && cam.distanceTo(f.x() + 0.5, f.y() + 0.5, f.z() + 0.5) <= max) out.add(f);
            }
        }
        out.sort(Comparator.comparingDouble(f -> cam.distanceTo(f.x() + 0.5, f.y() + 0.5, f.z() + 0.5)));
        return out.size() > maxCount.getInt() ? out.subList(0, maxCount.getInt()) : out;
    }

    private void draw(Events.RenderWorld3D e) {
        List<ChunkScan.Find> finds = nearest(e.canvas().camera());
        visible = finds;
        for (ChunkScan.Find f : finds) {
            int c = blocks.colorOf(f.id());
            render.box(e.canvas(), f.x(), f.y(), f.z(), f.x() + 1, f.y() + 1, f.z() + 1, c);
            render.tracer(e.canvas(), f.x() + 0.5, f.y() + 0.5, f.z() + 0.5, c);
        }
    }
}
