package dev.noir.gui;

import dev.noir.NoirCore;
import dev.noir.util.Animation;
import dev.noir.util.Clock;
import dev.noir.util.Colors;
import dev.noir.util.Keys;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immediate-mode UI state shared by the ClickGUI and the HUD editor.
 *
 * <p>Input events are queued by the screen between frames and consumed by widgets
 * during the next render. Overlapping panels are resolved with layers: before each frame
 * the owner computes which layer the mouse is over (using last frame's bounds), and only
 * widgets drawn in that layer see hover/clicks.
 */
public final class Ui {
    public final NoirCore core;
    public final Theme t;
    private final Clock clock;
    private Canvas c;

    private float mx;
    private float my;
    private final boolean[] down = new boolean[8];
    private final boolean[] pressed = new boolean[8];
    private final boolean[] released = new boolean[8];
    private final boolean[] consumed = new boolean[8];
    private float scroll;
    private final List<KeyPress> keys = new ArrayList<>();
    private final StringBuilder typed = new StringBuilder();

    private Object active;
    private Object focus;
    private Object layer;
    private Object mouseLayer;
    private final Map<Object, float[]> layerBounds = new HashMap<>();
    private final Map<Object, float[]> nextLayerBounds = new HashMap<>();
    private final Deque<float[]> clips = new ArrayDeque<>();

    private Object tipId;
    private long tipSince;
    private String tipText;
    private String tipExtra;
    private boolean tipSeen;

    private final Map<Object, Animation> anims = new HashMap<>();
    private final Map<Object, TextBuffer> texts = new HashMap<>();
    private final Set<Object> expanded = new HashSet<>();
    private final Map<Object, Float> measured = new HashMap<>();

    public static final class KeyPress {
        public final int key;
        public final int mods;
        boolean consumed;

        KeyPress(int key, int mods) {
            this.key = key;
            this.mods = mods;
        }

        public boolean ctrl() {
            return (mods & Keys.MOD_CONTROL) != 0 || (mods & Keys.MOD_SUPER) != 0;
        }
    }

    public Ui(NoirCore core) {
        this.core = core;
        this.t = core.theme();
        this.clock = core.platform().clock();
    }

    // ---- input queue -------------------------------------------------------------------

    public void mouseMoved(float x, float y) {
        mx = x;
        my = y;
    }

    public void mouseDown(int button) {
        if (button < 0 || button >= down.length) return;
        down[button] = true;
        pressed[button] = true;
    }

    public void mouseUp(int button) {
        if (button < 0 || button >= down.length) return;
        down[button] = false;
        released[button] = true;
    }

    public void scroll(float amount) {
        scroll += amount;
    }

    public void keyPressed(int key, int mods) {
        keys.add(new KeyPress(key, mods));
    }

    public void charTyped(int codepoint) {
        if (codepoint >= 32 && codepoint != 127) typed.appendCodePoint(codepoint);
    }

    // ---- frame -------------------------------------------------------------------------

    /** @param layerOrder layers bottom to top; the topmost whose last bounds contain the mouse owns it */
    public void beginFrame(Canvas canvas, List<Object> layerOrder) {
        this.c = canvas;
        mouseLayer = null;
        for (int i = layerOrder.size() - 1; i >= 0; i--) {
            float[] b = layerBounds.get(layerOrder.get(i));
            if (b != null && mx >= b[0] && my >= b[1] && mx < b[0] + b[2] && my < b[1] + b[3]) {
                mouseLayer = layerOrder.get(i);
                break;
            }
        }
        if (active != null && active instanceof LayerCapture lc) mouseLayer = lc.layer;
        tipSeen = false;
        nextLayerBounds.clear();
        layer = null;
    }

    public void endFrame() {
        layerBounds.clear();
        layerBounds.putAll(nextLayerBounds);
        for (int i = 0; i < down.length; i++) {
            pressed[i] = false;
            released[i] = false;
            consumed[i] = false;
        }
        if (!down[0] && active != null && !(active instanceof Listening)) active = null;
        scroll = 0;
        keys.clear();
        typed.setLength(0);
        if (!tipSeen) tipId = null;
    }

    public Canvas canvas() { return c; }
    public float mouseX() { return mx; }
    public float mouseY() { return my; }
    public long now() { return clock.millis(); }

    public void layer(Object id) { this.layer = id; }

    public Object currentLayer() { return layer; }

    /** Records the bounds of the current layer for next frame's mouse ownership. */
    public void layerBounds(float x, float y, float w, float h) {
        nextLayerBounds.put(layer, new float[] {x, y, w, h});
    }

    public boolean mouseOverLayer(Object id) { return mouseLayer == id; }

    public boolean anyMouseLayer() { return mouseLayer != null; }

    // ---- hit testing -------------------------------------------------------------------

    public boolean inside(float x, float y, float w, float h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public boolean hover(float x, float y, float w, float h) {
        if (layer != mouseLayer) return false;
        if (!inside(x, y, w, h)) return false;
        float[] clip = clips.peek();
        return clip == null || (mx >= clip[0] && my >= clip[1] && mx < clip[2] && my < clip[3]);
    }

    /** True once for a press of {@code button} inside the rect; consumes it. */
    public boolean click(float x, float y, float w, float h, int button) {
        if (!pressed[button] || consumed[button] || !hover(x, y, w, h)) return false;
        consumed[button] = true;
        return true;
    }

    public boolean down(int button) { return down[button]; }

    /** Unconsumed press anywhere (for "click outside closes"). */
    public boolean pressedAnywhere(int button) { return pressed[button] && !consumed[button]; }

    public void consume(int button) { consumed[button] = true; }

    /** Takes the scroll amount if the mouse is over the rect. */
    public float takeScroll(float x, float y, float w, float h) {
        if (scroll == 0 || !hover(x, y, w, h)) return 0;
        float s = scroll;
        scroll = 0;
        return s;
    }

    public List<KeyPress> keys() { return keys; }

    /** Returns and consumes an unconsumed press of {@code key}. */
    public KeyPress takeKey(int key) {
        for (KeyPress k : keys) {
            if (!k.consumed && k.key == key) {
                k.consumed = true;
                return k;
            }
        }
        return null;
    }

    public KeyPress takeAnyKey() {
        for (KeyPress k : keys) {
            if (!k.consumed) {
                k.consumed = true;
                return k;
            }
        }
        return null;
    }

    public String takeTyped() {
        String s = typed.toString();
        typed.setLength(0);
        return s;
    }

    public boolean hasTyped() { return typed.length() > 0; }

    // ---- active / focus ----------------------------------------------------------------

    public Object active() { return active; }

    public void setActive(Object id) { this.active = id; }

    /** Captures the mouse for a drag so it stays with this layer even off its bounds. */
    public void beginDrag(Object id) { this.active = new LayerCapture(layer, id); }

    public boolean dragging(Object id) { return active instanceof LayerCapture lc && lc.id.equals(id) && down[0]; }

    public Object focus() { return focus; }

    public void setFocus(Object id) { this.focus = id; }

    public boolean focused(Object id) { return id.equals(focus); }

    /** Starts listening for a key for a keybind widget. */
    public void listen(Object id) { this.active = new Listening(id); }

    public boolean listening(Object id) { return active instanceof Listening l && l.id.equals(id); }

    public boolean isListening() { return active instanceof Listening; }

    public void stopListening() {
        if (active instanceof Listening) active = null;
    }

    private record LayerCapture(Object layer, Object id) {}

    private record Listening(Object id) {}

    // ---- clip ------------------------------------------------------------------------------

    public void pushClip(float x, float y, float w, float h) {
        float[] r = {x, y, x + w, y + h};
        float[] cur = clips.peek();
        if (cur != null) {
            r[0] = Math.max(r[0], cur[0]);
            r[1] = Math.max(r[1], cur[1]);
            r[2] = Math.min(r[2], cur[2]);
            r[3] = Math.min(r[3], cur[3]);
        }
        clips.push(r);
        c.pushClip(x, y, w, h);
    }

    public void popClip() {
        clips.pop();
        c.popClip();
    }

    // ---- state helpers ---------------------------------------------------------------------

    /** Animated value for {@code id} easing toward {@code target} over ~120ms (scaled by animation speed). */
    public float animate(Object id, float target) {
        return animate(id, target, 120);
    }

    public float animate(Object id, float target, double durationMs) {
        Animation a = anims.computeIfAbsent(id, k -> new Animation(clock, durationMs, core.client().animationSpeed::getDouble, target));
        a.target(target);
        return a.value();
    }

    public boolean expanded(Object id) { return expanded.contains(id); }

    public void setExpanded(Object id, boolean on) {
        if (on) expanded.add(id);
        else expanded.remove(id);
    }

    public void toggleExpanded(Object id) { setExpanded(id, !expanded(id)); }

    public float measured(Object id, float fallback) { return measured.getOrDefault(id, fallback); }

    public void measure(Object id, float value) { measured.put(id, value); }

    // ---- popups -------------------------------------------------------------------------------

    /** A floating element drawn above everything (dropdowns). */
    public interface Popup {
        /** Bounds in design pixels: x, y, w, h. */
        float[] bounds(Canvas canvas);

        void render(Ui ui);
    }

    public static final Object POPUP_LAYER = "noir:popup";

    private Popup popup;

    public void openPopup(Popup p) { this.popup = p; }

    public void closePopup() { this.popup = null; }

    public Popup popup() { return popup; }

    /**
     * Call before drawing the layers below the popup: a press outside the open popup only
     * closes it, it doesn't fall through to the widgets underneath.
     */
    public void dismissPopupOnOutsideClick() {
        if (popup == null) return;
        float[] b = popup.bounds(c);
        boolean outside = !inside(b[0], b[1], b[2], b[3]);
        if (outside && (pressedAnywhere(0) || pressedAnywhere(1))) {
            popup = null;
            consume(0);
            consume(1);
        }
    }

    /** Draws the open popup in its own layer. Call after all panels. */
    public void drawPopup() {
        if (popup == null) return;
        Popup p = popup;
        layer(POPUP_LAYER);
        float[] b = p.bounds(c);
        layerBounds(b[0], b[1], b[2], b[3]);
        p.render(this);
        if (takeKey(Keys.ESCAPE) != null) popup = null;
    }

    // ---- tooltips ----------------------------------------------------------------------------

    public void tooltip(Object id, float x, float y, float w, float h, String text, String extra) {
        if (!core.client().tooltips.get() || text == null || !hover(x, y, w, h)) return;
        tipSeen = true;
        if (!id.equals(tipId)) {
            tipId = id;
            tipSince = clock.millis();
        }
        tipText = text;
        tipExtra = extra;
    }

    /** Draws the pending tooltip on top. Call last. */
    public void drawTooltip() {
        if (tipId == null || !tipSeen) return;
        if (clock.millis() - tipSince < core.client().tooltipDelay.getDouble()) return;
        float maxW = 280;
        float pad = t.padC() + 2;
        List<String> lines = wrap(tipText, FontRole.CAPTION, maxW - pad * 2);
        List<String> extra = tipExtra == null ? List.of() : wrap(tipExtra, FontRole.CAPTION, maxW - pad * 2);
        float lh = c.lineHeight(FontRole.CAPTION);
        float w = 0;
        for (String l : lines) w = Math.max(w, c.textWidth(l, FontRole.CAPTION));
        for (String l : extra) w = Math.max(w, c.textWidth(l, FontRole.CAPTION));
        w += pad * 2;
        float h = pad * 2 + lh * (lines.size() + extra.size()) + (extra.isEmpty() ? 0 : 6);
        float x = mx + 14, y = my + 16;
        if (x + w > c.width() - 8) x = mx - w - 8;
        if (y + h > c.height() - 8) y = my - h - 8;
        c.roundRect(x, y, w, h, t.rControl(), t.raised());
        c.roundOutline(x, y, w, h, t.rControl(), 1, t.edge());
        float ty = y + pad;
        for (String l : lines) {
            c.text(l, x + pad, ty, FontRole.CAPTION, t.ink());
            ty += lh;
        }
        if (!extra.isEmpty()) {
            ty += 6;
            for (String l : extra) {
                c.text(l, x + pad, ty, FontRole.CAPTION, t.muted());
                ty += lh;
            }
        }
    }

    public List<String> wrap(String text, FontRole role, float maxWidth) {
        List<String> out = new ArrayList<>();
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (c.textWidth(candidate, role) > maxWidth && !line.isEmpty()) {
                    out.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }
            out.add(line.toString());
        }
        return out;
    }

    // ---- shared controls ---------------------------------------------------------------------

    /** 1px ring-soft halo with a 2px offset around a white element. */
    public void halo(float x, float y, float w, float h, float r, float strength) {
        if (strength <= 0.01f) return;
        c.roundOutline(x - 3, y - 3, w + 6, h + 6, r + 3, 1, Colors.mulAlpha(t.ring(), strength));
    }

    /** Text button. Primary = ink fill with on-ink label; secondary = outlined. */
    public boolean button(Object id, float x, float y, float w, float h, String label, boolean primary) {
        boolean hov = hover(x, y, w, h);
        float r = t.rControl();
        if (primary) {
            c.roundRect(x, y, w, h, r, hov ? t.inkHover() : t.ink());
            halo(x, y, w, h, r, 1);
        } else {
            if (hov) c.roundRect(x, y, w, h, r, t.raised());
            c.roundOutline(x, y, w, h, r, 1, hov ? t.edgeStrong() : t.edge());
        }
        float tw = c.textWidth(label, FontRole.LABEL);
        c.text(label, x + (w - tw) / 2, y + (h - c.lineHeight(FontRole.LABEL)) / 2, FontRole.LABEL, primary ? t.onInk() : t.ink());
        return click(x, y, w, h, 0);
    }

    /** Pill switch. Returns true if clicked. {@code blocked} draws it disabled and ignores clicks. */
    public boolean toggle(Object id, float x, float y, boolean on, boolean blocked) {
        float w = 28, h = 16;
        boolean hov = !blocked && hover(x - 4, y - 4, w + 8, h + 8);
        float k = animate(id, on ? 1 : 0);
        c.pushAlpha(blocked ? 0.4f : 1f);
        if (k > 0.01f) {
            c.roundRect(x, y, w, h, h / 2, Colors.mulAlpha(hov ? t.inkHover() : t.ink(), k));
            halo(x, y, w, h, h / 2, k);
        }
        c.roundOutline(x, y, w, h, h / 2, 1, Colors.mulAlpha(hov ? t.ink() : t.edgeStrong(), 1 - k));
        float knob = 10;
        float kx = x + 3 + (w - knob - 6) * k;
        c.roundRect(kx, y + 3, knob, knob, knob / 2, Colors.lerp(t.muted(), t.onInk(), k));
        c.popAlpha();
        return !blocked && click(x - 4, y - 4, w + 8, h + 8, 0);
    }

    /**
     * Single-line text input. Returns the live text; {@link #submitted(Object)} reports Enter.
     * Surface fill, border-strong outline, ink-subtle placeholder, 2px ink focus ring.
     */
    public String textField(Object id, float x, float y, float w, float h, String value, String placeholder, int maxLength) {
        TextBuffer buf = texts.computeIfAbsent(id, k -> new TextBuffer());
        boolean isFocused = focused(id);
        if (!isFocused) buf.sync(value);
        boolean hov = hover(x, y, w, h);
        if (click(x, y, w, h, 0)) {
            setFocus(id);
            isFocused = true;
            buf.caret = buf.text.length();
        } else if (isFocused && pressedAnywhere(0) && !inside(x, y, w, h)) {
            setFocus(null);
            isFocused = false;
        }
        buf.submitted = false;
        if (isFocused) handleTextInput(buf, maxLength);

        float r = t.rControl();
        c.roundRect(x, y, w, h, r, t.surface());
        c.roundOutline(x, y, w, h, r, 1, hov || isFocused ? t.ink() : t.edgeStrong());
        if (isFocused) c.roundOutline(x - 2, y - 2, w + 4, h + 4, r + 2, 2, t.ink());

        float pad = t.padC() + 2;
        float ty = y + (h - c.lineHeight(FontRole.BODY)) / 2;
        pushClip(x + pad - 1, y, w - pad * 2 + 2, h);
        String s = buf.text.toString();
        float caretX = c.textWidth(s.substring(0, Math.min(buf.caret, s.length())), FontRole.BODY);
        float shift = Math.max(0, caretX - (w - pad * 2 - 2));
        if (s.isEmpty() && !isFocused) {
            c.text(placeholder, x + pad, ty, FontRole.BODY, t.subtle());
        } else if (s.isEmpty()) {
            c.text(placeholder, x + pad, ty, FontRole.BODY, Colors.mulAlpha(t.subtle(), 0.6f));
        }
        if (buf.selectAll && isFocused && !s.isEmpty()) {
            c.rect(x + pad - shift, ty, c.textWidth(s, FontRole.BODY), c.lineHeight(FontRole.BODY), Colors.mulAlpha(t.ink(), 0.25f));
        }
        c.text(s, x + pad - shift, ty, FontRole.BODY, t.ink());
        if (isFocused && (now() / 530) % 2 == 0) {
            c.rect(x + pad - shift + caretX, ty + 1, 1, c.lineHeight(FontRole.BODY) - 2, t.ink());
        }
        popClip();
        return buf.text.toString();
    }

    public boolean submitted(Object id) {
        TextBuffer b = texts.get(id);
        return b != null && b.submitted;
    }

    public void clearText(Object id) {
        TextBuffer b = texts.get(id);
        if (b != null) {
            b.text.setLength(0);
            b.caret = 0;
        }
    }

    private void handleTextInput(TextBuffer buf, int maxLength) {
        for (KeyPress k : keys) {
            if (k.consumed) continue;
            k.consumed = true;
            switch (k.key) {
                case Keys.BACKSPACE -> {
                    if (buf.selectAll) buf.clear();
                    else if (k.ctrl()) {
                        int i = buf.caret;
                        while (i > 0 && buf.text.charAt(i - 1) == ' ') i--;
                        while (i > 0 && buf.text.charAt(i - 1) != ' ') i--;
                        buf.text.delete(i, buf.caret);
                        buf.caret = i;
                    } else if (buf.caret > 0) {
                        buf.text.deleteCharAt(buf.caret - 1);
                        buf.caret--;
                    }
                }
                case Keys.DELETE -> {
                    if (buf.selectAll) buf.clear();
                    else if (buf.caret < buf.text.length()) buf.text.deleteCharAt(buf.caret);
                }
                case Keys.LEFT -> buf.caret = Math.max(0, buf.caret - 1);
                case Keys.RIGHT -> buf.caret = Math.min(buf.text.length(), buf.caret + 1);
                case Keys.HOME -> buf.caret = 0;
                case Keys.END -> buf.caret = buf.text.length();
                case Keys.ENTER, Keys.KP_ENTER -> buf.submitted = true;
                case Keys.ESCAPE, Keys.TAB -> setFocus(null);
                case Keys.A -> {
                    if (k.ctrl()) buf.selectAll = true;
                }
                case Keys.V -> {
                    if (k.ctrl()) {
                        String clip = core.platform().clipboard();
                        if (clip != null) buf.insert(clip.replace('\n', ' '), maxLength);
                    }
                }
                case Keys.C -> {
                    if (k.ctrl()) core.platform().setClipboard(buf.text.toString());
                }
                default -> {
                    // Printable keys arrive as typed characters; everything is consumed while focused.
                }
            }
            if (k.key == Keys.LEFT || k.key == Keys.RIGHT || k.key == Keys.HOME || k.key == Keys.END) buf.selectAll = false;
        }
        String s = takeTyped();
        if (!s.isEmpty()) buf.insert(s, maxLength);
    }

    private static final class TextBuffer {
        final StringBuilder text = new StringBuilder();
        int caret;
        boolean submitted;
        boolean selectAll;
        String lastSynced;

        void sync(String v) {
            if (v == null) v = "";
            if (!v.equals(lastSynced)) {
                text.setLength(0);
                text.append(v);
                caret = text.length();
                lastSynced = v;
            }
        }

        void clear() {
            text.setLength(0);
            caret = 0;
            selectAll = false;
        }

        void insert(String s, int maxLength) {
            if (selectAll) clear();
            int room = maxLength - text.length();
            if (room <= 0) return;
            if (s.length() > room) s = s.substring(0, room);
            text.insert(caret, s);
            caret += s.length();
        }
    }
}
