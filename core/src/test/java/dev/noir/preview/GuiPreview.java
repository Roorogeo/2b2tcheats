package dev.noir.preview;

import dev.noir.NoirCore;
import dev.noir.gui.ClickGui;
import dev.noir.hud.HudEditor;
import dev.noir.module.Module;
import dev.noir.module.settings.EnumSetting;
import dev.noir.network.PacketKind;
import dev.noir.network.PacketLog;
import dev.noir.testutil.FakeHudData;
import dev.noir.testutil.FakePlatform;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Renders the real ClickGUI and HUD code through {@link Java2DCanvas} to PNGs.
 * The modules are {@link DemoModules} stand-ins and the packet log / HUD numbers are
 * sample data; the layout, colors, shapes and fonts are exactly what the game draws.
 */
public final class GuiPreview {
    private static final int W = 1920;
    private static final int H = 1080;

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "docs/screenshots");
        Files.createDirectories(out);
        clickGui(out.resolve("clickgui.png"), s -> {});
        clickGui(out.resolve("clickgui-search.png"), s -> s.gui.setSearch("range"));
        clickGui(out.resolve("clickgui-strict-dropdown.png"), s -> {
            Module crits = s.core.modules().find("Criticals");
            s.gui.ui().setExpanded(s.core.modules().find("KillAura"), false);
            s.gui.ui().setExpanded(crits, true);
            s.settle();
            float[] p = s.gui.panelBounds().get("category:COMBAT");
            @SuppressWarnings("unchecked")
            EnumSetting<DemoModules.CritMode> mode = (EnumSetting<DemoModules.CritMode>) crits.setting("mode");
            float chipY = p[1] + 48 + 6 + 4 + 3 * 36 + 6;
            s.click(p[0] + p[2] - 40, chipY + 14, 0);
            float[] pb = s.gui.ui().popup().bounds(s.canvas);
            s.hover(pb[0] + 40, pb[1] + 4 + 26 + 13);
            s.platform.clock.advance(800);
            s.frame();
            if (mode.rawValue() != DemoModules.CritMode.JUMP) throw new IllegalStateException("strict mode let Packet through");
        });
        baseHunting(out.resolve("basehunting.png"));
        clickGui(out.resolve("clickgui-suschunk.png"), s -> {
            s.gui.ui().setExpanded(s.core.modules().find("KillAura"), false);
            s.gui.ui().setExpanded(s.core.modules().find("SusChunk"), true);
        });
        hud(out.resolve("hud.png"), false);
        hud(out.resolve("hud-editor.png"), true);
        System.out.println("Wrote previews to " + out.toAbsolutePath());
    }

    private static final class Scene {
        final FakePlatform platform;
        final NoirCore core;
        final ClickGui gui;
        final Java2DCanvas canvas;

        Scene(Path dir) {
            platform = new FakePlatform(dir);
            core = new NoirCore(platform);
            core.register(DemoModules.all().toArray(Module[]::new));
            core.start();
            gui = core.gui();
            canvas = new Java2DCanvas(W, H, 1f);
        }

        void frame() {
            backdrop(canvas);
            gui.render(canvas);
            platform.clock.advance(16);
        }

        void settle() {
            for (int i = 0; i < 4; i++) frame();
            platform.clock.advance(1000);
            for (int i = 0; i < 3; i++) frame();
        }

        void hover(float x, float y) {
            gui.mouseMoved(x, y);
            frame();
        }

        void click(float x, float y, int button) {
            gui.mouseMoved(x, y);
            frame();
            gui.mouseDown(x, y, button);
            frame();
            gui.mouseUp(x, y, button);
            frame();
            platform.clock.advance(500);
            frame();
        }
    }

    private static void clickGui(Path file, Consumer<Scene> setup) throws Exception {
        Scene s = new Scene(Files.createTempDirectory("noir-preview"));
        for (String name : new String[] {"KillAura", "AutoTotem", "Sprint", "ESP", "Fullbright", "SusChunk", "Search"}) {
            s.core.modules().find(name).setEnabled(true);
        }
        samplePackets(s.core);
        s.gui.open();
        s.gui.ui().setExpanded(s.core.modules().find("KillAura"), true);
        s.gui.setCollapsed("packetlog", false);
        s.settle();
        setup.accept(s);
        s.settle();
        if (file.getFileName().toString().equals("clickgui.png")) {
            float[] mv = s.gui.panelBounds().get("category:MOVEMENT");
            s.hover(mv[0] + 60, mv[1] + 48 + 10 + 36 + 15);
            s.platform.clock.advance(800);
            s.frame();
        }
        ImageIO.write(s.canvas.image(), "png", file.toFile());
    }

    private static void samplePackets(NoirCore core) {
        PacketLog log = core.packets().log();
        long t = 18_420;
        for (int i = 0; i < 6; i++, t++) {
            log.add(new PacketLog.Entry(t, "ServerboundInteractPacket", PacketKind.INTERACT, "KillAura", false, null));
            log.add(new PacketLog.Entry(t, "ServerboundSwingPacket", PacketKind.SWING, "KillAura", false, null));
            if (i % 3 == 0) log.add(new PacketLog.Entry(t, "ServerboundContainerClickPacket", PacketKind.CONTAINER, "AutoTotem", false, null));
            log.add(new PacketLog.Entry(t, "ServerboundMovePlayerPacket$PosRot", PacketKind.MOVEMENT, "vanilla (rotation: KillAura)", false, null));
        }
        log.add(new PacketLog.Entry(t, "ServerboundSwingPacket", PacketKind.SWING, "ESP (direct)", true, "read-only module"));
        log.add(new PacketLog.Entry(t, "ServerboundMovePlayerPacket$Pos", PacketKind.MOVEMENT, "vanilla", false, null));
    }

    private static void hud(Path file, boolean editor) throws Exception {
        Scene s = new Scene(Files.createTempDirectory("noir-preview"));
        for (String name : new String[] {"KillAura", "AutoTotem", "CrystalAura", "Sprint", "ElytraFly", "ESP", "Fullbright", "SusChunk", "Search"}) {
            s.core.modules().find(name).setEnabled(true);
        }
        s.platform.clock.advance(400);
        s.core.modules().find("Freecam").setEnabled(true);
        s.platform.clock.advance(300);
        FakeHudData data = new FakeHudData();
        data.hits.put("SusChunk", 3);
        data.hits.put("Search", 12);
        data.hits.put("StashFinder", 1);
        if (!editor) {
            backdrop(s.canvas);
            s.core.hud().render(s.canvas, data);
        } else {
            HudEditor ed = new HudEditor(s.core);
            ed.select(s.core.hud().find("arraylist"));
            for (int i = 0; i < 3; i++) {
                backdrop(s.canvas);
                float[] b = s.core.hud().bounds(s.core.hud().find("coords"), s.canvas, data);
                ed.mouseMoved(b[0] + 10, b[1] + 10);
                ed.render(s.canvas, data);
            }
        }
        ImageIO.write(s.canvas.image(), "png", new File(file.toString()));
    }

    /**
     * The real Base Hunting modules over ~300 fake chunks (a stash, a base, a portal, a trail of
     * old chunks through new terrain, and Search finds), drawn through the WorldCanvas path.
     */
    private static void baseHunting(Path file) throws Exception {
        FakePlatform platform = new FakePlatform(Files.createTempDirectory("noir-preview"));
        NoirCore core = new NoirCore(platform, Runnable::run);
        core.register(DemoModules.all().toArray(Module[]::new));
        core.start();
        java.util.Map<Long, dev.noir.testutil.FakeChunk> loaded = new java.util.HashMap<>();
        core.baseHunt().setSource(new dev.noir.basehunt.ChunkSource() {
            @Override public String dimension() { return "overworld"; }
            @Override public java.util.List<dev.noir.basehunt.ChunkView> loaded() { return new java.util.ArrayList<>(loaded.values()); }
            @Override public dev.noir.basehunt.ChunkView get(int x, int z) { return loaded.get(((long) x << 32) ^ (z & 0xFFFFFFFFL)); }
        });
        core.events().post(new dev.noir.event.Events.WorldChange(true));
        for (String m : new String[] {"Waypoints", "SusChunk", "NewChunks", "Trails", "Search", "PortalESP", "StashFinder"}) {
            core.modules().find(m).setEnabled(true);
        }
        core.modules().find("NewChunks").setting("renderheight").parse("63");
        core.modules().find("Trails").setting("renderheight").parse("63.2");
        core.modules().find("SusChunk").setting("renderheight").parse("63.4");
        core.modules().find("Search").setting("tracers").parse("on");
        core.modules().find("Search").setting("maxdistance").parse("400");

        for (int cx = -9; cx <= 9; cx++) {
            for (int cz = -2; cz <= 14; cz++) {
                dev.noir.testutil.FakeChunk c = dev.noir.testutil.FakeChunk.overworld(cx, cz);
                boolean trail = Math.abs(cx - (cz / 2 - 3)) <= 0 && cz >= 0;
                if (trail) c.flowing(4, 62, 4);
                if (cx == 4 && cz == 3) {
                    c.fill(2, 63, 2, 13, 63, 2, "chest").fill(2, 63, 5, 9, 63, 5, "barrel");
                    for (int i = 0; i < 20; i++) c.blockEntity("minecraft:chest");
                }
                if (cx == -5 && cz == 8) {
                    c.fill(3, 63, 3, 8, 66, 3, "obsidian").set(5, 63, 7, "white_bed").set(6, 63, 7, "crafting_table")
                        .set(7, 63, 7, "furnace").fill(2, 63, 10, 5, 63, 10, "chest");
                    for (int i = 0; i < 6; i++) c.blockEntity("minecraft:chest");
                }
                if (cx == 2 && cz == 9) c.fill(5, 63, 6, 6, 65, 6, "nether_portal");
                if ((cx * 7 + cz * 3) % 23 == 0) c.set(8, 40, 8, "spawner");
                loaded.put(((long) cx << 32) ^ (cz & 0xFFFFFFFFL), c);
                core.events().post(new dev.noir.event.Events.ChunkLoad(cx, cz, c, c));
                if (!trail) {
                    core.events().post(new dev.noir.event.Events.BlockChange("overworld", cx * 16 + 3, 62, cz * 16 + 3,
                        "minecraft:air", "minecraft:water", true));
                }
            }
        }
        for (int i = 0; i < 45; i++) {
            core.events().post(new dev.noir.event.Events.TickPost(i));
            platform.clock.advance(50);
        }

        dev.noir.render.CameraInfo cam = new dev.noir.render.CameraInfo(8, 150, -70, 0, 38, 70, W, H);
        Java2DCanvas canvas = new Java2DCanvas(W, H, 1f);
        Graphics2D g = canvas.graphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0x5E7380), 0, H, new Color(0x9AA9A0)));
        g.fillRect(0, 0, W, H);
        Java2DWorldCanvas world = new Java2DWorldCanvas(cam);
        for (int cx = -9; cx <= 9; cx++) {
            for (int cz = -2; cz <= 14; cz++) {
                double x0 = cx * 16, z0 = cz * 16;
                int ground = ((cx + cz) & 1) == 0 ? 0xFF3E4A3A : 0xFF43503F;
                world.quad(x0, 62.9, z0, x0 + 16, 62.9, z0, x0 + 16, 62.9, z0 + 16, x0, 62.9, z0 + 16, ground);
            }
        }
        core.events().post(new dev.noir.event.Events.RenderWorld3D(world, 0));
        world.paint(g);

        FakeHudData data = new FakeHudData();
        data.camera = cam;
        data.hits = core.hud().counters();
        data.x = 8;
        data.y = 150;
        data.z = -70;
        core.hud().render(canvas, data);
        core.events().post(new dev.noir.event.Events.RenderHud(canvas, 0, cam));
        ImageIO.write(canvas.image(), "png", file.toFile());
        core.shutdown();
    }

    /** A neutral stand-in for the game world behind the GUI. */
    private static void backdrop(Java2DCanvas c) {
        Graphics2D g = c.graphics();
        g.setClip(null);
        g.setPaint(new GradientPaint(0, 0, new Color(0x4A5A63), W, H, new Color(0x2C3B2E)));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(0x334032));
        for (int i = 0; i < 14; i++) g.fillRect(i * 160 - 40, H - 260 + (i % 3) * 30, 150, 260);
    }
}
