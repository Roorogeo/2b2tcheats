package dev.noir.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.LineMetrics;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders the bundled Inter TTFs: glyphs are rasterized once per (weight, pixel size)
 * with Java's font rasterizer — the same one the GUI previews use, so in-game metrics
 * match them — into a white-on-transparent texture, then drawn with tinted GUI blits at
 * 1:1 physical pixels. Strings with characters outside the atlas use the vanilla font.
 */
final class InterFontAtlas {
    private static final Logger LOG = LoggerFactory.getLogger("Noir");
    private static final String CHARSET;
    private static final int ATLAS_WIDTH = 1024;
    private static final Map<String, Atlas> ATLASES = new HashMap<>();
    private static final Map<String, Font> BASE = new HashMap<>();
    private static boolean failed;

    static {
        StringBuilder b = new StringBuilder();
        for (char c = 32; c < 127; c++) b.append(c);
        for (char c = 160; c < 256; c++) b.append(c);
        b.append("·−×…°–—•←→↑↓✓");
        CHARSET = b.toString();
        // Font rasterization only; never start an AWT toolkit next to GLFW.
        if (System.getProperty("java.awt.headless") == null) System.setProperty("java.awt.headless", "true");
    }

    private record Glyph(int u, int v, int w, int h, int xOff, int yOff, float advance) {}

    private record Atlas(Identifier id, int texW, int texH, Map<Character, Glyph> glyphs, float ascent, float descent) {}

    private InterFontAtlas() {}

    static boolean available() {
        return !failed;
    }

    private static Atlas atlas(FontRole role, float sizePx) {
        int px = Math.max(4, Math.round(sizePx));
        String key = role.weightFile() + "_" + px;
        Atlas a = ATLASES.get(key);
        if (a != null || failed) return a;
        try {
            a = build(role.weightFile(), px, key);
            ATLASES.put(key, a);
            return a;
        } catch (Throwable t) {
            failed = true;
            LOG.error("Inter font unavailable, falling back to the vanilla font", t);
            return null;
        }
    }

    private static Font base(String file) throws Exception {
        Font f = BASE.get(file);
        if (f != null) return f;
        try (InputStream in = InterFontAtlas.class.getResourceAsStream("/assets/noir/font/" + file + ".ttf")) {
            if (in == null) throw new IllegalStateException("missing " + file);
            f = Font.createFont(Font.TRUETYPE_FONT, in);
        }
        BASE.put(file, f);
        return f;
    }

    private static Atlas build(String file, int px, String key) throws Exception {
        Font font = base(file).deriveFont((float) px);
        FontRenderContext frc = new FontRenderContext(null, true, true);
        LineMetrics lm = font.getLineMetrics("Ag", frc);

        record Pending(char c, GlyphVector gv, Rectangle bounds, float advance) {}
        List<Pending> pending = new ArrayList<>();
        for (char c : CHARSET.toCharArray()) {
            if (!font.canDisplay(c)) continue;
            GlyphVector gv = font.createGlyphVector(frc, String.valueOf(c));
            pending.add(new Pending(c, gv, gv.getPixelBounds(frc, 0, 0), gv.getGlyphMetrics(0).getAdvance()));
        }

        // Shelf packing with 1px padding around each glyph.
        Map<Character, Glyph> glyphs = new HashMap<>();
        int x = 0, y = 0, rowH = 0;
        int[][] pos = new int[pending.size()][2];
        for (int i = 0; i < pending.size(); i++) {
            Rectangle r = pending.get(i).bounds;
            int w = Math.max(1, r.width) + 2, h = Math.max(1, r.height) + 2;
            if (x + w > ATLAS_WIDTH) {
                x = 0;
                y += rowH;
                rowH = 0;
            }
            pos[i][0] = x;
            pos[i][1] = y;
            x += w;
            rowH = Math.max(rowH, h);
        }
        int texH = Integer.highestOneBit(Math.max(1, y + rowH) - 1) << 1;

        BufferedImage img = new BufferedImage(ATLAS_WIDTH, texH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(java.awt.Color.WHITE);
        for (int i = 0; i < pending.size(); i++) {
            Pending p = pending.get(i);
            Rectangle r = p.bounds;
            int u = pos[i][0], v = pos[i][1];
            g.drawGlyphVector(p.gv, u + 1 - r.x, v + 1 - r.y);
            glyphs.put(p.c, new Glyph(u, v, Math.max(1, r.width) + 2, Math.max(1, r.height) + 2, r.x - 1, r.y - 1, p.advance));
        }
        g.dispose();

        NativeImage native_ = new NativeImage(ATLAS_WIDTH, texH, false);
        for (int yy = 0; yy < texH; yy++) {
            for (int xx = 0; xx < ATLAS_WIDTH; xx++) {
                int a = img.getRGB(xx, yy) >>> 24;
                native_.setPixel(xx, yy, (a << 24) | 0xFFFFFF);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("noir", "font/" + key.toLowerCase(Locale.ROOT).replace('-', '_'));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "noir " + key, native_));
        return new Atlas(id, ATLAS_WIDTH, texH, glyphs, lm.getAscent(), lm.getDescent());
    }

    private static boolean covers(Atlas a, String s) {
        for (int i = 0; i < s.length(); i++) if (!a.glyphs.containsKey(s.charAt(i))) return false;
        return true;
    }

    /** Width in pixels, or -1 if the string needs the fallback font. */
    static float width(String s, float sizePx, FontRole role) {
        Atlas a = atlas(role, sizePx);
        if (a == null || !covers(a, s)) return -1;
        float w = 0;
        for (int i = 0; i < s.length(); i++) w += a.glyphs.get(s.charAt(i)).advance;
        return w;
    }

    /** Draws {@code s} with its line box top-left at (px, py). Returns false to request the fallback font. */
    static boolean draw(GuiGraphicsExtractor g, String s, float px, float py, float sizePx, float lineHeightPx, FontRole role, int argb) {
        Atlas a = atlas(role, sizePx);
        if (a == null || !covers(a, s)) return false;
        int baseline = Math.round(py + (lineHeightPx - (a.ascent + a.descent)) / 2f + a.ascent);
        float x = px;
        for (int i = 0; i < s.length(); i++) {
            Glyph gl = a.glyphs.get(s.charAt(i));
            if (gl.w > 3 || gl.h > 3) {
                g.blit(RenderPipelines.GUI_TEXTURED, a.id, Math.round(x) + gl.xOff, baseline + gl.yOff, gl.u, gl.v,
                    gl.w, gl.h, gl.w, gl.h, a.texW, a.texH, argb);
            }
            x += gl.advance;
        }
        return true;
    }
}
