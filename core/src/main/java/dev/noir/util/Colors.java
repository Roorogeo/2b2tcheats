package dev.noir.util;

import java.util.Locale;

/** ARGB color helpers. All colors in Noir are packed 0xAARRGGBB ints. */
public final class Colors {
    private Colors() {}

    public static int argb(int a, int r, int g, int b) {
        return (clamp255(a) << 24) | (clamp255(r) << 16) | (clamp255(g) << 8) | clamp255(b);
    }

    /** CSS-style rgba(r, g, b, a) where alpha is 0..1. */
    public static int rgba(int r, int g, int b, double alpha) {
        return argb((int) Math.round(alpha * 255.0), r, g, b);
    }

    public static int alpha(int c) { return c >>> 24; }
    public static int red(int c) { return (c >> 16) & 0xFF; }
    public static int green(int c) { return (c >> 8) & 0xFF; }
    public static int blue(int c) { return c & 0xFF; }

    public static int withAlpha(int c, int alpha) {
        return (c & 0x00FFFFFF) | (clamp255(alpha) << 24);
    }

    /** Multiplies the color's existing alpha by {@code factor} (0..1). */
    public static int mulAlpha(int c, float factor) {
        if (factor >= 1f) return c;
        if (factor <= 0f) return c & 0x00FFFFFF;
        return withAlpha(c, Math.round(alpha(c) * factor));
    }

    public static int lerp(int from, int to, float t) {
        if (t <= 0f) return from;
        if (t >= 1f) return to;
        return argb(
            Math.round(alpha(from) + (alpha(to) - alpha(from)) * t),
            Math.round(red(from) + (red(to) - red(from)) * t),
            Math.round(green(from) + (green(to) - green(from)) * t),
            Math.round(blue(from) + (blue(to) - blue(from)) * t));
    }

    public static String toHex(int c) {
        return String.format(Locale.ROOT, "#%08X", c);
    }

    /**
     * Parses {@code #RGB}, {@code #RRGGBB}, {@code #AARRGGBB}, {@code 0xAARRGGBB} and
     * {@code rgba(r,g,b,a)}. Returns null when the text is not a color.
     */
    public static Integer parse(String text) {
        if (text == null) return null;
        String s = text.trim().toLowerCase(Locale.ROOT);
        try {
            if (s.startsWith("rgba(") && s.endsWith(")")) {
                String[] p = s.substring(5, s.length() - 1).split(",");
                if (p.length != 4) return null;
                return rgba(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()),
                    Integer.parseInt(p[2].trim()), Double.parseDouble(p[3].trim()));
            }
            if (s.startsWith("rgb(") && s.endsWith(")")) {
                String[] p = s.substring(4, s.length() - 1).split(",");
                if (p.length != 3) return null;
                return argb(255, Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
            }
            if (s.startsWith("#")) s = s.substring(1);
            else if (s.startsWith("0x")) s = s.substring(2);
            if (!s.matches("[0-9a-f]+")) return null;
            return switch (s.length()) {
                case 3 -> {
                    int r = Character.digit(s.charAt(0), 16), g = Character.digit(s.charAt(1), 16), b = Character.digit(s.charAt(2), 16);
                    yield argb(255, r * 17, g * 17, b * 17);
                }
                case 6 -> 0xFF000000 | Integer.parseUnsignedInt(s, 16);
                case 8 -> Integer.parseUnsignedInt(s, 16);
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Returns {hue 0..1, saturation 0..1, value 0..1}. */
    public static float[] toHsv(int c) {
        float r = red(c) / 255f, g = green(c) / 255f, b = blue(c) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d == 0) h = 0;
        else if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        return new float[] {h, max == 0 ? 0 : d / max, max};
    }

    public static int fromHsv(float h, float s, float v, int alpha) {
        h = ((h % 1f) + 1f) % 1f;
        float c = v * s;
        float hp = h * 6f;
        float x = c * (1 - Math.abs(hp % 2f - 1));
        float r, g, b;
        if (hp < 1) { r = c; g = x; b = 0; }
        else if (hp < 2) { r = x; g = c; b = 0; }
        else if (hp < 3) { r = 0; g = c; b = x; }
        else if (hp < 4) { r = 0; g = x; b = c; }
        else if (hp < 5) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        float m = v - c;
        return argb(alpha, Math.round((r + m) * 255), Math.round((g + m) * 255), Math.round((b + m) * 255));
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : Math.min(v, 255);
    }
}
