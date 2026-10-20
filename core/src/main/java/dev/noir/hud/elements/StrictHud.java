package dev.noir.hud.elements;

import dev.noir.gui.Canvas;
import dev.noir.gui.FontRole;
import dev.noir.gui.Theme;
import dev.noir.hud.Anchor;
import dev.noir.hud.HudData;
import dev.noir.hud.HudElement;
import dev.noir.module.settings.BoolSetting;
import dev.noir.network.StrictMode;

/** Shows whether Strict mode is on: a white chip when on, an outlined one when off. */
public final class StrictHud extends HudElement {
    private final BoolSetting showWhenOn = bool("Show When On", true, "Show the indicator while Strict mode is on.");
    private final BoolSetting showWhenOff = bool("Show When Off", true, "Show the indicator while Strict mode is off.");

    public StrictHud() {
        super("strict", "Strict Indicator", "Strict mode status.", true, Anchor.TOP_LEFT, 16, 48, false);
    }

    private String label() {
        return StrictMode.enabled() ? "STRICT" : "STRICT OFF";
    }

    @Override
    public boolean hasContent(HudData data) {
        return StrictMode.enabled() ? showWhenOn.get() : showWhenOff.get();
    }

    @Override
    public float[] size(Canvas c, HudData data) {
        return new float[] {c.textWidth(label(), FontRole.LABEL) + theme().padC() * 2 + pad() * 2, 20 + pad() * 2};
    }

    @Override
    public void render(Canvas c, HudData data, float x, float y) {
        Theme t = theme();
        float[] s = size(c, data);
        cardBg(c, x, y, s[0], s[1]);
        float cx = x + pad(), cy = y + pad(), w = s[0] - pad() * 2, h = 20;
        boolean on = StrictMode.enabled();
        if (on) {
            c.roundRect(cx, cy, w, h, t.rChip(), t.ink());
            c.roundOutline(cx - 3, cy - 3, w + 6, h + 6, t.rChip() + 3, 1, t.ring());
        } else {
            c.roundRect(cx, cy, w, h, t.rChip(), t.surface());
            c.roundOutline(cx, cy, w, h, t.rChip(), 1, t.edgeStrong());
        }
        c.text(label(), cx + t.padC(), cy + (h - lh(c, FontRole.LABEL)) / 2, FontRole.LABEL, on ? t.onInk() : t.ink());
    }
}
