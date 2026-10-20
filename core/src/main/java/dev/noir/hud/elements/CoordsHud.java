package dev.noir.hud.elements;

import dev.noir.gui.Canvas;
import dev.noir.gui.FontRole;
import dev.noir.hud.Anchor;
import dev.noir.hud.HudData;
import dev.noir.hud.HudElement;
import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.NumberSetting;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Coordinates plus the matching nether/overworld coordinates. */
public final class CoordsHud extends HudElement {
    private final NumberSetting decimals = number("Decimals", 0, 0, 2, 1, "Decimal places shown.");
    private final BoolSetting other = bool("Other Dimension", true, "Also show the nether coordinates (or overworld while in the nether).");
    private final BoolSetting dimension = bool("Dimension Name", false, "Show the current dimension's name.");

    public CoordsHud() {
        super("coords", "Coordinates", "Your position and the matching nether/overworld position.", true, Anchor.BOTTOM_LEFT, 16, 16, true);
    }

    private String fmt(double v) {
        return String.format(Locale.ROOT, "%." + decimals.getInt() + "f", v);
    }

    private List<String[]> lines(HudData d) {
        List<String[]> out = new ArrayList<>();
        String dim = d.dimension();
        if (dimension.get()) out.add(new String[] {"Dim", pretty(dim)});
        out.add(new String[] {"XYZ", fmt(d.x()) + "  " + fmt(d.y()) + "  " + fmt(d.z())});
        if (other.get()) {
            if (dim.equals("the_nether")) out.add(new String[] {"Overworld", fmt(d.x() * 8) + "  " + fmt(d.z() * 8)});
            else if (dim.equals("overworld")) out.add(new String[] {"Nether", fmt(d.x() / 8) + "  " + fmt(d.z() / 8)});
        }
        return out;
    }

    private static String pretty(String dim) {
        return switch (dim) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> dim;
        };
    }

    private float labelW(Canvas c, List<String[]> lines) {
        float w = 0;
        for (String[] l : lines) w = Math.max(w, c.textWidth(l[0], FontRole.LABEL));
        return w;
    }

    @Override
    public float[] size(Canvas c, HudData data) {
        List<String[]> lines = lines(data);
        float lw = labelW(c, lines), w = 0;
        for (String[] l : lines) w = Math.max(w, lw + theme().padC() + c.textWidth(l[1], FontRole.BODY));
        return new float[] {w + pad() * 2, lines.size() * lh(c, FontRole.BODY) + pad() * 2};
    }

    @Override
    public void render(Canvas c, HudData data, float x, float y) {
        float[] s = size(c, data);
        cardBg(c, x, y, s[0], s[1]);
        List<String[]> lines = lines(data);
        float lw = labelW(c, lines);
        float cy = y + pad();
        for (String[] l : lines) {
            c.text(l[0], x + pad(), cy + 1, FontRole.LABEL, theme().muted());
            c.text(l[1], x + pad() + lw + theme().padC(), cy, FontRole.BODY, theme().ink());
            cy += lh(c, FontRole.BODY);
        }
    }
}
