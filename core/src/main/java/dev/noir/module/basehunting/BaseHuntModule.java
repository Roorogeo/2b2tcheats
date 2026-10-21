package dev.noir.module.basehunting;

import dev.noir.basehunt.BaseHunt;
import dev.noir.basehunt.ChunkKey;
import dev.noir.event.Events;
import dev.noir.module.Category;
import dev.noir.module.Module;
import dev.noir.module.settings.BoolSetting;

import java.util.HashSet;
import java.util.Set;

/**
 * Base for Base Hunting modules: read-only (they never request actions), share the
 * scan cache, and alert/log/waypoint their finds locally.
 */
public abstract class BaseHuntModule extends Module {
    /** Local notification options for a module's finds. */
    public record Alerts(BoolSetting chat, BoolSetting toast, BoolSetting log, BoolSetting waypoint) {}

    private final Set<Object> alerted = new HashSet<>();

    protected BaseHuntModule(String name, String description) {
        super(name, Category.BASE_HUNTING, description);
        on(Events.WorldChange.class, e -> alerted.clear());
    }

    protected final BaseHunt hunt() {
        return core().baseHunt();
    }

    protected final Alerts alerts(String what, boolean chatByDefault, boolean waypointByDefault) {
        group("Alerts", "Local notifications for " + what + ". Nothing is sent to the server.");
        return new Alerts(
            bool("Chat Alert", chatByDefault, "Print a local chat line for each new " + what + "."),
            bool("Toast Alert", false, "Show a toast for each new " + what + "."),
            bool("Log To File", true, "Append each new " + what + " to config/noir/logs/" + name().toLowerCase() + ".log."),
            bool("Auto Waypoint", waypointByDefault, "Create a waypoint for each new " + what + " (needs the Waypoints module)."));
    }

    /** Alerts, logs and waypoints a find once per session (keyed by {@code id}). */
    protected final void reportOnce(Object id, Alerts a, String type, int x, int y, int z, int color, String details) {
        if (!alerted.add(id)) return;
        String where = x + ", " + y + ", " + z;
        hunt().alert(name(), type + " at " + where + (details.isEmpty() ? "" : " (" + details + ")"), a.chat().get(), a.toast().get());
        if (a.log().get()) hunt().logFind(name(), x, y, z, type + (details.isEmpty() ? "" : " " + details));
        if (a.waypoint().get()) {
            Waypoints w = core().modules().get(Waypoints.class);
            if (w != null) w.autoCreate(type, x, y, z, color);
        }
    }

    protected final void forgetAlerts() {
        alerted.clear();
    }

    protected final boolean inRange(ChunkKey key, double camX, double camZ, int chunks) {
        double r = chunks * 16.0;
        return key.distanceSq(camX, camZ) <= r * r;
    }
}
