package dev.noir.hud.elements;

import dev.noir.gui.Canvas;
import dev.noir.gui.FontRole;
import dev.noir.gui.Theme;
import dev.noir.hud.Anchor;
import dev.noir.hud.HudData;
import dev.noir.hud.HudElement;
import dev.noir.module.Module;
import dev.noir.module.settings.NumberSetting;
import dev.noir.util.Animation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Short toasts when a module is toggled. */
public final class ToastHud extends HudElement {
    private final NumberSetting duration = number("Duration", 1600, 400, 6000, 100, "How long each toast stays.").unit("ms");
    private final NumberSetting maxToasts = number("Max Toasts", 4, 1, 8, 1, "Maximum toasts shown at once.");

    private static final float W = 200;
    private static final float H = 44;
    private static final float GAP = 8;

    private record Toast(String title, String body, boolean on, long start, Animation anim) {}

    private final Deque<Toast> toasts = new ArrayDeque<>();

    public ToastHud() {
        super("toasts", "Toasts", "A toast when a module is toggled.", true, Anchor.BOTTOM_RIGHT, 16, 16, true);
    }

    public void onToggle(Module m, boolean on) {
        push(m.name(), on ? "Enabled" : "Disabled", on);
    }

    /** Shows a toast. {@code filled} draws the white dot (on) instead of the outlined one. */
    public void push(String title, String body, boolean filled) {
        if (core == null || !enabled.get()) return;
        Animation a = new Animation(core.platform().clock(), 140, core.client().animationSpeed::getDouble, 0);
        a.target(1);
        toasts.removeIf(t -> t.title.equals(title) && t.body.equals(body));
        toasts.addFirst(new Toast(title, body, filled, core.platform().clock().millis(), a));
        while (toasts.size() > maxToasts.getInt()) toasts.removeLast();
    }

    private List<Toast> live() {
        long now = core.platform().clock().millis();
        toasts.removeIf(t -> now - t.start > duration.getDouble() + 200);
        List<Toast> out = new ArrayList<>(toasts);
        for (Toast t : out) if (now - t.start > duration.getDouble()) t.anim.target(0);
        return out;
    }

    @Override
    public boolean hasContent(HudData data) {
        return !live().isEmpty();
    }

    @Override
    public float[] size(Canvas c, HudData data) {
        int n = Math.max(1, live().size());
        return new float[] {W, n * H + (n - 1) * GAP};
    }

    @Override
    public void render(Canvas c, HudData data, float x, float y) {
        Theme t = theme();
        List<Toast> list = live();
        boolean bottom = anchor.get().bottom();
        float[] s = size(c, data);
        float cy = bottom ? y + s[1] - H : y;
        for (Toast toast : list) {
            float v = toast.anim.value();
            c.pushAlpha(v);
            float tx = x + (1 - v) * (alignRight() ? 16 : -16);
            if (card.get()) {
                c.roundRect(tx, cy, W, H, t.rControl(), t.surface());
                c.roundOutline(tx, cy, W, H, t.rControl(), 1, t.edge());
            }
            float dot = 10;
            float dx = tx + t.padC() + 4, dy = cy + (H - dot) / 2;
            if (toast.on) {
                c.roundRect(dx, dy, dot, dot, dot / 2, t.ink());
                c.roundOutline(dx - 3, dy - 3, dot + 6, dot + 6, dot / 2 + 3, 1, t.ring());
            } else {
                c.roundOutline(dx, dy, dot, dot, dot / 2, 1, t.edgeStrong());
            }
            float textX = dx + dot + t.padC() + 4;
            c.text(c.ellipsize(toast.title, FontRole.BODY, W - (textX - tx) - t.padC()), textX, cy + 6, FontRole.BODY, t.ink());
            c.text(c.ellipsize(toast.body, FontRole.CAPTION, W - (textX - tx) - t.padC()), textX, cy + 6 + lh(c, FontRole.BODY),
                FontRole.CAPTION, t.muted());
            c.popAlpha();
            float step = (H + GAP) * v;
            cy += bottom ? -step : step;
        }
    }
}
