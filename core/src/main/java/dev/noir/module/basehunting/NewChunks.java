package dev.noir.module.basehunting;

import dev.noir.basehunt.ChunkKey;
import dev.noir.basehunt.NewChunkTracker;
import dev.noir.event.Events;
import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.ColorSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.render.CameraInfo;
import dev.noir.render.Shapes;
import dev.noir.util.Colors;

import java.util.Map;

/** Marks new vs old chunks using liquid flow and generation-version clues. See {@link NewChunkTracker}. */
public final class NewChunks extends BaseHuntModule {
    private final BoolSetting liquid = bool("Liquid Flow", true,
        "New: flowing-liquid updates arrive right after the chunk loads. Old: the chunk arrives with liquid already settled.");
    private final BoolSetting version = bool("Version", true,
        "Old: overworld chunks without deepslate below y 0 (pre-1.18) or nether chunks without 1.16 blocks (pre-1.16).");
    private final NumberSetting window = number("Detection Window", 4000, 500, 15000, 250,
        "How long after a chunk loads liquid updates still mark it new.").unit("ms").showWhen(liquid);
    private final BoolSetting showNew = bool("Show New", true, "Draw new chunks.");
    private final BoolSetting showOld = bool("Show Old", true, "Draw old chunks.");
    private final ColorSetting newColor = color("New Color", 0xFFFFFFFF, "Color of new chunks.");
    private final ColorSetting oldColor = color("Old Color", 0xFF7A7A7A, "Color of old chunks.");
    private final NumberSetting height = number("Render Height", 0, -64, 320, 1, "Y level of the chunk squares.");
    private final NumberSetting opacity = number("Opacity", 0.18, 0, 1, 0.02, "Fill opacity of the squares.");
    private final BoolSetting outline = bool("Outline", true, "Outline each square.");
    private final NumberSetting lineWidth = number("Line Width", 1, 0.5, 6, 0.5, "Outline width.").unit("px").showWhen(outline);
    private final NumberSetting distance = number("Render Distance", 32, 2, 128, 1, "Chunks farther than this aren't drawn.").unit(" ch");

    public NewChunks() {
        super("NewChunks", "Marks chunks that were just generated (new) versus generated earlier (old).");
        Runnable apply = () -> hunt().newChunks().configure(liquid.get(), version.get(), window.getInt());
        liquid.onChange(v -> apply.run());
        version.onChange(v -> apply.run());
        window.onChange(v -> apply.run());
        on(Events.RenderWorld3D.class, this::draw);
    }

    @Override
    protected void onEnable() {
        hunt().newChunks().configure(liquid.get(), version.get(), window.getInt());
    }

    private void draw(Events.RenderWorld3D e) {
        CameraInfo cam = e.canvas().camera();
        String dim = hunt().dimension();
        for (Map.Entry<ChunkKey, NewChunkTracker.Verdict> entry : hunt().newChunks().all().entrySet()) {
            ChunkKey k = entry.getKey();
            if (!k.dimension().equals(dim) || !inRange(k, cam.x(), cam.z(), distance.getInt())) continue;
            NewChunkTracker.State st = entry.getValue().state();
            int c;
            if (st == NewChunkTracker.State.NEW && showNew.get()) c = newColor.argb();
            else if (st == NewChunkTracker.State.OLD && showOld.get()) c = oldColor.argb();
            else continue;
            double x0 = k.x() << 4, z0 = k.z() << 4;
            int fill = Colors.withAlpha(c, (int) Math.round(Colors.alpha(c) * opacity.getDouble()));
            Shapes.flatSquare(e.canvas(), x0, z0, x0 + 16, z0 + 16, height.getDouble(), fill, outline.get() ? c : 0, lineWidth.getFloat());
        }
    }
}
