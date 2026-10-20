package dev.noir.gui;

import dev.noir.util.Colors;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Canvas that maps design pixels to physical pixels and rasterizes shapes itself.
 * Backends implement four pixel-space primitives.
 */
public abstract class RasterCanvas implements Canvas {
    private final float pixelWidth;
    private final float pixelHeight;
    private final float baseScale;
    private float scale;
    private float tx;
    private float ty;
    private float alpha = 1f;
    private final Deque<float[]> transforms = new ArrayDeque<>();
    private final Deque<Float> alphas = new ArrayDeque<>();
    private final Deque<int[]> clips = new ArrayDeque<>();

    /**
     * @param pixelWidth  surface width in physical pixels
     * @param pixelHeight surface height in physical pixels
     * @param pixelsPerDesignPixel UI scale: physical pixels per design pixel
     */
    protected RasterCanvas(float pixelWidth, float pixelHeight, float pixelsPerDesignPixel) {
        this.pixelWidth = pixelWidth;
        this.pixelHeight = pixelHeight;
        this.baseScale = pixelsPerDesignPixel;
        this.scale = pixelsPerDesignPixel;
    }

    /** Fills a physical-pixel rectangle [x0,x1) x [y0,y1). Alpha is already applied. */
    protected abstract void fillPixels(int x0, int y0, int x1, int y1, int argb);

    /** Draws text with its line box top-left at physical (px, py); {@code lineHeightPx} is the line box height. */
    protected abstract void drawTextPixels(String s, float px, float py, float sizePx, float lineHeightPx, FontRole role, int argb);

    protected abstract float textWidthPixels(String s, float sizePx, FontRole role);

    /** Pushes a clip rectangle {x0, y0, x1, y1} in physical pixels (already intersected with the parent). */
    protected abstract void pushClipPixels(int[] rect);

    /** Pops the last clip; {@code restored} is the clip now in effect, or null. */
    protected abstract void popClipPixels(int[] restored);

    protected void drawItemPixels(Object stack, float px, float py, float sizePx) {}

    @Override
    public float width() {
        return pixelWidth / baseScale;
    }

    @Override
    public float height() {
        return pixelHeight / baseScale;
    }

    public float pixelsPerDesignPixel() {
        return baseScale;
    }

    protected final float currentScale() {
        return scale;
    }

    private int px(float x) {
        return Math.round(tx + x * scale);
    }

    private int py(float y) {
        return Math.round(ty + y * scale);
    }

    @Override
    public void rect(float x, float y, float w, float h, int argb) {
        int c = Colors.mulAlpha(argb, alpha);
        if (Colors.alpha(c) == 0) return;
        int x0 = px(x), y0 = py(y), x1 = px(x + w), y1 = py(y + h);
        if (x1 > x0 && y1 > y0) fillPixels(x0, y0, x1, y1, c);
    }

    @Override
    public void roundRect(float x, float y, float w, float h, float radius, int argb) {
        shape(x, y, w, h, radius, 0f, argb);
    }

    @Override
    public void roundOutline(float x, float y, float w, float h, float radius, float thickness, int argb) {
        shape(x, y, w, h, radius, Math.max(thickness, 1f / scale), argb);
    }

    private void shape(float x, float y, float w, float h, float radius, float thickness, int argb) {
        int c = Colors.mulAlpha(argb, alpha);
        int baseAlpha = Colors.alpha(c);
        if (baseAlpha == 0) return;
        int x0 = px(x), y0 = py(y);
        int pw = px(x + w) - x0, ph = py(y + h) - y0;
        if (pw <= 0 || ph <= 0) return;
        float r = radius * scale;
        if (r < 0.5f && thickness <= 0) {
            fillPixels(x0, y0, x0 + pw, y0 + ph, c);
            return;
        }
        int[] rects = RoundRectRaster.rects(pw, ph, r, thickness * scale);
        for (int i = 0; i < rects.length; i += 5) {
            int a = rects[i + 4] == 255 ? baseAlpha : Math.round(baseAlpha * (rects[i + 4] / 255f));
            if (a == 0) continue;
            fillPixels(x0 + rects[i], y0 + rects[i + 1], x0 + rects[i + 2], y0 + rects[i + 3], Colors.withAlpha(c, a));
        }
    }

    @Override
    public void text(String s, float x, float y, FontRole role, int argb) {
        if (s == null || s.isEmpty()) return;
        int c = Colors.mulAlpha(argb, alpha);
        if (Colors.alpha(c) < 4) return;
        drawTextPixels(s, tx + x * scale, ty + y * scale, role.size * scale, role.lineHeight * scale, role, c);
    }

    @Override
    public float textWidth(String s, FontRole role) {
        if (s == null || s.isEmpty()) return 0;
        return textWidthPixels(s, role.size * scale, role) / scale;
    }

    @Override
    public void pushClip(float x, float y, float w, float h) {
        int[] r = {px(x), py(y), px(x + w), py(y + h)};
        int[] cur = clips.peek();
        if (cur != null) {
            r[0] = Math.max(r[0], cur[0]);
            r[1] = Math.max(r[1], cur[1]);
            r[2] = Math.min(r[2], cur[2]);
            r[3] = Math.min(r[3], cur[3]);
        }
        if (r[2] < r[0]) r[2] = r[0];
        if (r[3] < r[1]) r[3] = r[1];
        clips.push(r);
        pushClipPixels(r);
    }

    @Override
    public void popClip() {
        clips.pop();
        popClipPixels(clips.peek());
    }

    /** Current clip in design pixels, or null. Used by the UI for hit testing. */
    public float[] clipDesign() {
        int[] c = clips.peek();
        if (c == null) return null;
        return new float[] {(c[0] - tx) / scale, (c[1] - ty) / scale, (c[2] - tx) / scale, (c[3] - ty) / scale};
    }

    @Override
    public void pushAlpha(float a) {
        alphas.push(alpha);
        alpha *= Math.max(0f, Math.min(1f, a));
    }

    @Override
    public void popAlpha() {
        alpha = alphas.pop();
    }

    @Override
    public void pushScale(float s, float cx, float cy) {
        transforms.push(new float[] {scale, tx, ty});
        tx = tx + cx * scale * (1f - s);
        ty = ty + cy * scale * (1f - s);
        scale = scale * s;
    }

    @Override
    public void popScale() {
        float[] t = transforms.pop();
        scale = t[0];
        tx = t[1];
        ty = t[2];
    }

    @Override
    public void item(Object stack, float x, float y, float size) {
        if (stack != null && alpha > 0.05f) drawItemPixels(stack, tx + x * scale, ty + y * scale, size * scale);
    }

    /** Maps a design-space point through the current transform back to untransformed design space. */
    public float[] toScreen(float x, float y) {
        return new float[] {(tx + x * scale) / baseScale, (ty + y * scale) / baseScale};
    }
}
