package dev.noir.gui;

/**
 * Noir type scale. Sizes are design pixels; weights map to bundled Inter files.
 * Headings 600, body 400, labels 500.
 */
public enum FontRole {
    DISPLAY(22, 600, 28),
    HEADING(15, 600, 20),
    BODY(13, 400, 18),
    LABEL(12, 500, 16),
    CAPTION(12, 400, 16);

    public final float size;
    public final int weight;
    public final float lineHeight;

    FontRole(float size, int weight, float lineHeight) {
        this.size = size;
        this.weight = weight;
        this.lineHeight = lineHeight;
    }

    /** Font id under assets/noir/font (e.g. "inter_semibold_15"). */
    public String fontId() {
        return weightFile().replace('-', '_') + "_" + (int) size;
    }

    /** TTF file under assets/noir/font. */
    public String weightFile() {
        return switch (weight) {
            case 600 -> "inter-semibold";
            case 500 -> "inter-medium";
            default -> "inter-regular";
        };
    }
}
