package dev.noir.gui;

import dev.noir.NoirCore;
import dev.noir.module.Module;
import dev.noir.module.settings.EnumSetting;
import dev.noir.preview.DemoModules;
import dev.noir.preview.Java2DCanvas;
import dev.noir.testutil.FakePlatform;
import dev.noir.util.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClickGuiTest {
    @TempDir
    Path dir;
    FakePlatform platform;
    NoirCore core;
    ClickGui gui;
    Java2DCanvas canvas;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform(dir);
        core = new NoirCore(platform);
        core.register(DemoModules.all().toArray(Module[]::new));
        core.start();
        gui = core.gui();
        canvas = new Java2DCanvas(1920, 1080, 1f);
        gui.open();
        frames(3);
        platform.clock.advance(1000);
        frames(2);
    }

    private void frames(int n) {
        for (int i = 0; i < n; i++) {
            gui.render(canvas);
            platform.clock.advance(16);
        }
    }

    private void click(float x, float y, int button) {
        gui.mouseMoved(x, y);
        frames(1);
        gui.mouseDown(x, y, button);
        frames(1);
        gui.mouseUp(x, y, button);
        frames(1);
        platform.clock.advance(400);
        frames(1);
    }

    private float[] combat() {
        return gui.panelBounds().get("category:COMBAT");
    }

    /** Center of the n-th module row of a category panel. */
    private float[] row(float[] panel, int index) {
        float y = panel[1] + Panel.HEADER + 6 + 4 + index * (CategoryPanel.ROW + CategoryPanel.ROW_GAP) + CategoryPanel.ROW / 2;
        return new float[] {panel[0] + 40, y};
    }

    @Test
    void leftClickTogglesAModule() {
        Module killAura = core.modules().find("KillAura");
        float[] r = row(combat(), 0);
        click(r[0], r[1], 0);
        assertTrue(killAura.isEnabled());
        click(r[0], r[1], 0);
        assertFalse(killAura.isEnabled());
    }

    @Test
    void rightClickExpandsSettings() {
        Module killAura = core.modules().find("KillAura");
        float[] r = row(combat(), 0);
        float before = combat()[3];
        click(r[0], r[1], 1);
        assertTrue(gui.ui().expanded(killAura));
        frames(30);
        assertTrue(combat()[3] > before + 200, "panel should grow with the settings");
    }

    @Test
    void draggingTheHeaderMovesThePanel() {
        float[] p = combat();
        float hx = p[0] + 20, hy = p[1] + 20;
        gui.mouseMoved(hx, hy);
        frames(1);
        gui.mouseDown(hx, hy, 0);
        frames(1);
        gui.mouseMoved(hx + 300, hy + 120);
        frames(2);
        gui.mouseUp(hx + 300, hy + 120, 0);
        frames(1);
        float[] moved = combat();
        assertEquals(p[0] + 300, moved[0], 0.5);
        assertEquals(p[1] + 120, moved[1], 0.5);
    }

    @Test
    void typingFocusesSearch() {
        gui.charTyped('c');
        gui.charTyped('r');
        gui.charTyped('i');
        frames(2);
        assertEquals("cri", gui.searchText());
        gui.keyPressed(Keys.BACKSPACE, 0);
        frames(1);
        assertEquals("cr", gui.searchText());
    }

    @Test
    void escapeClosesAfterTheAnimation() {
        gui.keyPressed(Keys.ESCAPE, 0);
        frames(1);
        assertTrue(gui.isClosing());
        platform.clock.advance(1000);
        frames(1);
        assertTrue(gui.isClosed());
    }

    @Test
    void strictBlockedOptionCannotBeSelected() {
        @SuppressWarnings("unchecked")
        EnumSetting<DemoModules.CritMode> mode = (EnumSetting<DemoModules.CritMode>) core.modules().find("Criticals").setting("mode");
        SettingsView.EnumDropdown<DemoModules.CritMode> dd = new SettingsView.EnumDropdown<>(mode, 900, 400, 120);
        gui.ui().openPopup(dd);
        frames(1);
        float[] b = dd.bounds(canvas);
        float packetY = b[1] + 4 + 26 + 13;
        click(b[0] + 30, packetY, 0);
        assertEquals(DemoModules.CritMode.JUMP, mode.rawValue(), "strict mode must block Packet");

        core.client().strictMode.set(false);
        gui.ui().openPopup(dd);
        frames(1);
        click(b[0] + 30, packetY, 0);
        assertEquals(DemoModules.CritMode.PACKET, mode.rawValue());
    }

    @Test
    void clickOutsideAPopupOnlyClosesIt() {
        Module killAura = core.modules().find("KillAura");
        @SuppressWarnings("unchecked")
        EnumSetting<DemoModules.CritMode> mode = (EnumSetting<DemoModules.CritMode>) core.modules().find("Criticals").setting("mode");
        gui.ui().openPopup(new SettingsView.EnumDropdown<>(mode, 1500, 700, 120));
        frames(1);
        float[] r = row(combat(), 0);
        click(r[0], r[1], 0);
        assertFalse(killAura.isEnabled(), "click should not fall through the popup dismissal");
        assertEquals(null, gui.ui().popup());
    }

    @Test
    void layoutRoundTrips() {
        gui.movePanel("category:RENDER", 777, 333);
        var json = gui.layoutToJson();
        ClickGui other = new ClickGui(core);
        other.layoutFromJson(json);
        other.render(canvas);
        float[] b = other.panelBounds().get("category:RENDER");
        assertEquals(777, b[0], 0.01);
        assertEquals(333, b[1], 0.01);
        assertNotEquals(0, b[2]);
    }
}
