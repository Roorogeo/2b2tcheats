package dev.noir.hud.elements;

import dev.noir.gui.Canvas;
import dev.noir.gui.FontRole;
import dev.noir.hud.Anchor;
import dev.noir.hud.HudData;
import dev.noir.hud.HudElement;
import dev.noir.module.settings.BoolSetting;

import java.util.Map;

/** Hit counts published by Base Hunting modules (SusChunk, Search, ...). */
public final class HitCounterHud extends HudElement {
    private final BoolSetting hideEmpty = bool("Hide When Empty", true, "Hide when no module reports any hits.");
    private final BoolSetting hideZero = bool("Hide Zero Counters", false, "Hide counters that are zero.");

    public HitCounterHud() {
        super("hits", "Hit Counter", "Hits found by SusChunk, Search and other finders.", true, Anchor.MIDDLE_LEFT, 16, 0, true);
    }

    private Map<String, Integer> counters(HudData d) {
        Map<String, Integer> m = new java.util.LinkedHashMap<>(d.hitCounters());
        if (hideZero.get()) m.values().removeIf(v -> v == 0);
        return m;
    }

    @Override
    public boolean hasContent(HudData data) {
        return !(hideEmpty.get() && counters(data).isEmpty());
    }

    @Override
    public float[] size(Canvas c, HudData data) {
        Map<String, Integer> m = counters(data);
        float w = c.textWidth("No hits", FontRole.CAPTION);
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            w = Math.max(w, c.textWidth(e.getKey(), FontRole.LABEL) + theme().padP() + c.textWidth(e.getValue().toString(), FontRole.BODY));
        }
        return new float[] {w + pad() * 2, Math.max(1, m.size()) * lh(c, FontRole.BODY) + pad() * 2};
    }

    @Override
    public void render(Canvas c, HudData data, float x, float y) {
        float[] s = size(c, data);
        cardBg(c, x, y, s[0], s[1]);
        Map<String, Integer> m = counters(data);
        float cy = y + pad();
        if (m.isEmpty()) c.text("No hits", x + pad(), cy, FontRole.CAPTION, theme().subtle());
        for (Map.Entry<String, Integer> e : m.entrySet()) {
            c.text(e.getKey(), x + pad(), cy + 1, FontRole.LABEL, theme().muted());
            String v = e.getValue().toString();
            c.text(v, x + s[0] - pad() - c.textWidth(v, FontRole.BODY), cy, FontRole.BODY, theme().ink());
            cy += lh(c, FontRole.BODY);
        }
    }
}
