package dev.noir.hud.elements;

import dev.noir.gui.Canvas;
import dev.noir.gui.FontRole;
import dev.noir.hud.Anchor;
import dev.noir.hud.HudData;
import dev.noir.hud.HudElement;
import dev.noir.module.settings.BoolSetting;

/** Totems of undying in the inventory. */
public final class TotemHud extends HudElement {
    private final BoolSetting hideZero = bool("Hide When Zero", false, "Hide the counter when you have no totems.");

    public TotemHud() {
        super("totems", "Totems", "Totem of undying count.", true, Anchor.BOTTOM_CENTER, -110, 50, true);
    }

    @Override
    public boolean hasContent(HudData data) {
        return !(hideZero.get() && data.totems() == 0);
    }

    @Override
    public float[] size(Canvas c, HudData data) {
        float w = c.textWidth("Totems", FontRole.LABEL) + theme().gap() * 2 + c.textWidth(Integer.toString(data.totems()), FontRole.BODY);
        return new float[] {w + pad() * 2, lh(c, FontRole.BODY) + pad() * 2};
    }

    @Override
    public void render(Canvas c, HudData data, float x, float y) {
        float[] s = size(c, data);
        cardBg(c, x, y, s[0], s[1]);
        c.text("Totems", x + pad(), y + pad() + 1, FontRole.LABEL, theme().muted());
        c.text(Integer.toString(data.totems()), x + pad() + c.textWidth("Totems", FontRole.LABEL) + theme().gap() * 2, y + pad(),
            FontRole.BODY, data.totems() == 0 ? theme().muted() : theme().ink());
    }
}
