package dev.noir.hud.elements;

import dev.noir.NoirCore;
import dev.noir.gui.Canvas;
import dev.noir.gui.FontRole;
import dev.noir.hud.Anchor;
import dev.noir.hud.HudData;
import dev.noir.hud.HudElement;
import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.ColorSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.module.settings.StringSetting;

public final class WatermarkHud extends HudElement {
    private final StringSetting label = text("Text", "NOIR", 24, "Watermark text.");
    private final NumberSetting tracking = number("Letter Spacing", 3, 0, 12, 0.5, "Extra space between letters.");
    private final BoolSetting version = bool("Show Version", false, "Show the client version under the watermark.");
    private final ColorSetting color = color("Color", 0xFFFFFFFF, "Watermark color.");

    public WatermarkHud() {
        super("watermark", "Watermark", "The NOIR wordmark.", true, Anchor.TOP_LEFT, 16, 14, false);
    }

    private float textW(Canvas c) {
        String s = label.get();
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += c.textWidth(String.valueOf(s.charAt(i)), FontRole.DISPLAY);
        return w + tracking.getFloat() * Math.max(0, s.length() - 1);
    }

    @Override
    public float[] size(Canvas c, HudData data) {
        float w = textW(c);
        float h = lh(c, FontRole.DISPLAY);
        if (version.get()) {
            w = Math.max(w, c.textWidth("v" + NoirCore.VERSION, FontRole.CAPTION));
            h += lh(c, FontRole.CAPTION);
        }
        return new float[] {w + pad() * 2, h + pad() * 2};
    }

    @Override
    public void render(Canvas c, HudData data, float x, float y) {
        float[] s = size(c, data);
        cardBg(c, x, y, s[0], s[1]);
        float cx = x + pad();
        String t = label.get();
        for (int i = 0; i < t.length(); i++) {
            String ch = String.valueOf(t.charAt(i));
            c.text(ch, cx, y + pad(), FontRole.DISPLAY, color.argb());
            cx += c.textWidth(ch, FontRole.DISPLAY) + tracking.getFloat();
        }
        if (version.get()) {
            c.text("v" + NoirCore.VERSION, x + pad(), y + pad() + lh(c, FontRole.DISPLAY), FontRole.CAPTION, theme().muted());
        }
    }
}
