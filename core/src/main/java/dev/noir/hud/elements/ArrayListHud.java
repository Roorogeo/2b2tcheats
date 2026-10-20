package dev.noir.hud.elements;

import dev.noir.gui.Canvas;
import dev.noir.gui.FontRole;
import dev.noir.gui.Theme;
import dev.noir.hud.Anchor;
import dev.noir.hud.HudData;
import dev.noir.hud.HudElement;
import dev.noir.module.Module;
import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.ColorSetting;
import dev.noir.module.settings.EnumSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.util.Animation;
import dev.noir.util.Colors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Enabled modules, with fade/slide in and out. */
public final class ArrayListHud extends HudElement {
    public enum Sort {
        LENGTH("Length"),
        ALPHABETICAL("Alphabetical"),
        CATEGORY("Category");

        private final String label;

        Sort(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    private final EnumSetting<Sort> sort = mode("Sort", Sort.LENGTH, "Order of the list.");
    private final BoolSetting fade = bool("Fade", true, "Fade and slide modules in and out when toggled.");
    private final NumberSetting fadeTime = number("Fade Time", 180, 40, 1000, 10, "Duration of the fade.").unit("ms").showWhen(fade);
    private final BoolSetting info = bool("Show Info", true, "Show each module's info (e.g. its mode) after the name.");
    private final BoolSetting lineBg = bool("Line Background", true, "Draw a surface chip behind each line.");
    private final NumberSetting spacing = number("Line Spacing", 2, 0, 10, 1, "Vertical gap between lines.");
    private final ColorSetting nameColor = color("Name Color", 0xFFFFFFFF, "Module name color.");
    private final ColorSetting infoColor = color("Info Color", 0xFFA3A3A3, "Info text color.");

    private final Map<Module, Animation> anims = new IdentityHashMap<>();

    public ArrayListHud() {
        super("arraylist", "ArrayList", "List of enabled modules.", true, Anchor.TOP_RIGHT, 12, 12, false);
    }

    public void onToggle(Module m, boolean on) {
        if (core == null) return;
        Animation a = anims.computeIfAbsent(m, k -> new Animation(core.platform().clock(), fadeTime.getDouble(),
            () -> fade.get() ? core.client().animationSpeed.getDouble() : 0, on ? 0 : 1));
        a.target(on ? 1 : 0);
    }

    private float visibility(Module m) {
        Animation a = anims.get(m);
        if (a == null) return m.isEnabled() ? 1 : 0;
        return a.value();
    }

    private List<Module> lines() {
        List<Module> out = new ArrayList<>();
        for (Module m : core.modules().all()) {
            if (!m.showInArrayList.get()) continue;
            if (m.isEnabled() || visibility(m) > 0.01f) out.add(m);
        }
        return out;
    }

    private String infoOf(Module m) {
        if (!info.get()) return null;
        String i = m.info();
        return i == null || i.isEmpty() ? null : i;
    }

    private float lineWidth(Canvas c, Module m) {
        float w = c.textWidth(m.name(), FontRole.BODY);
        String i = infoOf(m);
        if (i != null) w += theme().gap() * 2 + c.textWidth(i, FontRole.BODY);
        return w + (lineBg.get() ? theme().padC() * 2 : 0);
    }

    private float lineHeight(Canvas c) {
        return lh(c, FontRole.BODY) + (lineBg.get() ? 6 : 0);
    }

    @Override
    public boolean hasContent(HudData data) {
        return !lines().isEmpty();
    }

    @Override
    public float[] size(Canvas c, HudData data) {
        List<Module> lines = lines();
        float w = 0, h = 0;
        for (Module m : lines) {
            w = Math.max(w, lineWidth(c, m));
            h += (lineHeight(c) + spacing.getFloat()) * visibility(m);
        }
        if (lines.isEmpty()) {
            w = c.textWidth("ArrayList", FontRole.BODY);
            h = lineHeight(c);
        }
        return new float[] {w + pad() * 2, Math.max(h - spacing.getFloat(), lineHeight(c)) + pad() * 2};
    }

    @Override
    public void render(Canvas c, HudData data, float x, float y) {
        Theme t = theme();
        float[] s = size(c, data);
        cardBg(c, x, y, s[0], s[1]);
        List<Module> lines = lines();
        Comparator<Module> order = switch (sort.get()) {
            case LENGTH -> Comparator.comparingDouble((Module m) -> -lineWidth(c, m));
            case ALPHABETICAL -> Comparator.comparing(Module::name, String.CASE_INSENSITIVE_ORDER);
            case CATEGORY -> Comparator.comparing((Module m) -> m.category().ordinal()).thenComparing(Module::name);
        };
        lines.sort(order);
        boolean right = alignRight();
        boolean bottom = anchor.get().bottom();
        if (bottom) java.util.Collections.reverse(lines);
        float lh = lineHeight(c);
        float cy = bottom ? y + s[1] - pad() - lh : y + pad();
        for (Module m : lines) {
            float v = visibility(m);
            float lw = lineWidth(c, m);
            float slide = (1 - v) * 14;
            float lx = right ? x + s[0] - pad() - lw + slide : x + pad() - slide;
            c.pushAlpha(v);
            if (lineBg.get()) {
                c.roundRect(lx, cy, lw, lh, t.rChip(), t.surface());
                c.roundOutline(lx, cy, lw, lh, t.rChip(), 1, t.edge());
            }
            float tx = lx + (lineBg.get() ? t.padC() : 0);
            float ty = cy + (lh - lh(c, FontRole.BODY)) / 2;
            c.text(m.name(), tx, ty, FontRole.BODY, nameColor.argb());
            String i = infoOf(m);
            if (i != null) {
                c.text(i, tx + c.textWidth(m.name(), FontRole.BODY) + t.gap() * 2, ty, FontRole.BODY, Colors.mulAlpha(infoColor.argb(), 1f));
            }
            c.popAlpha();
            float step = (lh + spacing.getFloat()) * v;
            cy += bottom ? -step : step;
        }
    }
}
