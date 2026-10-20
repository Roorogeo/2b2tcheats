package dev.noir.preview;

import dev.noir.gui.FontRole;
import dev.noir.gui.RasterCanvas;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * The same raster pipeline the game uses, drawn into a BufferedImage with the bundled
 * Inter TTFs. Used for GUI previews and GUI tests.
 */
public final class Java2DCanvas extends RasterCanvas {
    private static final Map<String, Font> BASE_FONTS = new HashMap<>();
    private final BufferedImage image;
    private final Graphics2D g;
    private final Map<String, Font> sized = new HashMap<>();

    public Java2DCanvas(int pixelWidth, int pixelHeight, float pixelsPerDesignPixel) {
        super(pixelWidth, pixelHeight, pixelsPerDesignPixel);
        this.image = new BufferedImage(pixelWidth, pixelHeight, BufferedImage.TYPE_INT_ARGB);
        this.g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setComposite(AlphaComposite.SrcOver);
    }

    public BufferedImage image() {
        return image;
    }

    public Graphics2D graphics() {
        return g;
    }

    public void fillBackground(int argb) {
        g.setColor(new Color(argb, true));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
    }

    private static Font base(String file) {
        synchronized (BASE_FONTS) {
            return BASE_FONTS.computeIfAbsent(file, f -> {
                try (InputStream in = Java2DCanvas.class.getResourceAsStream("/assets/noir/font/" + f + ".ttf")) {
                    if (in == null) throw new IllegalStateException("missing font " + f);
                    return Font.createFont(Font.TRUETYPE_FONT, in);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to load " + f, e);
                }
            });
        }
    }

    private Font font(FontRole role, float sizePx) {
        String key = role.weightFile() + "@" + Math.round(sizePx * 100);
        return sized.computeIfAbsent(key, k -> base(role.weightFile()).deriveFont(sizePx));
    }

    @Override
    protected void fillPixels(int x0, int y0, int x1, int y1, int argb) {
        g.setColor(new Color(argb, true));
        g.fillRect(x0, y0, x1 - x0, y1 - y0);
    }

    @Override
    protected void drawTextPixels(String s, float px, float py, float sizePx, float lineHeightPx, FontRole role, int argb) {
        Font f = font(role, sizePx);
        g.setFont(f);
        g.setColor(new Color(argb, true));
        FontMetrics fm = g.getFontMetrics(f);
        float baseline = py + (lineHeightPx - (fm.getAscent() + fm.getDescent())) / 2f + fm.getAscent();
        g.drawString(s, px, baseline);
    }

    @Override
    protected float textWidthPixels(String s, float sizePx, FontRole role) {
        FontRenderContext frc = g.getFontRenderContext();
        return (float) font(role, sizePx).getStringBounds(s, frc).getWidth();
    }

    @Override
    protected void pushClipPixels(int[] rect) {
        setClip(rect);
    }

    @Override
    protected void popClipPixels(int[] restored) {
        setClip(restored);
    }

    private void setClip(int[] rect) {
        if (rect == null) g.setClip(null);
        else g.setClip(rect[0], rect[1], Math.max(0, rect[2] - rect[0]), Math.max(0, rect[3] - rect[1]));
    }

    @Override
    protected void drawItemPixels(Object stack, float px, float py, float sizePx) {
        // Stand-in for item icons (the game draws real ones).
        g.setColor(new Color(0xFF8A8A8A, true));
        g.fillRoundRect(Math.round(px + sizePx * 0.15f), Math.round(py + sizePx * 0.15f), Math.round(sizePx * 0.7f),
            Math.round(sizePx * 0.7f), Math.round(sizePx * 0.25f), Math.round(sizePx * 0.25f));
    }
}
