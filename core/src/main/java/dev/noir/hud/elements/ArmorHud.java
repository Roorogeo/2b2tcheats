package dev.noir.hud.elements;

import dev.noir.gui.Canvas;
import dev.noir.gui.FontRole;
import dev.noir.gui.Theme;
import dev.noir.hud.Anchor;
import dev.noir.hud.HudData;
import dev.noir.hud.HudElement;
import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.NumberSetting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Armor pieces with durability bars. */
public final class ArmorHud extends HudElement {
    private final BoolSetting percent = bool("Percent", true, "Show durability as a percentage under each piece.");
    private final BoolSetting bar = bool("Bar", true, "Show a durability bar under each piece.");
    private final BoolSetting reverse = bool("Feet First", false, "Order boots to helmet instead of helmet to boots.");
    private final NumberSetting warn = number("Warn Below", 20, 0, 100, 1, "Durability % under which the value is drawn in full ink.").unit("%");

    private static final float SLOT = 18;

    public ArmorHud() {
        super("armor", "Armor", "Armor durability.", true, Anchor.BOTTOM_CENTER, 110, 50, false);
    }

    private List<HudData.ArmorPiece> pieces(HudData d) {
        List<HudData.ArmorPiece> list = new ArrayList<>(d.armor());
        if (reverse.get()) Collections.reverse(list);
        return list;
    }

    @Override
    public boolean hasContent(HudData data) {
        for (HudData.ArmorPiece p : data.armor()) if (!p.empty()) return true;
        return false;
    }

    private float cellW(Canvas c) {
        return Math.max(SLOT, c.textWidth("100%", FontRole.CAPTION)) + theme().gap();
    }

    @Override
    public float[] size(Canvas c, HudData data) {
        float h = SLOT + (bar.get() ? 5 : 0) + (percent.get() ? lh(c, FontRole.CAPTION) : 0);
        return new float[] {cellW(c) * Math.max(1, pieces(data).size()) + pad() * 2, h + pad() * 2};
    }

    @Override
    public void render(Canvas c, HudData data, float x, float y) {
        Theme t = theme();
        float[] s = size(c, data);
        cardBg(c, x, y, s[0], s[1]);
        float cw = cellW(c);
        float cx = x + pad();
        for (HudData.ArmorPiece p : pieces(data)) {
            float ix = cx + (cw - t.gap() - SLOT) / 2;
            float cy = y + pad();
            if (p.empty()) {
                c.roundOutline(ix + 1, cy + 1, SLOT - 2, SLOT - 2, t.rChip(), 1, t.divider());
            } else {
                c.item(p.stack(), ix + 1, cy + 1, 16);
                cy += SLOT;
                float dur = p.durability();
                if (bar.get() && p.maxDamage() > 0) {
                    c.roundRect(ix, cy + 1, SLOT, 3, 1.5f, t.edge());
                    c.roundRect(ix, cy + 1, Math.max(1, SLOT * dur), 3, 1.5f, t.ink());
                    cy += 5;
                }
                if (percent.get() && p.maxDamage() > 0) {
                    String pct = Math.round(dur * 100) + "%";
                    int col = dur * 100 < warn.getDouble() ? t.ink() : t.muted();
                    c.text(pct, cx + (cw - t.gap() - c.textWidth(pct, FontRole.CAPTION)) / 2, cy, FontRole.CAPTION, col);
                }
            }
            cx += cw;
        }
    }
}
