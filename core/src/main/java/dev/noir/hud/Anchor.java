package dev.noir.hud;

/** Which screen edge/corner a HUD element is positioned from. Offsets point inward. */
public enum Anchor {
    TOP_LEFT("Top left", 0, 0),
    TOP_CENTER("Top center", 0.5f, 0),
    TOP_RIGHT("Top right", 1, 0),
    MIDDLE_LEFT("Middle left", 0, 0.5f),
    CENTER("Center", 0.5f, 0.5f),
    MIDDLE_RIGHT("Middle right", 1, 0.5f),
    BOTTOM_LEFT("Bottom left", 0, 1),
    BOTTOM_CENTER("Bottom center", 0.5f, 1),
    BOTTOM_RIGHT("Bottom right", 1, 1);

    private final String label;
    public final float fx;
    public final float fy;

    Anchor(String label, float fx, float fy) {
        this.label = label;
        this.fx = fx;
        this.fy = fy;
    }

    /** Top-left coordinate along one axis for an element of {@code size}. */
    public static float place(float f, float screen, float size, float offset) {
        if (f == 0) return offset;
        if (f == 1) return screen - size - offset;
        return (screen - size) / 2 + offset;
    }

    /** Inverse of {@link #place}: the offset that puts the element at {@code pos}. */
    public static float offsetFor(float f, float screen, float size, float pos) {
        if (f == 0) return pos;
        if (f == 1) return screen - size - pos;
        return pos - (screen - size) / 2;
    }

    public static Anchor nearest(float cx, float cy, float screenW, float screenH) {
        int col = cx < screenW / 3 ? 0 : cx < screenW * 2 / 3 ? 1 : 2;
        int row = cy < screenH / 3 ? 0 : cy < screenH * 2 / 3 ? 1 : 2;
        return values()[row * 3 + col];
    }

    public boolean right() {
        return fx == 1;
    }

    public boolean bottom() {
        return fy == 1;
    }

    @Override
    public String toString() {
        return label;
    }
}
