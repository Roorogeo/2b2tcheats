package dev.noir.gui;

/**
 * Drawing surface in design pixels. Implemented over Minecraft's GuiGraphics in game
 * and over Java2D for previews/tests, both through {@link RasterCanvas} so shapes are
 * rasterized identically.
 */
public interface Canvas {
    float width();

    float height();

    void rect(float x, float y, float w, float h, int argb);

    /** Anti-aliased filled rounded rectangle. */
    void roundRect(float x, float y, float w, float h, float radius, int argb);

    /** Anti-aliased rounded outline drawn inside the given bounds. */
    void roundOutline(float x, float y, float w, float h, float radius, float thickness, int argb);

    /** Draws text with its line box's top-left at (x, y). */
    void text(String s, float x, float y, FontRole role, int argb);

    float textWidth(String s, FontRole role);

    default float lineHeight(FontRole role) {
        return role.lineHeight;
    }

    void pushClip(float x, float y, float w, float h);

    void popClip();

    /** Multiplies the alpha of everything drawn until {@link #popAlpha()}. */
    void pushAlpha(float alpha);

    void popAlpha();

    /** Scales everything drawn until {@link #popScale()} around (cx, cy). */
    void pushScale(float scale, float cx, float cy);

    void popScale();

    /** Draws a game item (an opaque platform ItemStack) with its top-left at (x, y). */
    default void item(Object stack, float x, float y, float size) {}

    /** Trims {@code s} with an ellipsis so it fits {@code maxWidth}. */
    default String ellipsize(String s, FontRole role, float maxWidth) {
        if (textWidth(s, role) <= maxWidth) return s;
        String dots = "…";
        int end = s.length();
        while (end > 0 && textWidth(s.substring(0, end) + dots, role) > maxWidth) end--;
        return s.substring(0, end) + dots;
    }
}
