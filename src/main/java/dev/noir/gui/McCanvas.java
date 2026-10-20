package dev.noir.gui;

import com.mojang.blaze3d.platform.Window;
import dev.noir.NoirCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * {@link Canvas} over Minecraft's GUI renderer. The pose is scaled by 1/guiScale so one
 * unit is one physical pixel; shapes are rasterized by {@link RasterCanvas} into pixel
 * fills, and text is drawn from the Inter glyph atlas (vanilla font as fallback).
 */
public final class McCanvas extends RasterCanvas {
    private final GuiGraphicsExtractor g;
    private final Minecraft mc;
    private final boolean inter;

    private McCanvas(GuiGraphicsExtractor g, Minecraft mc, int pixelWidth, int pixelHeight, float ratio, boolean inter) {
        super(pixelWidth, pixelHeight, ratio);
        this.g = g;
        this.mc = mc;
        this.inter = inter;
    }

    /** Physical pixels per design pixel. */
    public static float pixelRatio(Minecraft mc, NoirCore core) {
        float r = core.client().uiScale.getFloat();
        if (core.client().followGuiScale.get()) r *= mc.getWindow().getGuiScale() / 2f;
        return Math.max(0.25f, r);
    }

    /** Converts a GUI-scaled mouse coordinate to design pixels. */
    public static float toDesign(Minecraft mc, NoirCore core, double guiCoord) {
        return (float) (guiCoord * mc.getWindow().getGuiScale() / pixelRatio(mc, core));
    }

    public static McCanvas begin(GuiGraphicsExtractor g, NoirCore core) {
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        int guiScale = w.getGuiScale();
        g.nextStratum();
        g.pose().pushMatrix();
        g.pose().scale(1f / guiScale, 1f / guiScale);
        return new McCanvas(g, mc, w.getWidth(), w.getHeight(), pixelRatio(mc, core), core.client().useInter.get() && InterFontAtlas.available());
    }

    public void end() {
        g.pose().popMatrix();
        g.nextStratum();
    }

    @Override
    protected void fillPixels(int x0, int y0, int x1, int y1, int argb) {
        g.fill(x0, y0, x1, y1, argb);
    }

    @Override
    protected void drawTextPixels(String s, float px, float py, float sizePx, float lineHeightPx, FontRole role, int argb) {
        if (inter && InterFontAtlas.draw(g, s, px, py, sizePx, lineHeightPx, role, argb)) return;
        float k = sizePx / 9f;
        g.pose().pushMatrix();
        g.pose().translate(px, py + (lineHeightPx - 9f * k) / 2f);
        g.pose().scale(k, k);
        g.text(mc.font, s, 0, 0, argb, false);
        g.pose().popMatrix();
    }

    @Override
    protected float textWidthPixels(String s, float sizePx, FontRole role) {
        if (inter) {
            float w = InterFontAtlas.width(s, sizePx, role);
            if (w >= 0) return w;
        }
        return mc.font.width(s) * (sizePx / 9f);
    }

    @Override
    protected void pushClipPixels(int[] rect) {
        g.enableScissor(rect[0], rect[1], rect[2], rect[3]);
    }

    @Override
    protected void popClipPixels(int[] restored) {
        g.disableScissor();
    }

    @Override
    protected void drawItemPixels(Object stack, float px, float py, float sizePx) {
        if (!(stack instanceof ItemStack item)) return;
        g.pose().pushMatrix();
        g.pose().translate(px, py);
        g.pose().scale(sizePx / 16f, sizePx / 16f);
        g.item(item, 0, 0);
        g.pose().popMatrix();
    }
}
