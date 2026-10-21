package dev.noir.module.basehunting;

import dev.noir.basehunt.WaypointStore;
import dev.noir.event.Events;
import dev.noir.gui.Canvas;
import dev.noir.gui.FontRole;
import dev.noir.gui.Theme;
import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.ColorSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.module.settings.StringSetting;
import dev.noir.render.CameraInfo;
import dev.noir.render.Shapes;

import java.util.Locale;

/**
 * Local waypoints: created by the other Base Hunting modules (Auto Waypoint) or with
 * {@code .waypoint add}. Drawn as beams with a name/distance label. Stored per server in
 * config/noir/waypoints/.
 */
public final class Waypoints extends BaseHuntModule {
    private final BoolSetting autoCreate = bool("Auto Create", true, "Let other modules create waypoints for their finds.");
    private final StringSetting nameFormat = text("Name Format", "{type} {x} {z}", 48,
        "Name for auto-created waypoints. Tokens: {type} {x} {y} {z} {dim} {n}.");
    private final NumberSetting mergeRadius = number("Merge Radius", 32, 0, 512, 4,
        "Skip an auto waypoint if one of the same type exists within this many blocks.").unit("m");
    private final BoolSetting beam = bool("Beam", true, "Draw a vertical beam at each waypoint.");
    private final NumberSetting beamWidth = number("Beam Width", 0.25, 0.05, 2, 0.05, "Beam thickness in blocks.").showWhen(beam);
    private final NumberSetting beamOpacity = number("Beam Opacity", 0.5, 0.05, 1, 0.05, "Beam opacity.").showWhen(beam);
    private final BoolSetting label = bool("Label", true, "Show the waypoint's name on screen.");
    private final BoolSetting showDistance = bool("Show Distance", true, "Show the distance under the name.").showWhen(label);
    private final NumberSetting maxDistance = number("Max Distance", 10000, 64, 100000, 64, "Waypoints farther than this aren't drawn.").unit("m");
    private final ColorSetting defaultColor = color("Default Color", 0xFFFFFFFF, "Color for waypoints added by command.");

    private int created;

    public Waypoints() {
        super("Waypoints", "Waypoints for anything found, with beams and distance labels.");
        on(Events.RenderWorld3D.class, this::draw);
        on(Events.RenderHud.class, this::labels);
    }

    public WaypointStore store() {
        return hunt().waypoints();
    }

    /** Called by other modules for their finds. */
    public void autoCreate(String type, int x, int y, int z, int color) {
        if (!isEnabled() || !autoCreate.get()) return;
        String dim = hunt().dimension();
        String name = WaypointStore.format(nameFormat.get(), type, dim, x, y, z, ++created);
        store().add(new WaypointStore.Waypoint(name, type, dim, x, y, z, color, core().platform().clock().millis()), mergeRadius.getDouble());
    }

    public boolean addManual(String name, int x, int y, int z) {
        return store().add(new WaypointStore.Waypoint(name, "Manual", hunt().dimension(), x, y, z, defaultColor.argb(),
            core().platform().clock().millis()), 0);
    }

    @Override
    public String info() {
        return Integer.toString(store().in(hunt().dimension()).size());
    }

    private void draw(Events.RenderWorld3D e) {
        if (!beam.get()) return;
        CameraInfo cam = e.canvas().camera();
        for (WaypointStore.Waypoint w : store().in(hunt().dimension())) {
            if (w.distanceTo(cam.x(), cam.y(), cam.z()) > maxDistance.getDouble()) continue;
            int c = dev.noir.util.Colors.withAlpha(w.color(), (int) Math.round(255 * beamOpacity.getDouble()));
            // Beams far away are clamped toward the camera so they stay inside the render range.
            double dist = Math.hypot(w.x() + 0.5 - cam.x(), w.z() + 0.5 - cam.z());
            double scale = dist > 256 ? 256 / dist : 1;
            double bx = cam.x() + (w.x() - cam.x()) * scale, bz = cam.z() + (w.z() - cam.z()) * scale;
            Shapes.beam(e.canvas(), bx, bz, -64, 320, c, beamWidth.getDouble() * Math.max(1, 1 / scale));
        }
    }

    private void labels(Events.RenderHud e) {
        if (!label.get()) return;
        Canvas c = e.canvas();
        Theme t = core().theme();
        CameraInfo cam = e.camera();
        for (WaypointStore.Waypoint w : store().in(hunt().dimension())) {
            double d = w.distanceTo(cam.x(), cam.y(), cam.z());
            if (d > maxDistance.getDouble()) continue;
            double[] p = cam.project(w.x() + 0.5, w.y() + 1.5, w.z() + 0.5);
            if (p == null) continue;
            // Screen pixels -> design pixels.
            float sx = (float) (p[0] * c.width() / cam.screenWidthPx()), sy = (float) (p[1] * c.height() / cam.screenHeightPx());
            String dist = showDistance.get() ? String.format(Locale.ROOT, "%.0fm", d) : null;
            float w1 = c.textWidth(w.name(), FontRole.LABEL), w2 = dist == null ? 0 : c.textWidth(dist, FontRole.CAPTION);
            float bw = Math.max(w1, w2) + t.padC() * 2;
            float bh = c.lineHeight(FontRole.LABEL) + (dist == null ? 0 : c.lineHeight(FontRole.CAPTION)) + 8;
            float bx = sx - bw / 2, by = sy - bh;
            c.roundRect(bx, by, bw, bh, t.rChip(), t.surface());
            c.roundOutline(bx, by, bw, bh, t.rChip(), 1, t.edge());
            c.text(w.name(), sx - w1 / 2, by + 4, FontRole.LABEL, t.ink());
            if (dist != null) c.text(dist, sx - w2 / 2, by + 4 + c.lineHeight(FontRole.LABEL), FontRole.CAPTION, t.muted());
        }
    }
}
