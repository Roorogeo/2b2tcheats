package dev.noir.hud;

import dev.noir.NoirCore;
import dev.noir.gui.Canvas;
import dev.noir.gui.FontRole;
import dev.noir.gui.SettingsView;
import dev.noir.gui.Theme;
import dev.noir.gui.Ui;
import dev.noir.util.Colors;
import dev.noir.util.Keys;

import java.util.ArrayList;
import java.util.List;

/**
 * Drag HUD elements to move them (they re-anchor to the nearest screen region on drop),
 * right-click to toggle, scroll to scale, click to edit settings in the side panel.
 */
public final class HudEditor {
    private static final Object ELEMENTS = "noir:hud-elements";
    private static final Object PANEL = "noir:hud-panel";
    private static final Object HINT = "noir:hud-hint";
    private static final float PANEL_W = 280;

    private final NoirCore core;
    private final Ui ui;
    private HudElement selected;
    private HudElement dragging;
    private float dragDx;
    private float dragDy;
    private float panelScroll;
    private float panelContentH = 200;
    private boolean closeRequested;

    public HudEditor(NoirCore core) {
        this.core = core;
        this.ui = new Ui(core);
    }

    public Ui ui() { return ui; }

    public boolean closeRequested() { return closeRequested; }

    public void open() {
        closeRequested = false;
        dragging = null;
    }

    public HudElement selected() { return selected; }

    public void select(HudElement e) { this.selected = e; }

    public void render(Canvas c, HudData data) {
        HudManager hud = core.hud();
        Theme t = core.theme();
        float sw = c.width(), sh = c.height();
        List<Object> order = new ArrayList<>(List.of(ELEMENTS, HINT));
        if (selected != null) order.add(PANEL);
        if (ui.popup() != null) order.add(Ui.POPUP_LAYER);
        ui.beginFrame(c, order);
        ui.dismissPopupOnOutsideClick();

        c.rect(0, 0, sw, sh, Colors.mulAlpha(Colors.withAlpha(t.bg(), 255), 0.35f));
        c.rect(sw / 2 - 0.5f, 0, 1, sh, t.divider());
        c.rect(0, sh / 2 - 0.5f, sw, 1, t.divider());

        // Elements
        ui.layer(ELEMENTS);
        ui.layerBounds(0, 0, sw, sh);
        HudElement hovered = null;
        List<float[]> bounds = new ArrayList<>();
        for (HudElement e : hud.elements()) {
            float[] b = hud.bounds(e, c, data);
            bounds.add(b);
            if (ui.hover(b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4)) hovered = e;
        }
        if (dragging != null) hovered = dragging;
        for (int i = 0; i < hud.elements().size(); i++) {
            HudElement e = hud.elements().get(i);
            float[] b = bounds.get(i);
            boolean on = e.enabled.get();
            c.pushAlpha(on ? 1f : 0.35f);
            if (e.hasContent(data)) hud.draw(e, c, data);
            else {
                c.roundRect(b[0], b[1], b[2], b[3], t.rChip(), Colors.mulAlpha(t.surface(), 0.8f));
                c.text(e.name(), b[0] + 6, b[1] + (b[3] - c.lineHeight(FontRole.CAPTION)) / 2, FontRole.CAPTION, t.muted());
            }
            c.popAlpha();
            boolean sel = e == selected, hov = e == hovered;
            if (sel || hov) {
                c.roundOutline(b[0] - 3, b[1] - 3, b[2] + 6, b[3] + 6, t.rChip() + 2, 1, sel ? t.ink() : t.edgeStrong());
                String tag = e.name() + (on ? "" : " (off)") + "  " + Math.round(e.scale.getDouble() * 100) + "%";
                float ty = b[1] - 22 < 4 ? b[1] + b[3] + 6 : b[1] - 22;
                c.text(tag, b[0], ty, FontRole.CAPTION, sel ? t.ink() : t.muted());
            } else {
                c.roundOutline(b[0] - 3, b[1] - 3, b[2] + 6, b[3] + 6, t.rChip() + 2, 1, t.divider());
            }
        }
        if (hovered != null) {
            float[] b = hud.bounds(hovered, c, data);
            if (ui.click(b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, 0)) {
                selected = hovered;
                dragging = hovered;
                dragDx = ui.mouseX() - b[0];
                dragDy = ui.mouseY() - b[1];
                ui.beginDrag(hovered);
                panelScroll = 0;
            }
            if (ui.click(b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, 1)) hovered.enabled.toggle();
            float s = ui.takeScroll(b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4);
            if (s != 0) hovered.scale.set(hovered.scale.getDouble() + Math.signum(s) * 0.05);
        } else if (ui.click(0, 0, sw, sh, 0)) {
            selected = null;
        }
        if (dragging != null) {
            if (ui.dragging(dragging)) {
                float[] b = hud.bounds(dragging, c, data);
                float nx = Math.round((ui.mouseX() - dragDx) / 2) * 2f;
                float ny = Math.round((ui.mouseY() - dragDy) / 2) * 2f;
                nx = Math.max(0, Math.min(sw - b[2], nx));
                ny = Math.max(0, Math.min(sh - b[3], ny));
                Anchor a = dragging.anchor.get();
                dragging.offsetX.set((double) Anchor.offsetFor(a.fx, sw, b[2], nx));
                dragging.offsetY.set((double) Anchor.offsetFor(a.fy, sh, b[3], ny));
            } else {
                reanchor(dragging, c, data);
                dragging = null;
            }
        }

        // Hint
        ui.layer(HINT);
        String hint = "Drag to move  ·  Right-click to toggle  ·  Scroll to scale  ·  Esc to close";
        float hw = c.textWidth(hint, FontRole.CAPTION) + t.padP() * 2;
        float hx = (sw - hw) / 2, hy = 16;
        c.roundRect(hx, hy, hw, 30, t.rControl(), t.surface());
        c.roundOutline(hx, hy, hw, 30, t.rControl(), 1, t.edge());
        c.text(hint, hx + t.padP(), hy + (30 - c.lineHeight(FontRole.CAPTION)) / 2, FontRole.CAPTION, t.muted());
        ui.layerBounds(hx, hy, hw, 30);

        // Settings panel for the selection
        if (selected != null) drawPanel(c, data, sw, sh);

        ui.drawPopup();
        ui.drawTooltip();
        if (ui.takeKey(Keys.ESCAPE) != null) {
            if (selected != null) selected = null;
            else closeRequested = true;
        }
        ui.endFrame();
    }

