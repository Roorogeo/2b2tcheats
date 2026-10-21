package dev.noir.module.basehunting;

import dev.noir.basehunt.ChunkKey;
import dev.noir.basehunt.NewChunkTracker;
import dev.noir.basehunt.TrailDetector;
import dev.noir.event.Events;
import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.ColorSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.render.CameraInfo;
import dev.noir.render.Shapes;
import dev.noir.util.Colors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Highlights old chunks that form long, thin paths through new terrain — trails left by
 * players who travelled before. Uses the new/old classification shared with NewChunks.
 */
public final class Trails extends BaseHuntModule {
    private final NumberSetting minLength = number("Min Trail Length", 6, 3, 64, 1, "Shortest trail, in chunks.").unit(" ch");
    private final NumberSetting maxWidth = number("Max Trail Width", 3, 1, 8, 0.5, "Widest a trail may be, in chunks.").unit(" ch");
    private final NumberSetting newRatio = number("Min New Neighbors", 0.5, 0, 1, 0.05,
        "Fraction of the trail's neighbors that must be new chunks.");
    private final NumberSetting interval = number("Update Interval", 40, 10, 400, 10, "Ticks between trail searches.").unit("t");
    private final ColorSetting color = color("Color", 0xFFFFFFFF, "Trail color.");
    private final NumberSetting height = number("Render Height", 0, -64, 320, 1, "Y level of the trail squares.");
    private final NumberSetting opacity = number("Opacity", 0.3, 0, 1, 0.02, "Fill opacity.");
    private final BoolSetting outline = bool("Outline", true, "Outline each trail chunk.");
    private final NumberSetting lineWidth = number("Line Width", 1.5, 0.5, 6, 0.5, "Outline width.").unit("px").showWhen(outline);
    private final NumberSetting distance = number("Render Distance", 48, 2, 128, 1, "Chunks farther than this aren't drawn.").unit(" ch");
    private final Alerts alerts = alerts("trail", true, false);

    private volatile List<TrailDetector.Trail> trails = List.of();
    private int ticks;

    public Trails() {
        super("Trails", "Finds old chunks that form trails through new terrain.");
        on(Events.TickPost.class, e -> {
            if (++ticks % interval.getInt() == 0) update();
        });
        on(Events.RenderWorld3D.class, this::draw);
    }

    @Override
    public String info() {
        return Integer.toString(trails.size());
    }

    private void update() {
        String dim = hunt().dimension();
        Set<ChunkKey> old = new HashSet<>(), neu = new HashSet<>();
        for (Map.Entry<ChunkKey, NewChunkTracker.Verdict> e : hunt().newChunks().all().entrySet()) {
            if (!e.getKey().dimension().equals(dim)) continue;
            if (e.getValue().state() == NewChunkTracker.State.OLD) old.add(e.getKey());
            else if (e.getValue().state() == NewChunkTracker.State.NEW) neu.add(e.getKey());
        }
        List<TrailDetector.Trail> found = TrailDetector.detect(old, neu, minLength.getInt(), maxWidth.getDouble(), newRatio.getDouble());
        trails = new ArrayList<>(found);
        for (TrailDetector.Trail t : found) {
            ChunkKey first = t.chunks().get(0);
            reportOnce(first, alerts, "Trail", first.centerX(), 64, first.centerZ(), color.argb(),
                String.format(java.util.Locale.ROOT, "%.0f chunks long", t.length()));
        }
    }

    private void draw(Events.RenderWorld3D e) {
        CameraInfo cam = e.canvas().camera();
        int c = color.argb();
        int fill = Colors.withAlpha(c, (int) Math.round(Colors.alpha(c) * opacity.getDouble()));
        for (TrailDetector.Trail t : trails) {
            for (ChunkKey k : t.chunks()) {
                if (!inRange(k, cam.x(), cam.z(), distance.getInt())) continue;
                double x0 = k.x() << 4, z0 = k.z() << 4;
                Shapes.flatSquare(e.canvas(), x0, z0, x0 + 16, z0 + 16, height.getDouble(), fill, outline.get() ? c : 0, lineWidth.getFloat());
            }
        }
    }
}
