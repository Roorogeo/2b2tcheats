package dev.noir.gui;

import dev.noir.util.Colors;

/** A draggable, collapsible, scrollable panel. Right-click the header to collapse. */
public abstract class Panel {
    public static final float HEADER = 48;

    final String id;
    final String title;
    float x;
    float y;
    final float w;
    boolean collapsed;
    private float scroll;
    private float dragDx;
    private float dragDy;
    private float lastContentH = 120;
    private float lastH = HEADER;

    protected Panel(String id, String title, float w, boolean collapsed) {
        this.id = id;
        this.title = title;
        this.w = w;
        this.collapsed = collapsed;
    }

    public String id() { return id; }

    /** Draws the panel body and returns its height. */
    protected abstract float content(Ui ui, float x, float y, float w);

    /** Small caption shown at the right of the header. */
    protected String subtitle() { return null; }

    float height() { return lastH; }

    void render(Ui ui, float screenW, float screenH) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        float pad = t.padP();

        boolean overHeader = ui.hover(x, y, w, HEADER);
        if (ui.click(x, y, w, HEADER, 0)) {
            ui.beginDrag(this);
            dragDx = ui.mouseX() - x;
            dragDy = ui.mouseY() - y;
        }
        if (ui.dragging(this)) {
            x = Math.max(-w + 48, Math.min(screenW - 48, ui.mouseX() - dragDx));
            y = Math.max(0, Math.min(screenH - HEADER, ui.mouseY() - dragDy));
        }
        if (ui.click(x, y, w, HEADER, 1)) collapsed = !collapsed;

        float open = ui.animate(id + ":open", collapsed ? 0 : 1);
        float maxBody = Math.max(80, screenH - y - HEADER - pad - 8);
        float bodyH = Math.min(lastContentH, maxBody) * open;
        float h = HEADER + (bodyH > 0.5f ? bodyH + pad / 2 : 0);
        lastH = h;

        c.roundRect(x, y, w, h, t.rPanel(), t.surface());
        c.roundOutline(x, y, w, h, t.rPanel(), 1, overHeader || ui.dragging(this) ? t.edgeStrong() : t.edge());
        c.text(title, x + pad, y + (HEADER - c.lineHeight(FontRole.HEADING)) / 2, FontRole.HEADING, t.ink());
        String sub = subtitle();
        if (sub != null) {
            c.text(sub, x + w - pad - c.textWidth(sub, FontRole.CAPTION), y + (HEADER - c.lineHeight(FontRole.CAPTION)) / 2,
                FontRole.CAPTION, t.subtle());
        }

        if (bodyH > 0.5f) {
            c.rect(x + 1, y + HEADER - 1, w - 2, 1, Colors.mulAlpha(t.divider(), open));
            float bodyY = y + HEADER;
            float maxScroll = Math.max(0, lastContentH - maxBody);
            float s = ui.takeScroll(x, bodyY, w, bodyH);
            if (s != 0) scroll -= s * 36;
            scroll = Math.max(0, Math.min(maxScroll, scroll));
            ui.pushClip(x + 1, bodyY, w - 2, bodyH + pad / 2 - 2);
            float inset = pad / 2;
            lastContentH = content(ui, x + inset, bodyY + 6 - scroll, w - inset * 2) + 12;
            ui.popClip();
            if (maxScroll > 0) {
                float trackH = bodyH - 12;
                float thumbH = Math.max(24, trackH * maxBody / lastContentH);
                float thumbY = bodyY + 6 + (trackH - thumbH) * (scroll / maxScroll);
                c.roundRect(x + w - 5, thumbY, 2, thumbH, 1, t.edgeStrong());
            }
        } else if (!collapsed) {
            // First frames of opening: still measure content so the animation knows its target.
            ui.pushClip(x, y, 0, 0);
            lastContentH = content(ui, x, y + HEADER, w - t.padP()) + 12;
            ui.popClip();
        }
        ui.layerBounds(x, y, w, h);
    }
}
