package dev.noir.gui;

import com.google.gson.JsonObject;
import dev.noir.NoirCore;
import dev.noir.module.Category;
import dev.noir.util.Animation;
import dev.noir.util.Colors;
import dev.noir.util.Keys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The ClickGUI: category panels, Client/Theme panels, the Packet Log, and a search bar
 * that filters modules and settings. Opens/closes with a short fade + scale.
 * Platform screens forward input and call {@link #render} every frame.
 */
public final class ClickGui {
    private static final Object SEARCH = "noir:search";
    static final float SEARCH_W = 380;
    static final float SEARCH_H = 36;

    final NoirCore core;
    private final Ui ui;
    private final List<Panel> panels = new ArrayList<>();
    private final Map<String, float[]> savedLayout = new HashMap<>();
    private final Map<String, Boolean> pendingCollapse = new HashMap<>();
    private final Animation openAnim;
    private String search = "";
    private boolean closing;
    private boolean built;

    public ClickGui(NoirCore core) {
        this.core = core;
        this.ui = new Ui(core);
        this.openAnim = new Animation(core.platform().clock(), 160, core.client().animationSpeed::getDouble, 0);
    }

    public Ui ui() {
        return ui;
    }

    /** Lowercased search text, or null when empty. */
    String query() {
        String q = search.trim().toLowerCase(Locale.ROOT);
        return q.isEmpty() ? null : q;
    }

    public String searchText() {
        return search;
    }

    public void setSearch(String text) {
        this.search = text;
    }

    public void open() {
        closing = false;
        openAnim.snap(0);
        openAnim.target(1);
    }

    public void requestClose() {
        if (closing) return;
        closing = true;
        ui.setFocus(null);
        ui.stopListening();
        ui.closePopup();
        openAnim.target(0);
    }

    /** True once the close animation finished; the screen should close. */
    public boolean isClosed() {
        return closing && openAnim.done();
    }

    public boolean isClosing() {
        return closing;
    }

    private void build(float screenW) {
        built = true;
        for (Category c : Category.values()) panels.add(new CategoryPanel(this, c));
        panels.add(new HolderPanel(this, "client", "Client", core.client(), false, (u, r) -> {
            float bw = r[2] - u.t.padC() * 2;
            if (u.button("client:hud", r[0] + u.t.padC(), r[1] + 8, bw, 28, "Edit HUD", true)) core.platform().openHudEditor();
            return 44f;
        }));
        panels.add(new HolderPanel(this, "theme", "Theme", core.theme(), true, (u, r) -> {
            float bw = r[2] - u.t.padC() * 2;
            if (u.button("theme:reset", r[0] + u.t.padC(), r[1] + 8, bw, 28, "Reset to Noir", false)) core.theme().resetToNoir();
            return 44f;
        }));
        panels.add(new PacketLogPanel(this));

        // Default layout: one row; panels that don't fit stack under the last placed panel
        // (the Theme and Packet Log panels start collapsed, so they stack as headers).
        float gap = core.theme().gapP();
        float top = 24 + SEARCH_H + gap;
        float x = gap;
        Panel last = null;
        for (Panel p : panels) {
            if (x + p.w > screenW - gap && last != null) {
                p.x = Math.min(last.x, screenW - p.w - gap);
                p.y = last.y + Panel.HEADER + gap;
            } else {
                p.x = x;
                p.y = top;
                x += p.w + gap;
            }
            last = p;
            float[] saved = savedLayout.get(p.id());
            if (saved != null) {
                p.x = saved[0];
                p.y = saved[1];
                if (saved[2] >= 0) p.collapsed = saved[2] != 0;
            }
            Boolean c = pendingCollapse.remove(p.id());
            if (c != null) p.collapsed = c;
        }
    }

    public void render(Canvas c) {
        if (!built) build(c.width());
        float w = c.width(), h = c.height();
        List<Object> order = new ArrayList<>(panels);
        order.add(SEARCH);
        if (ui.popup() != null) order.add(Ui.POPUP_LAYER);
        ui.beginFrame(c, order);

        Theme t = core.theme();
        float a = openAnim.value();
        c.rect(0, 0, w, h, Colors.mulAlpha(Colors.withAlpha(t.bg(), 255), core.client().backdropOpacity.getFloat() * a));
        c.pushAlpha(a);
        c.pushScale(0.97f + 0.03f * a, w / 2, h / 2);

        ui.dismissPopupOnOutsideClick();
        if (ui.focus() == null && !ui.isListening() && ui.hasTyped() && !closing) ui.setFocus(SEARCH);

        Panel raise = null;
        String q = query();
        for (Panel p : panels) {
            if (p instanceof HolderPanel hp && !hp.visibleFor(q)) continue;
            ui.layer(p);
            if (ui.mouseOverLayer(p) && (ui.pressedAnywhere(0) || ui.pressedAnywhere(1))) raise = p;
            p.render(ui, w, h);
        }

        ui.layer(SEARCH);
        float sx = (w - SEARCH_W) / 2, sy = 24;
        search = ui.textField(SEARCH, sx, sy, SEARCH_W, SEARCH_H, search, "Search modules and settings", 48);
        ui.layerBounds(sx - 2, sy - 2, SEARCH_W + 4, SEARCH_H + 4);
        String hint = "Click to toggle  ·  Right-click for settings  ·  Drag headers to move  ·  Esc to close";
        c.text(hint, (w - c.textWidth(hint, FontRole.CAPTION)) / 2, h - 28, FontRole.CAPTION, t.subtle());

        ui.drawPopup();
        ui.drawTooltip();
        c.popScale();
        c.popAlpha();

        if (raise != null && panels.get(panels.size() - 1) != raise) {
            panels.remove(raise);
            panels.add(raise);
        }
        if (!closing) {
            if (ui.takeKey(Keys.ESCAPE) != null) requestClose();
            else if (ui.focus() == null && ui.takeKey(core.client().guiKey.code()) != null) requestClose();
        }
        ui.endFrame();
    }

    // ---- input passthrough --------------------------------------------------------------

    public void mouseMoved(float x, float y) { ui.mouseMoved(x, y); }

    public void mouseDown(float x, float y, int button) {
        ui.mouseMoved(x, y);
        if (!closing) ui.mouseDown(button);
    }

    public void mouseUp(float x, float y, int button) {
        ui.mouseMoved(x, y);
        ui.mouseUp(button);
    }

    public void scroll(float amount) { if (!closing) ui.scroll(amount); }

    public void keyPressed(int key, int mods) { if (!closing) ui.keyPressed(key, mods); }

    public void charTyped(int codepoint) { if (!closing) ui.charTyped(codepoint); }

    // ---- layout persistence -------------------------------------------------------------

    public JsonObject layoutToJson() {
        JsonObject o = new JsonObject();
        JsonObject ps = new JsonObject();
        if (built) {
            for (Panel p : panels) ps.add(p.id(), panelJson(p.x, p.y, p.collapsed));
        } else {
            savedLayout.forEach((id, v) -> ps.add(id, panelJson(v[0], v[1], v[2] != 0)));
        }
        o.add("panels", ps);
        return o;
    }

    private static JsonObject panelJson(float x, float y, boolean collapsed) {
        JsonObject j = new JsonObject();
        j.addProperty("x", x);
        j.addProperty("y", y);
        j.addProperty("collapsed", collapsed);
        return j;
    }

    public void layoutFromJson(JsonObject o) {
        if (o == null || !o.has("panels") || !o.get("panels").isJsonObject()) return;
        for (Map.Entry<String, com.google.gson.JsonElement> e : o.getAsJsonObject("panels").entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject j = e.getValue().getAsJsonObject();
            try {
                float[] v = {j.get("x").getAsFloat(), j.get("y").getAsFloat(), j.get("collapsed").getAsBoolean() ? 1 : 0};
                savedLayout.put(e.getKey(), v);
                for (Panel p : panels) {
                    if (p.id().equals(e.getKey())) {
                        p.x = v[0];
                        p.y = v[1];
                        p.collapsed = v[2] != 0;
                    }
                }
            } catch (RuntimeException ignored) {
                // malformed entry: keep default position
            }
        }
    }

    /** For tests and previews: positions of panels by id. */
    public Map<String, float[]> panelBounds() {
        Map<String, float[]> out = new HashMap<>();
        for (Panel p : panels) out.put(p.id(), new float[] {p.x, p.y, p.w, p.height()});
        return out;
    }

    /** Expands/collapses a panel by id (remembered if the panels aren't built yet). */
    public void setCollapsed(String panelId, boolean collapsed) {
        if (!built) {
            float[] v = savedLayout.get(panelId);
            if (v != null) v[2] = collapsed ? 1 : 0;
            else pendingCollapse.put(panelId, collapsed);
            return;
        }
        for (Panel p : panels) if (p.id().equals(panelId)) p.collapsed = collapsed;
    }

    /** Moves a panel by id (remembered if the panels aren't built yet). */
    public void movePanel(String panelId, float x, float y) {
        if (!built) {
            Boolean c = pendingCollapse.remove(panelId);
            float[] old = savedLayout.get(panelId);
            float collapsed = c != null ? (c ? 1 : 0) : old != null ? old[2] : -1;
            savedLayout.put(panelId, new float[] {x, y, collapsed});
            return;
        }
        for (Panel p : panels) {
            if (p.id().equals(panelId)) {
                p.x = x;
                p.y = y;
            }
        }
    }
}
