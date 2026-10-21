package dev.noir.module.basehunting;

import dev.noir.basehunt.BaseHunt;
import dev.noir.basehunt.ChunkKey;
import dev.noir.basehunt.ChunkScan;
import dev.noir.event.Events;
import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.ColorSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.render.CameraInfo;
import dev.noir.render.RenderOptions;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Highlights nether portals, end portals and end gateways, one box per portal. */
public final class PortalESP extends BaseHuntModule implements BaseHunt.BlockInterest {
    /** A connected group of portal blocks. */
    public record Portal(String id, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}

    private final BoolSetting nether = bool("Nether Portals", true, "Find nether portals.");
    private final BoolSetting end = bool("End Portals", true, "Find end portals.");
    private final BoolSetting gateways = bool("End Gateways", true, "Find end gateways.");
    private final ColorSetting netherColor = color("Nether Color", 0xFFFFFFFF, "Nether portal color.");
    private final ColorSetting endColor = color("End Color", 0xFFA3A3A3, "End portal and gateway color.");
    private final NumberSetting distance = number("Render Distance", 32, 2, 128, 1, "Chunks farther than this aren't drawn.").unit(" ch");
    private final RenderOptions render = renderOptions("Render", false);
    private final Alerts alerts = alerts("portal", true, true);

    private final Map<ChunkKey, List<Portal>> portals = new ConcurrentHashMap<>();

    public PortalESP() {
        super("PortalESP", "Highlights nether portals, end portals and end gateways in loaded chunks.");
        on(BaseHunt.ScanDone.class, e -> index(e.scan()));
        Runnable rescan = () -> {
            if (isEnabled()) hunt().scans().rescanLoaded();
        };
        nether.onChange(v -> rescan.run());
        end.onChange(v -> rescan.run());
        gateways.onChange(v -> rescan.run());
        on(Events.RenderWorld3D.class, this::draw);
        on(Events.TickPost.class, e -> core().hud().publishCount("Portals", count()));
    }

    @Override
    public Set<String> interestingBlocks() {
        Set<String> ids = new HashSet<>();
        if (nether.get()) ids.add("minecraft:nether_portal");
        if (end.get()) ids.add("minecraft:end_portal");
        if (gateways.get()) ids.add("minecraft:end_gateway");
        return ids;
    }

    @Override
    protected void onEnable() {
        for (ChunkScan s : hunt().scans().all()) index(s);
    }

    @Override
    protected void onDisable() {
        core().hud().clearCount("Portals");
    }

    @Override
    public String info() {
        return Integer.toString(count());
    }

    private int count() {
        String dim = hunt().dimension();
        int n = 0;
        for (Map.Entry<ChunkKey, List<Portal>> e : portals.entrySet()) if (e.getKey().dimension().equals(dim)) n += e.getValue().size();
        return n;
    }

    private void index(ChunkScan s) {
        Set<String> wanted = interestingBlocks();
        List<Portal> found = cluster(s.finds, wanted);
        if (found.isEmpty()) portals.remove(s.key);
        else portals.put(s.key, found);
        for (Portal p : found) {
            String type = p.id().equals("minecraft:nether_portal") ? "Nether portal" : p.id().equals("minecraft:end_portal") ? "End portal" : "End gateway";
            reportOnce(s.key.dimension() + p.minX() + "," + p.minY() + "," + p.minZ(), alerts, type, p.minX(), p.minY(), p.minZ(),
                colorOf(p.id()), "");
        }
    }

    /** Groups face-adjacent finds of the same id into portals. */
    public static List<Portal> cluster(List<ChunkScan.Find> finds, Set<String> ids) {
        Map<Long, ChunkScan.Find> byPos = new HashMap<>();
        for (ChunkScan.Find f : finds) if (ids.contains(f.id())) byPos.put(pack(f.x(), f.y(), f.z()), f);
        List<Portal> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (Map.Entry<Long, ChunkScan.Find> start : byPos.entrySet()) {
            if (!seen.add(start.getKey())) continue;
            ChunkScan.Find s = start.getValue();
            int minX = s.x(), minY = s.y(), minZ = s.z(), maxX = s.x(), maxY = s.y(), maxZ = s.z();
            ArrayDeque<ChunkScan.Find> q = new ArrayDeque<>();
            q.add(s);
            while (!q.isEmpty()) {
                ChunkScan.Find f = q.poll();
                minX = Math.min(minX, f.x());
                minY = Math.min(minY, f.y());
                minZ = Math.min(minZ, f.z());
                maxX = Math.max(maxX, f.x());
                maxY = Math.max(maxY, f.y());
                maxZ = Math.max(maxZ, f.z());
                for (int[] d : dirs) {
                    long k = pack(f.x() + d[0], f.y() + d[1], f.z() + d[2]);
                    ChunkScan.Find n = byPos.get(k);
                    if (n != null && n.id().equals(s.id()) && seen.add(k)) q.add(n);
                }
            }
            out.add(new Portal(s.id(), minX, minY, minZ, maxX, maxY, maxZ));
        }
        return out;
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private int colorOf(String id) {
        return id.equals("minecraft:nether_portal") ? netherColor.argb() : endColor.argb();
    }

    private void draw(Events.RenderWorld3D e) {
        CameraInfo cam = e.canvas().camera();
        String dim = hunt().dimension();
        Set<String> wanted = interestingBlocks();
        for (Map.Entry<ChunkKey, List<Portal>> entry : portals.entrySet()) {
            if (!entry.getKey().dimension().equals(dim) || !inRange(entry.getKey(), cam.x(), cam.z(), distance.getInt())) continue;
            for (Portal p : entry.getValue()) {
                if (!wanted.contains(p.id())) continue;
                int c = colorOf(p.id());
                render.box(e.canvas(), p.minX(), p.minY(), p.minZ(), p.maxX() + 1, p.maxY() + 1, p.maxZ() + 1, c);
                render.tracer(e.canvas(), (p.minX() + p.maxX() + 1) / 2.0, (p.minY() + p.maxY() + 1) / 2.0, (p.minZ() + p.maxZ() + 1) / 2.0, c);
            }
        }
    }
}
