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
