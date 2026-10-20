package dev.noir.hud;

import dev.noir.NoirCore;
import dev.noir.gui.Canvas;
import dev.noir.gui.FontRole;
import dev.noir.gui.Theme;
import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.EnumSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.module.settings.SettingGroup;
import dev.noir.module.settings.SettingsHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * A movable HUD element. Position = anchor + inward offset, in design pixels;
 * everything is drawn at the element's scale. Elements only read data.
 */
public abstract class HudElement extends SettingsHolder {
    private final String id;
    private final SettingGroup layout = new SettingGroup("Layout", "Position, size and alignment.").collapsedByDefault();
    public final BoolSetting enabled;
    public final EnumSetting<Anchor> anchor;
    public final NumberSetting offsetX;
    public final NumberSetting offsetY;
    public final NumberSetting scale;
    public final BoolSetting card;
    protected NoirCore core;

    protected HudElement(String id, String name, String description, boolean enabledByDefault, Anchor defaultAnchor,
                         float defaultX, float defaultY, boolean cardByDefault) {
        super(name, description);
        this.id = id;
        this.enabled = add(new BoolSetting("Enabled", enabledByDefault, "Show this element."));
        this.card = add(new BoolSetting("Card", cardByDefault, "Draw a surface card with a 1px border behind the element."));
        this.anchor = addTo(layout, new EnumSetting<>("Alignment", defaultAnchor,
            "Screen corner or edge this element is positioned and aligned from."));
        this.offsetX = addTo(layout, new NumberSetting("Offset X", defaultX, -4000, 4000, 1, "Horizontal distance from the anchor edge, inward."));
        this.offsetY = addTo(layout, new NumberSetting("Offset Y", defaultY, -4000, 4000, 1, "Vertical distance from the anchor edge, inward."));
        this.scale = addTo(layout, new NumberSetting("Scale", 1.0, 0.5, 3.0, 0.05, "Size of this element.").unit("x"));
    }

    @Override
    public List<SettingGroup> groups() {
        List<SettingGroup> all = new ArrayList<>(super.groups());
        all.add(layout);
        return all;
    }

    void bind(NoirCore core) {
        this.core = core;
    }

    public final String id() {
        return id;
    }

    /** Unscaled content size {w, h} in design pixels. */
    public abstract float[] size(Canvas c, HudData data);

    /** Draws content with its top-left at (x, y), unscaled (the manager applies scale). */
    public abstract void render(Canvas c, HudData data, float x, float y);

    /** Shown in the HUD editor even when there's nothing to display. */
    public boolean hasContent(HudData data) {
        return true;
    }

    protected final Theme theme() {
        return core.theme();
    }

    protected final boolean alignRight() {
        return anchor.get().right();
    }

    /** Draws the optional card behind the element. */
    protected final void cardBg(Canvas c, float x, float y, float w, float h) {
        if (!card.get()) return;
        Theme t = theme();
        c.roundRect(x, y, w, h, t.rControl(), t.surface());
        c.roundOutline(x, y, w, h, t.rControl(), 1, t.edge());
    }

    /** Padding inside a card (0 without card). */
    protected final float pad() {
        return card.get() ? theme().padC() : 0;
    }

    protected static float lh(Canvas c, FontRole r) {
        return c.lineHeight(r);
    }
}
