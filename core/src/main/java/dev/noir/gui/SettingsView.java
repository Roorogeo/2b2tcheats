package dev.noir.gui;

import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.ColorSetting;
import dev.noir.module.settings.EnumSetting;
import dev.noir.module.settings.IdListSetting;
import dev.noir.module.settings.KeybindSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.module.settings.Setting;
import dev.noir.module.settings.SettingGroup;
import dev.noir.module.settings.SettingsHolder;
import dev.noir.module.settings.StringSetting;
import dev.noir.network.StrictMode;
import dev.noir.util.Colors;
import dev.noir.util.Keys;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

/** Renders and edits the settings of any {@link SettingsHolder}. */
public final class SettingsView {
    private SettingsView() {}

    public static final float ROW = 28;
    private static final float GROUP_ROW = 26;
    private static final Map<Object, float[]> HSV = new IdentityHashMap<>();

    /**
     * Draws the visible settings and returns the height used.
     *
     * @param query lowercase search text; when non-null only matching settings are shown
     */
    public static float render(Ui ui, SettingsHolder holder, float x, float y, float w, String query) {
        float cy = y;
        for (SettingGroup g : holder.groups()) {
            if (!g.isVisible()) continue;
            List<Setting<?, ?>> visible = new ArrayList<>();
            for (Setting<?, ?> s : g.settings()) {
                if (s.isVisible() && matches(s, query)) visible.add(s);
            }
            if (visible.isEmpty()) continue;
            boolean collapsed = !g.isRoot() && g.isCollapsed() && query == null;
            if (!g.isRoot()) cy += groupHeader(ui, g, x, cy, w, collapsed);
            if (collapsed) continue;
            for (Setting<?, ?> s : visible) cy += row(ui, s, x, cy, w);
        }
        return cy - y;
    }

    public static boolean matches(Setting<?, ?> s, String query) {
        return query == null || s.name().toLowerCase(Locale.ROOT).contains(query);
    }

    public static boolean anyMatch(SettingsHolder h, String query) {
        if (query == null) return true;
        for (Setting<?, ?> s : h.settings()) if (s.isVisible() && matches(s, query)) return true;
        return false;
    }

