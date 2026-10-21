package dev.noir.render;

import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.EnumSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.util.Colors;

/**
 * The fill / outline / tracer options every render module exposes. Created through
 * {@code SettingsHolder.renderOptions(...)} so the settings live in the module.
 */
public final class RenderOptions {
    public final BoolSetting fill;
    public final NumberSetting fillOpacity;
    public final BoolSetting outline;
    public final NumberSetting lineWidth;
    public final BoolSetting tracers;
    public final NumberSetting tracerWidth;
    public final EnumSetting<Shapes.TracerOrigin> tracerOrigin;

    public RenderOptions(BoolSetting fill, NumberSetting fillOpacity, BoolSetting outline, NumberSetting lineWidth,
                         BoolSetting tracers, NumberSetting tracerWidth, EnumSetting<Shapes.TracerOrigin> tracerOrigin) {
        this.fill = fill;
        this.fillOpacity = fillOpacity;
        this.outline = outline;
        this.lineWidth = lineWidth;
        this.tracers = tracers;
        this.tracerWidth = tracerWidth;
        this.tracerOrigin = tracerOrigin;
    }

    /** Fill color derived from a base color and the fill opacity. */
    public int fillColor(int base) {
        return fill.get() ? Colors.withAlpha(base, (int) Math.round(Colors.alpha(base) * fillOpacity.getDouble())) : 0;
    }

    public int outlineColor(int base) {
        return outline.get() ? base : 0;
    }

    /** Draws a box with the configured fill and outline. */
    public void box(WorldCanvas c, double x0, double y0, double z0, double x1, double y1, double z1, int base) {
        if (fill.get()) Shapes.fillBox(c, x0, y0, z0, x1, y1, z1, fillColor(base));
        if (outline.get()) Shapes.outlineBox(c, x0, y0, z0, x1, y1, z1, base, lineWidth.getFloat());
    }

    public void tracer(WorldCanvas c, double x, double y, double z, int base) {
        if (tracers.get()) Shapes.tracer(c, x, y, z, base, tracerWidth.getFloat(), tracerOrigin.get());
    }
}