    private void drawPanel(Canvas c, HudData data, float sw, float sh) {
        Theme t = core.theme();
        ui.layer(PANEL);
        float[] b = core.hud().bounds(selected, c, data);
        float px = b[0] + b[2] / 2 < sw / 2 ? sw - PANEL_W - 24 : 24;
        float py = 64;
        float maxH = sh - py - 24;
        float headerH = 44;
        float h = Math.min(maxH, headerH + panelContentH + 8);
        c.roundRect(px, py, PANEL_W, h, t.rPanel(), t.surface());
        c.roundOutline(px, py, PANEL_W, h, t.rPanel(), 1, t.edge());
        c.text(selected.name(), px + t.padP(), py + (headerH - c.lineHeight(FontRole.HEADING)) / 2, FontRole.HEADING, t.ink());
        c.rect(px + 1, py + headerH - 1, PANEL_W - 2, 1, t.divider());
        float bodyH = h - headerH - 8;
        float s = ui.takeScroll(px, py + headerH, PANEL_W, bodyH);
        if (s != 0) panelScroll -= s * 36;
        panelScroll = Math.max(0, Math.min(Math.max(0, panelContentH - bodyH), panelScroll));
        ui.pushClip(px + 1, py + headerH, PANEL_W - 2, bodyH + 6);
        float inset = t.padP() / 2;
        panelContentH = SettingsView.render(ui, selected, px + inset, py + headerH + 6 - panelScroll, PANEL_W - inset * 2, null) + 12;
        ui.popClip();
        ui.layerBounds(px, py, PANEL_W, h);
    }

    /** Re-anchors to the screen third the element's center is in, keeping it where it is. */
    void reanchor(HudElement e, Canvas c, HudData data) {
        HudManager hud = core.hud();
        float[] b = hud.bounds(e, c, data);
        Anchor a = Anchor.nearest(b[0] + b[2] / 2, b[1] + b[3] / 2, c.width(), c.height());
        e.anchor.set(a);
        e.offsetX.set((double) Math.round(Anchor.offsetFor(a.fx, c.width(), b[2], b[0])));
        e.offsetY.set((double) Math.round(Anchor.offsetFor(a.fy, c.height(), b[3], b[1])));
    }

    public void mouseMoved(float x, float y) { ui.mouseMoved(x, y); }

    public void mouseDown(float x, float y, int button) {
        ui.mouseMoved(x, y);
        ui.mouseDown(button);
    }

    public void mouseUp(float x, float y, int button) {
        ui.mouseMoved(x, y);
        ui.mouseUp(button);
    }

    public void scroll(float amount) { ui.scroll(amount); }

    public void keyPressed(int key, int mods) { ui.keyPressed(key, mods); }

    public void charTyped(int codepoint) { ui.charTyped(codepoint); }
}