    private static float groupHeader(Ui ui, SettingGroup g, float x, float y, float w, boolean collapsed) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        boolean hov = ui.hover(x, y, w, GROUP_ROW);
        float pad = t.padC();
        float ty = y + (GROUP_ROW - c.lineHeight(FontRole.CAPTION)) / 2 + 2;
        c.rect(x + pad, y + 2, w - pad * 2, 1, t.divider());
        c.text(g.name().toUpperCase(Locale.ROOT), x + pad, ty, FontRole.LABEL, hov ? t.ink() : t.muted());
        String sign = collapsed ? "+" : "−";
        c.text(sign, x + w - pad - c.textWidth(sign, FontRole.LABEL), ty, FontRole.LABEL, hov ? t.ink() : t.muted());
        if (ui.click(x, y, w, GROUP_ROW, 0)) g.setCollapsed(!collapsed);
        ui.tooltip(g, x, y, w, GROUP_ROW, g.description(), null);
        return GROUP_ROW;
    }

    private static String strictExtra(Setting<?, ?> s) {
        if (!s.hasStrictRule()) return null;
        return StrictMode.enabled() ? "Strict mode: " + s.strictReason() : "Strict mode is off; this option is allowed.";
    }

    /** Draws a row's label, reset-on-right-click and tooltip. Returns label color. */
    private static void label(Ui ui, Setting<?, ?> s, float x, float y, float w, float h, boolean greyed) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        boolean hov = ui.hover(x, y, w, h);
        if (hov) c.roundRect(x, y, w, h, t.rControl(), Colors.mulAlpha(t.raised(), 0.9f));
        int color = greyed ? t.subtle() : hov ? t.ink() : t.muted();
        c.text(s.name(), x + t.padC(), y + (ROW - c.lineHeight(FontRole.LABEL)) / 2, FontRole.LABEL, color);
        String extra = strictExtra(s);
        if (!s.isDefault()) extra = (extra == null ? "" : extra + "\n") + "Right-click to reset (default " + defaultDisplay(s) + ").";
        ui.tooltip(s, x, y, w, h, s.description(), extra);
        if (ui.click(x, y, w * 0.55f, ROW, 1)) s.reset();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String defaultDisplay(Setting s) {
        String d = s.displayValue(s.defaultValue());
        return d.isEmpty() ? "empty" : d;
    }

    private static float row(Ui ui, Setting<?, ?> s, float x, float y, float w) {
        if (s instanceof BoolSetting b) return bool(ui, b, x, y, w);
        if (s instanceof NumberSetting n) return number(ui, n, x, y, w);
        if (s instanceof EnumSetting<?> e) return enumRow(ui, e, x, y, w);
        if (s instanceof ColorSetting col) return color(ui, col, x, y, w);
        if (s instanceof KeybindSetting k) return keybind(ui, k, x, y, w);
        if (s instanceof StringSetting str) return string(ui, str, x, y, w);
        if (s instanceof IdListSetting<?> l) return idList(ui, l, x, y, w);
        return 0;
    }

    // ---- bool -------------------------------------------------------------------------------

    private static float bool(Ui ui, BoolSetting s, float x, float y, float w) {
        boolean cannotEnable = s.isStrictBlocked(true);
        label(ui, s, x, y, w, ROW, cannotEnable);
        float tx = x + w - ui.t.padC() - 28;
        if (ui.toggle(s, tx, y + (ROW - 16) / 2, s.get(), cannotEnable && !s.get())) {
            boolean next = !s.rawValue();
            if (!(next && cannotEnable)) s.set(next);
        }
        if (!cannotEnable && ui.click(x, y, w, ROW, 0)) s.toggle();
        return ROW;
    }

    // ---- number -----------------------------------------------------------------------------

    private static float number(Ui ui, NumberSetting s, float x, float y, float w) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        float h = 42;
        boolean greyed = s.isCurrentlyBlockedRaw();
        label(ui, s, x, y, w, h, greyed);
        String v = s.display();
        c.text(v, x + w - t.padC() - c.textWidth(v, FontRole.BODY), y + (ROW - c.lineHeight(FontRole.BODY)) / 2,
            FontRole.BODY, greyed ? t.subtle() : t.ink());

        float trackX = x + t.padC(), trackW = w - t.padC() * 2, trackY = y + 28, trackH = 6;
        boolean hov = ui.hover(trackX - 6, trackY - 8, trackW + 12, trackH + 16);
        if (ui.click(trackX - 6, trackY - 8, trackW + 12, trackH + 16, 0)) ui.beginDrag(s);
        boolean drag = ui.dragging(s);
        if (drag) {
            double f = (ui.mouseX() - trackX) / trackW;
            double candidate = s.valueAtFraction(f);
            Double snapped = s.preview(Double.toString(Math.max(s.min(), Math.min(s.max(), candidate))));
            if (snapped != null && !s.isStrictBlocked(snapped)) s.set(snapped);
        }
        float scroll = ui.takeScroll(x, y, w, h);
        if (scroll != 0) {
            double next = s.rawValue() + Math.signum(scroll) * s.step();
            if (!s.isStrictBlocked(next)) s.set(next);
        }
        float f = (float) s.fraction();
        float k = ui.animate(s.key() + "@" + System.identityHashCode(s), f, 80);
        c.roundOutline(trackX, trackY, trackW, trackH, trackH / 2, 1, hov || drag ? t.ink() : t.edgeStrong());
        if (k > 0) c.roundRect(trackX, trackY, Math.max(trackH, trackW * k), trackH, trackH / 2, greyed ? t.muted() : t.ink());
        float knob = 12;
        float kx = trackX + trackW * k - knob / 2, ky = trackY + trackH / 2 - knob / 2;
        c.roundRect(kx, ky, knob, knob, knob / 2, hov || drag ? t.inkHover() : t.ink());
        ui.halo(kx, ky, knob, knob, knob / 2, hov || drag ? 1 : 0.6f);
        return h;
    }

    // ---- enum -------------------------------------------------------------------------------

    private static <E extends Enum<E>> float enumRow(Ui ui, EnumSetting<E> s, float x, float y, float w) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        boolean greyed = s.isCurrentlyBlockedRaw();
        label(ui, s, x, y, w, ROW, false);
        String v = s.get().toString();
        float cw = c.textWidth(v, FontRole.BODY) + t.padC() * 2;
        float ch = 20;
        float cx = x + w - t.padC() - cw, cy = y + (ROW - ch) / 2;
        boolean hov = ui.hover(cx, cy, cw, ch);
        c.roundRect(cx, cy, cw, ch, t.rChip(), hov ? t.raised() : t.surface());
        c.roundOutline(cx, cy, cw, ch, t.rChip(), 1, hov ? t.edgeStrong() : t.edge());
        c.text(v, cx + t.padC(), cy + (ch - c.lineHeight(FontRole.BODY)) / 2, FontRole.BODY, greyed ? t.muted() : t.ink());
        if (ui.click(x, y, w, ROW, 0)) ui.openPopup(new EnumDropdown<>(s, cx, cy + ch + 4, cw));
        float scroll = ui.takeScroll(x, y, w, ROW);
        if (scroll != 0) s.cycle(scroll > 0 ? -1 : 1);
        return ROW;
    }

    /** Dropdown listing an enum's options; Strict-blocked options are greyed with the reason. */
    static final class EnumDropdown<E extends Enum<E>> implements Ui.Popup {
        private final EnumSetting<E> setting;
        private final float x;
        private final float y;
        private final float minW;
        private static final float OPT = 26;

        EnumDropdown(EnumSetting<E> setting, float x, float y, float minW) {
            this.setting = setting;
            this.x = x;
            this.y = y;
            this.minW = minW;
        }

        @Override
        public float[] bounds(Canvas c) {
            float w = minW;
            for (E e : setting.options()) w = Math.max(w, c.textWidth(e.toString(), FontRole.BODY) + 40);
            float h = setting.options().size() * OPT + 8;
            float px = Math.min(x + minW - w, c.width() - w - 8);
            float py = y + h > c.height() - 8 ? y - h - 32 : y;
            return new float[] {Math.max(8, px), py, w, h};
        }

        @Override
        public void render(Ui ui) {
            Canvas c = ui.canvas();
            Theme t = ui.t;
            float[] b = bounds(c);
            c.roundRect(b[0], b[1], b[2], b[3], t.rControl(), t.raised());
            c.roundOutline(b[0], b[1], b[2], b[3], t.rControl(), 1, t.edge());
            float oy = b[1] + 4;
            for (E e : setting.options()) {
                boolean blocked = setting.isOptionBlocked(e);
                boolean selected = setting.rawValue() == e;
                boolean hov = ui.hover(b[0] + 4, oy, b[2] - 8, OPT);
                if (hov && !blocked) c.roundRect(b[0] + 4, oy, b[2] - 8, OPT, t.rChip(), Colors.mulAlpha(t.ink(), 0.08f));
                int col = blocked ? Colors.mulAlpha(t.muted(), 0.45f) : selected || hov ? t.ink() : t.muted();
                if (selected) c.roundRect(b[0] + 12, oy + OPT / 2 - 3, 6, 6, 3, col);
                c.text(e.toString(), b[0] + 26, oy + (OPT - c.lineHeight(FontRole.BODY)) / 2, FontRole.BODY, col);
                if (blocked) ui.tooltip(e, b[0], oy, b[2], OPT, "Unavailable in Strict mode.", setting.strictReason());
                if (ui.click(b[0], oy, b[2], OPT, 0) && !blocked) {
                    setting.set(e);
                    ui.closePopup();
                }
                oy += OPT;
            }
        }
    }

    // ---- color ------------------------------------------------------------------------------

    private static float color(Ui ui, ColorSetting s, float x, float y, float w) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        label(ui, s, x, y, w, ROW, false);
        float sw = 28, sh = 16;
        float sx = x + w - t.padC() - sw, sy = y + (ROW - sh) / 2;
        swatch(ui, sx, sy, sw, sh, s.rawValue());
        String hex = Colors.toHex(s.rawValue());
        c.text(hex, sx - t.gap() * 2 - c.textWidth(hex, FontRole.CAPTION), y + (ROW - c.lineHeight(FontRole.CAPTION)) / 2,
            FontRole.CAPTION, t.muted());
        if (ui.click(x, y, w, ROW, 0)) ui.toggleExpanded(s);
        float h = ROW;
        if (ui.expanded(s)) h += colorEditor(ui, s, x, y + ROW, w, s.rawValue(), v -> s.set(v));
        return h;
    }

    static void swatch(Ui ui, float x, float y, float w, float h, int argb) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        float r = Math.min(ui.t.rChip(), h / 2);
        if (Colors.alpha(argb) < 255) {
            ui.pushClip(x, y, w, h);
            float cell = 4;
            for (float yy = 0; yy < h; yy += cell) {
                for (float xx = 0; xx < w; xx += cell) {
                    boolean dark = ((int) (xx / cell) + (int) (yy / cell)) % 2 == 0;
                    c.rect(x + xx, y + yy, cell, cell, dark ? 0xFF3A3A3A : 0xFF9A9A9A);
                }
            }
            ui.popClip();
        }
        c.roundRect(x, y, w, h, r, argb);
        c.roundOutline(x, y, w, h, r, 1, t.edgeStrong());
    }

    /** HSV + alpha sliders and a hex field. Returns height. */
    static float colorEditor(Ui ui, Object id, float x, float y, float w, int argb, IntConsumer set) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        float[] hsv = HSV.computeIfAbsent(id, k -> new float[4]);
        if (hsv[3] == 0 || (int) hsv[3] != argb) {
            float[] fresh = Colors.toHsv(argb);
            // Keep the hue when the color is grey so dragging saturation back up doesn't jump to red.
            if (fresh[1] > 0.001f && fresh[2] > 0.001f) hsv[0] = fresh[0];
            hsv[1] = fresh[1];
            hsv[2] = fresh[2];
        }
        int alpha = Colors.alpha(argb);
        float px = x + t.padC(), pw = w - t.padC() * 2;
        float cy = y + 2;
        float hue = gradient(ui, id + "#h", px, cy, pw, hsv[0], f -> Colors.fromHsv(f, 1, 1, 255));
        cy += 22;
        float sat = gradient(ui, id + "#s", px, cy, pw, hsv[1], f -> Colors.fromHsv(hsv[0], f, Math.max(hsv[2], 0.2f), 255));
        cy += 22;
        float val = gradient(ui, id + "#v", px, cy, pw, hsv[2], f -> Colors.fromHsv(hsv[0], hsv[1], f, 255));
        cy += 22;
        int opaque = Colors.fromHsv(hsv[0], hsv[1], hsv[2], 255);
        float al = gradient(ui, id + "#a", px, cy, pw, alpha / 255f, f -> Colors.withAlpha(opaque, Math.round(f * 255)));
        cy += 24;
        if (hue != hsv[0] || sat != hsv[1] || val != hsv[2] || Math.round(al * 255) != alpha) {
            hsv[0] = hue;
            hsv[1] = sat;
            hsv[2] = val;
            int next = Colors.fromHsv(hue, sat, val, Math.round(al * 255));
            hsv[3] = next;
            set.accept(next);
        } else {
            hsv[3] = argb;
        }
        String hexId = id + "#hex";
        String typed = ui.textField(hexId, px, cy, pw, 26, Colors.toHex(argb), "#AARRGGBB", 9);
        if (ui.focused(hexId)) {
            Integer parsed = Colors.parse(typed);
            if (parsed != null && parsed != argb) set.accept(parsed);
        }
        cy += 32;
        c.rect(px, cy - 3, pw, 1, t.divider());
        return cy - y;
    }

    private interface FloatToColor {
        int at(float f);
    }

    /** A thin track painted as a gradient with a knob; returns the (possibly dragged) value. */
    private static float gradient(Ui ui, Object id, float x, float y, float w, float value, FloatToColor colorAt) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        float h = 10;
        float ty = y + 4;
        ui.pushClip(x, ty, w, h);
        int steps = 24;
        float segW = w / steps;
        for (int i = 0; i < steps; i++) {
            c.rect(x + i * segW, ty, segW + 1, h, colorAt.at((i + 0.5f) / steps));
        }
        ui.popClip();
        c.roundOutline(x, ty, w, h, h / 2, 1, t.edgeStrong());
        if (ui.click(x - 4, y, w + 8, 18, 0)) ui.beginDrag(id);
        float v = value;
        if (ui.dragging(id)) v = Math.max(0f, Math.min(1f, (ui.mouseX() - x) / w));
        float kx = x + w * v - 5;
        c.roundRect(kx, ty - 1, 10, h + 2, 5, t.ink());
        c.roundOutline(kx, ty - 1, 10, h + 2, 5, 1, t.onInk());
        return v;
    }

    // ---- keybind ----------------------------------------------------------------------------

    private static float keybind(Ui ui, KeybindSetting s, float x, float y, float w) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        label(ui, s, x, y, w, ROW, false);
        boolean listening = ui.listening(s);
        String v = listening ? "Press a key…" : Keys.display(s.rawValue());
        float cw = c.textWidth(v, FontRole.LABEL) + t.padC() * 2, ch = 20;
        float cx = x + w - t.padC() - cw, cy = y + (ROW - ch) / 2;
        if (listening) {
            c.roundRect(cx, cy, cw, ch, t.rChip(), t.ink());
            ui.halo(cx, cy, cw, ch, t.rChip(), 1);
            c.text(v, cx + t.padC(), cy + (ch - c.lineHeight(FontRole.LABEL)) / 2, FontRole.LABEL, t.onInk());
            Ui.KeyPress k = ui.takeAnyKey();
            if (k != null) {
                if (k.key == Keys.ESCAPE) ui.stopListening();
                else {
                    s.set(k.key == Keys.BACKSPACE || k.key == Keys.DELETE ? Keys.NONE : k.key);
                    ui.stopListening();
                }
            } else {
                for (int b = 2; b < 8; b++) {
                    if (ui.pressedAnywhere(b)) {
                        s.set(Keys.mouse(b));
                        ui.consume(b);
                        ui.stopListening();
                        break;
                    }
                }
                if (ui.listening(s) && ui.pressedAnywhere(0) && !ui.inside(x, y, w, ROW)) ui.stopListening();
            }
        } else {
            boolean hov = ui.hover(cx, cy, cw, ch);
            c.roundRect(cx, cy, cw, ch, t.rChip(), hov ? t.raised() : t.surface());
            c.roundOutline(cx, cy, cw, ch, t.rChip(), 1, hov ? t.edgeStrong() : t.edge());
            c.text(v, cx + t.padC(), cy + (ch - c.lineHeight(FontRole.LABEL)) / 2, FontRole.LABEL,
                s.isBound() ? t.ink() : t.muted());
            if (ui.click(x, y, w, ROW, 0)) ui.listen(s);
        }
        return ROW;
    }

    // ---- string -----------------------------------------------------------------------------

    private static float string(Ui ui, StringSetting s, float x, float y, float w) {
        label(ui, s, x, y, w, ROW, false);
        float pad = ui.t.padC();
        String typed = ui.textField(s, x + pad, y + ROW - 2, w - pad * 2, 28, s.rawValue(), s.name(), s.maxLength());
        if (ui.focused(s) && !typed.equals(s.rawValue())) s.set(typed);
        return ROW + 34;
    }

    // ---- id lists ---------------------------------------------------------------------------

    private static float idList(Ui ui, IdListSetting<?> s, float x, float y, float w) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        label(ui, s, x, y, w, ROW, false);
        String count = s.size() + (s.size() == 1 ? " entry" : " entries");
        boolean open = ui.expanded(s);
        String v = count + (open ? "  −" : "  +");
        c.text(v, x + w - t.padC() - c.textWidth(v, FontRole.CAPTION), y + (ROW - c.lineHeight(FontRole.CAPTION)) / 2,
            FontRole.CAPTION, t.muted());
        if (ui.click(x, y, w, ROW, 0)) ui.toggleExpanded(s);
        if (!open) return ROW;

        float cy = y + ROW;
        float pad = t.padC();
        String remove = null;
        for (Map.Entry<String, Integer> e : s.rawValue().entrySet()) {
            String entryId = e.getKey();
            Object key = s.key() + "/" + entryId + "@" + System.identityHashCode(s);
            float eh = 24;
            boolean hov = ui.hover(x + pad, cy, w - pad * 2, eh);
            if (hov) c.roundRect(x + pad, cy, w - pad * 2, eh, t.rChip(), Colors.mulAlpha(t.raised(), 0.9f));
            swatch(ui, x + pad + 4, cy + 6, 12, 12, e.getValue());
            String shown = entryId.startsWith("minecraft:") ? entryId.substring(10) : entryId;
            c.text(c.ellipsize(shown, FontRole.BODY, w - pad * 2 - 56), x + pad + 22, cy + (eh - c.lineHeight(FontRole.BODY)) / 2,
                FontRole.BODY, hov ? t.ink() : t.muted());
            float xw = c.textWidth("×", FontRole.BODY);
            float xx = x + w - pad - 8 - xw;
            boolean xh = ui.hover(xx - 4, cy, xw + 8, eh);
            c.text("×", xx, cy + (eh - c.lineHeight(FontRole.BODY)) / 2, FontRole.BODY, xh ? t.ink() : t.subtle());
            if (ui.click(xx - 4, cy, xw + 8, eh, 0)) remove = entryId;
            else if (ui.click(x + pad, cy, w - pad * 2, eh, 0)) ui.toggleExpanded(key);
            ui.tooltip(key, x + pad, cy, w - pad * 2 - xw - 12, eh, entryId, "Click to edit its color.");
            cy += eh;
            if (ui.expanded(key)) {
                final String eid = entryId;
                cy += colorEditor(ui, key, x, cy, w, e.getValue(), col -> s.put(eid, col));
            }
        }
        if (remove != null) s.remove(remove);
        Object addId = s.key() + "#add@" + System.identityHashCode(s);
        String typed = ui.textField(addId, x + pad, cy + 4, w - pad * 2, 26, "", "Add id, Enter", 64);
        if (ui.submitted(addId)) {
            if (!typed.isBlank() && s.add(typed.trim())) ui.clearText(addId);
        }
        return cy + 36 - y;
    }
}
