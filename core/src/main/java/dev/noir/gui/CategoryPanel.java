package dev.noir.gui;

import dev.noir.module.Category;
import dev.noir.module.Module;
import dev.noir.util.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One panel per category. Left-click a module row to toggle it, right-click to
 * expand its settings. Enabled rows are white with black labels and a soft halo.
 */
final class CategoryPanel extends Panel {
    static final float ROW = 30;
    static final float ROW_GAP = 6;

    private final ClickGui gui;
    private final Category category;

    CategoryPanel(ClickGui gui, Category category) {
        super("category:" + category.name(), category.toString(), 240, false);
        this.gui = gui;
        this.category = category;
    }

    @Override
    protected String subtitle() {
        List<Module> all = gui.core.modules().byCategory(category);
        int on = 0;
        for (Module m : all) if (m.isEnabled()) on++;
        return on + "/" + all.size();
    }

    List<Module> visibleModules(String query) {
        List<Module> out = new ArrayList<>();
        for (Module m : gui.core.modules().byCategory(category)) {
            if (query == null || m.name().toLowerCase(Locale.ROOT).contains(query) || SettingsView.anyMatch(m, query)) out.add(m);
        }
        return out;
    }

    @Override
    protected float content(Ui ui, float x, float y, float w) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        String query = gui.query();
        List<Module> modules = visibleModules(query);
        float cy = y + 4;
        if (modules.isEmpty()) {
            c.text(query == null ? "No modules" : "No matches", x + t.padC(), cy + 4, FontRole.CAPTION, t.subtle());
            return 28;
        }
        for (int i = 0; i < modules.size(); i++) {
            Module m = modules.get(i);
            boolean nameMatch = query == null || m.name().toLowerCase(Locale.ROOT).contains(query);
            boolean hov = ui.hover(x, cy, w, ROW);
            float on = ui.animate(m, m.isEnabled() ? 1 : 0);
            float r = t.rControl();

            if (hov && on < 1) c.roundRect(x, cy, w, ROW, r, Colors.mulAlpha(t.raised(), 1 - on));
            if (on > 0.01f) {
                c.roundRect(x, cy, w, ROW, r, Colors.mulAlpha(hov ? t.inkHover() : t.ink(), on));
                ui.halo(x, cy, w, ROW, r, on);
            }
            int idle = hov ? t.ink() : t.muted();
            int label = Colors.lerp(idle, t.onInk(), on);
            c.text(m.name(), x + t.padC(), cy + (ROW - c.lineHeight(FontRole.BODY)) / 2, FontRole.BODY, label);

            boolean expanded = ui.expanded(m) || (query != null && !nameMatch);
            String sign = expanded ? "−" : "+";
            int signColor = Colors.lerp(hov ? t.muted() : t.subtle(), t.onInk(), on);
            c.text(sign, x + w - t.padC() - c.textWidth(sign, FontRole.BODY), cy + (ROW - c.lineHeight(FontRole.BODY)) / 2,
                FontRole.BODY, signColor);

            ui.tooltip(m, x, cy, w, ROW, m.description(), "Left-click to toggle · Right-click for settings");
            if (ui.click(x, cy, w, ROW, 0)) m.toggle();
            if (ui.click(x, cy, w, ROW, 1)) ui.toggleExpanded(m);
            cy += ROW;

            float target = expanded ? ui.measured(m, 0) : 0;
            float eh = ui.animate("expand:" + m.name(), target, 160);
            if (expanded || eh > 0.5f) {
                ui.pushClip(x - 4, cy, w + 8, eh);
                float drawn = 6 + SettingsView.render(ui, m, x, cy + 6, w, nameMatch ? null : query);
                if (query == null) drawn += resetRow(ui, m, x, cy + drawn, w);
                ui.measure(m, drawn + 4);
                ui.popClip();
                cy += eh;
            }

            cy += ROW_GAP;
            boolean nextIdle = i + 1 < modules.size() && !modules.get(i + 1).isEnabled();
            if (!m.isEnabled() && nextIdle && eh < 0.5f) {
                c.rect(x + t.padC(), cy - ROW_GAP / 2 - 0.5f, w - t.padC() * 2, 1, t.divider());
            }
        }
        return cy - y;
    }

    private float resetRow(Ui ui, Module m, float x, float y, float w) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        String label = "Reset to defaults";
        float bw = c.textWidth(label, FontRole.LABEL) + t.padC() * 2, bh = 24;
        if (ui.button("reset:" + m.name(), x + w - bw - t.padC(), y + 4, bw, bh, label, false)) {
            gui.core.config().resetModule(m);
        }
        return bh + 10;
    }
}
