package dev.noir.preview;

import dev.noir.module.Category;
import dev.noir.module.Module;
import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.EnumSetting;

import java.util.List;

/**
 * Stand-in modules for GUI previews and GUI tests. They declare settings shaped like the
 * planned modules but have no behaviour; the real modules (stage 2/3) live in the
 * Minecraft layer because they read game state.
 */
public final class DemoModules {
    private DemoModules() {}

    public enum Rotation {
        SILENT("Silent"), CLIENT("Client"), NONE("None");

        private final String l;

        Rotation(String l) { this.l = l; }

        @Override public String toString() { return l; }
    }

    public enum Priority {
        DISTANCE("Distance"), HEALTH("Health"), ANGLE("Angle"), ARMOR("Armor");

        private final String l;

        Priority(String l) { this.l = l; }

        @Override public String toString() { return l; }
    }

    public enum CritMode {
        JUMP("Jump"), PACKET("Packet");

        private final String l;

        CritMode(String l) { this.l = l; }

        @Override public String toString() { return l; }
    }

    public static final class KillAura extends Module {
        public KillAura() {
            super("KillAura", Category.COMBAT, "Attacks entities around you.");
            number("Range", 3.0, 1.0, 6.0, 0.05, "Attack reach in blocks. Vanilla is 3.0.");
            BoolSetting cooldown = bool("Cooldown Sync", true, "Attack when the weapon's attack cooldown is full instead of on a fixed delay.");
            number("Attack Delay", 10, 0, 20, 1, "Ticks between attacks when Cooldown Sync is off.").visibleWhen(() -> !cooldown.get());
            mode("Rotation", Rotation.SILENT, "Silent: server-side only. Client: also turns your camera. None: only attacks what you look at.");
            number("Rotation Speed", 45, 1, 180, 1, "Maximum degrees turned per tick.").unit("°");
            mode("Priority", Priority.DISTANCE, "Which target to pick first.");
            group("Targets", "Entities to attack.");
            bool("Players", true, "Attack players (never friends).");
            bool("Hostiles", true, "Attack hostile mobs.");
            bool("Passives", false, "Attack passive mobs.");
            group("Checks", "When to attack.");
            BoolSetting walls = bool("Through Walls", false, "Attack without line of sight.");
            number("Wall Range", 2.5, 0, 6, 0.05, "Reach used when the target is behind a block.").showWhen(walls);
            bool("Weapon Only", false, "Only attack while holding a sword or axe.");
        }

        @Override
        public String info() {
            return "Distance";
        }
    }

    public static final class Criticals extends Module {
        public Criticals() {
            super("Criticals", Category.COMBAT, "Makes attacks critical hits.");
            EnumSetting<CritMode> m = mode("Mode", CritMode.JUMP, "Jump: a small real jump before attacking. Packet: fake position packets.");
            m.strictValues("Packet mode sends position packets a vanilla client never would.", CritMode.PACKET);
            bool("Only On Ground", true, "Only try when standing on the ground.");
        }
    }

    public static final class Simple extends Module {
        private final String info;

        public Simple(String name, Category c, String description, String info) {
            super(name, c, description);
            this.info = info;
            bool("Example", true, "Example setting.");
        }

        @Override
        public String info() {
            return info;
        }
    }

    public static List<Module> all() {
        return List.of(
            new KillAura(),
            new Simple("AutoTotem", Category.COMBAT, "Keeps a totem in your offhand.", null),
            new Criticals(),
            new Simple("AutoArmor", Category.COMBAT, "Equips your best armor.", null),
            new Simple("CrystalAura", Category.COMBAT, "Places and breaks end crystals.", null),
            new Simple("Sprint", Category.MOVEMENT, "Sprints automatically.", "Legit"),
            new Simple("NoFall", Category.MOVEMENT, "Prevents fall damage.", null),
            new Simple("ElytraFly", Category.MOVEMENT, "Better elytra control.", null),
            new Simple("Speed", Category.MOVEMENT, "Moves faster.", null),
            new Simple("Step", Category.MOVEMENT, "Steps up full blocks.", null),
            new Simple("Jesus", Category.MOVEMENT, "Walk on liquids.", null),
            new Simple("NoSlow", Category.MOVEMENT, "No slowdown while using items.", null),
            new Simple("AutoWalk", Category.MOVEMENT, "Walks forward for you.", null),
            new Simple("ESP", Category.RENDER, "Highlights entities.", "Outline"),
            new Simple("Tracers", Category.RENDER, "Lines to entities.", null),
            new Simple("Nametags", Category.RENDER, "Better nametags.", null),
            new Simple("Fullbright", Category.RENDER, "Maximum brightness.", "Gamma"),
            new Simple("StorageESP", Category.RENDER, "Highlights containers.", null),
            new Simple("NoRender", Category.RENDER, "Hides fog, overlays and particles.", null),
            new Simple("Freecam", Category.RENDER, "Detached camera.", null),
            new Simple("SusChunk", Category.BASE_HUNTING, "Scores chunks for player activity.", "3 hits"),
            new Simple("NewChunks", Category.BASE_HUNTING, "Marks new and old chunks.", null),
            new Simple("Trails", Category.BASE_HUNTING, "Finds old-chunk trails.", null),
            new Simple("Search", Category.BASE_HUNTING, "Block ESP for a custom list.", "12"),
            new Simple("PortalESP", Category.BASE_HUNTING, "Highlights portals.", null),
            new Simple("StashFinder", Category.BASE_HUNTING, "Finds chunks full of storage.", null),
            new Simple("Waypoints", Category.BASE_HUNTING, "Waypoints for finds.", null));
    }
}
