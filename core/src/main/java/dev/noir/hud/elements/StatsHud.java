package dev.noir.hud.elements;

import dev.noir.gui.Canvas;
import dev.noir.gui.FontRole;
import dev.noir.hud.Anchor;
import dev.noir.hud.HudData;
import dev.noir.hud.HudElement;
import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.EnumSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** FPS, ping and TPS. */
public final class StatsHud extends HudElement {
    public enum Layout {
        HORIZONTAL("Horizontal"),
        VERTICAL("Vertical");

        private final String label;

        Layout(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    private final BoolSetting fps = bool("FPS", true, "Show frames per second.");
    private final BoolSetting ping = bool("Ping", true, "Show your latency as reported by the server.");
    private final BoolSetting tps = bool("TPS", true, "Show estimated server ticks per second (from world time updates).");
    private final EnumSetting<Layout> layout = mode("Layout", Layout.HORIZONTAL, "One row or one stat per line.");

    public StatsHud() {
        super("stats", "Stats", "FPS, ping and TPS.", true, Anchor.BOTTOM_LEFT, 16, 76, true);
    }

    private List<String[]> items(HudData d) {
        List<String[]> out = new ArrayList<>();
        if (fps.get()) out.add(new String[] {"FPS", Integer.toString(d.fps())});
        if (ping.get()) out.add(new String[] {"Ping", d.ping() < 0 ? "–" : d.ping() + "ms"});
        if (tps.get()) out.add(new String[] {"TPS", d.tps() < 0 ? "–" : String.format(Locale.ROOT, "%.1f", d.tps())});
        return out;
    }

    private float itemW(Canvas c, String[] i) {
        return c.textWidth(i[0], FontRole.LABEL) + theme().gap() + c.textWidth(i[1], FontRole.BODY);
    }

    @Override
    public boolean hasContent(HudData data) {
        return !items(data).isEmpty();
    }

    @Override
    public float[] size(Canvas c, HudData data) {
        List<String[]> items = items(data);
        float w = 0, h;
        if (layout.get() == Layout.HORIZONTAL) {
            for (int i = 0; i < items.size(); i++) w += itemW(c, items.get(i)) + (i > 0 ? theme().padP() : 0);
            h = lh(c, FontRole.BODY);
        } else {
            for (String[] i : items) w = Math.max(w, itemW(c, i));
            h = items.size() * lh(c, FontRole.BODY);
        }
        return new float[] {w + pad() * 2, h + pad() * 2};
    }

    @Override
    public void render(Canvas c, HudData data, float x, float y) {
        float[] s = size(c, data);
        cardBg(c, x, y, s[0], s[1]);
        float cx = x + pad(), cy = y + pad();
        for (String[] i : items(data)) {
            c.text(i[0], cx, cy + 1, FontRole.LABEL, theme().muted());
            c.text(i[1], cx + c.textWidth(i[0], FontRole.LABEL) + theme().gap(), cy, FontRole.BODY, theme().ink());
            if (layout.get() == Layout.HORIZONTAL) cx += itemW(c, i) + theme().padP();
            else cy += lh(c, FontRole.BODY);
        }
    }
}
