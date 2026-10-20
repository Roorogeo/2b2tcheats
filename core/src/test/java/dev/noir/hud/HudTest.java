package dev.noir.hud;

import dev.noir.NoirCore;
import dev.noir.module.Module;
import dev.noir.preview.DemoModules;
import dev.noir.preview.Java2DCanvas;
import dev.noir.testutil.FakeHudData;
import dev.noir.testutil.FakePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudTest {
    @TempDir
    Path dir;
    FakePlatform platform;
    NoirCore core;
    Java2DCanvas canvas;
    FakeHudData data;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform(dir);
        core = new NoirCore(platform);
        core.register(DemoModules.all().toArray(Module[]::new));
        core.start();
        canvas = new Java2DCanvas(1920, 1080, 1f);
        data = new FakeHudData();
    }

    @Test
    void anchorPlacementIsInvertible() {
        for (Anchor a : Anchor.values()) {
            float x = Anchor.place(a.fx, 1920, 200, 37);
            assertEquals(37, Anchor.offsetFor(a.fx, 1920, 200, x), 1e-3);
        }
        assertEquals(1920 - 200 - 12, Anchor.place(1, 1920, 200, 12));
        assertEquals(Anchor.BOTTOM_RIGHT, Anchor.nearest(1800, 1000, 1920, 1080));
        assertEquals(Anchor.CENTER, Anchor.nearest(960, 540, 1920, 1080));
    }

    @Test
    void rightAnchoredElementHugsTheRightEdge() {
        HudElement list = core.hud().find("arraylist");
        core.modules().find("Sprint").setEnabled(true);
        float[] b = core.hud().bounds(list, canvas, data);
        assertEquals(1920 - 12, b[0] + b[2], 0.5);
        assertEquals(12, b[1], 0.5);
    }

    @Test
    void scaleScalesBounds() {
        HudElement wm = core.hud().find("watermark");
        float[] one = core.hud().bounds(wm, canvas, data);
        wm.setting("scale").parse("2");
        float[] two = core.hud().bounds(wm, canvas, data);
        assertEquals(one[2] * 2, two[2], 0.5);
    }

    @Test
    void arrayListOnlyHasContentWithEnabledModules() {
        HudElement list = core.hud().find("arraylist");
        assertFalse(list.hasContent(data));
        core.modules().find("ESP").setEnabled(true);
        assertTrue(list.hasContent(data));
        core.modules().find("ESP").showInArrayList.set(false);
        platform.clock.advance(2000);
        assertFalse(list.hasContent(data));
    }

    @Test
    void editorDragReanchors() {
        HudEditor editor = new HudEditor(core);
        HudElement wm = core.hud().find("watermark");
        float[] b = core.hud().bounds(wm, canvas, data);
        float gx = b[0] + 5, gy = b[1] + 5;
        editor.mouseMoved(gx, gy);
        editor.render(canvas, data);
        editor.mouseDown(gx, gy, 0);
        editor.render(canvas, data);
        editor.mouseMoved(1700, 1000);
        editor.render(canvas, data);
        editor.mouseUp(1700, 1000, 0);
        editor.render(canvas, data);
        editor.render(canvas, data);
        assertEquals(Anchor.BOTTOM_RIGHT, wm.anchor.get());
        float[] after = core.hud().bounds(wm, canvas, data);
        assertEquals(1700 - 5, after[0], 3);
        assertEquals(wm, editor.selected());
    }

    @Test
    void editorRightClickToggles() {
        HudEditor editor = new HudEditor(core);
        HudElement coords = core.hud().find("coords");
        float[] b = core.hud().bounds(coords, canvas, data);
        editor.mouseMoved(b[0] + 4, b[1] + 4);
        editor.render(canvas, data);
        editor.mouseDown(b[0] + 4, b[1] + 4, 1);
        editor.render(canvas, data);
        editor.mouseUp(b[0] + 4, b[1] + 4, 1);
        editor.render(canvas, data);
        assertFalse(coords.enabled.get());
    }

    @Test
    void hudRendersWithoutErrors() {
        core.modules().find("KillAura").setEnabled(true);
        data.hits.put("SusChunk", 3);
        data.dimension = "the_nether";
        core.hud().render(canvas, data);
        assertTrue(platform.errors.isEmpty(), platform.errors.toString());
    }
}
