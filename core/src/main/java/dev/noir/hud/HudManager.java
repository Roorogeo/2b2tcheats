package dev.noir.hud;

import dev.noir.NoirCore;
import dev.noir.gui.Canvas;
import dev.noir.hud.elements.ArmorHud;
import dev.noir.hud.elements.ArrayListHud;
import dev.noir.hud.elements.CoordsHud;
import dev.noir.hud.elements.HitCounterHud;
import dev.noir.hud.elements.StatsHud;
import dev.noir.hud.elements.StrictHud;
import dev.noir.hud.elements.ToastHud;
import dev.noir.hud.elements.TotemHud;
import dev.noir.hud.elements.WatermarkHud;
import dev.noir.module.Module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HudManager {
    private final NoirCore core;
    private final List<HudElement> elements = new ArrayList<>();
    private final ArrayListHud arrayList;
    private final ToastHud toasts;
    private final Map<String, Integer> counters = Collections.synchronizedMap(new LinkedHashMap<>());

    public HudManager(NoirCore core) {
        this.core = core;
        add(new WatermarkHud());
        arrayList = add(new ArrayListHud());
        add(new CoordsHud());
        add(new StatsHud());
        add(new ArmorHud());
        add(new TotemHud());
        add(new HitCounterHud());
        add(new StrictHud());
        toasts = add(new ToastHud());
    }

    private <E extends HudElement> E add(E e) {
        e.bind(core);
        elements.add(e);
        return e;
    }

    public List<HudElement> elements() {
        return Collections.unmodifiableList(elements);
    }

    public HudElement find(String id) {
        for (HudElement e : elements) if (e.id().equalsIgnoreCase(id) || e.name().equalsIgnoreCase(id)) return e;
        return null;
    }

    /** Modules publish hit counts here (e.g. "SusChunk" -> 3); shown by the Hit Counter element. */
    public void publishCount(String name, int count) {
        counters.put(name, count);
    }

    public void clearCount(String name) {
        counters.remove(name);
    }

    public Map<String, Integer> counters() {
        synchronized (counters) {
            return new LinkedHashMap<>(counters);
        }
    }

    public void onModuleToggle(Module m, boolean on) {
        arrayList.onToggle(m, on);
        toasts.onToggle(m, on);
    }

    /** Screen-space bounds {x, y, w, h} of an element (scaled). */
    public float[] bounds(HudElement e, Canvas c, HudData data) {
        float[] size = e.size(c, data);
        float s = e.scale.getFloat();
        float w = size[0] * s, h = size[1] * s;
        Anchor a = e.anchor.get();
        float x = Anchor.place(a.fx, c.width(), w, e.offsetX.getFloat());
        float y = Anchor.place(a.fy, c.height(), h, e.offsetY.getFloat());
        return new float[] {x, y, w, h};
    }

    public void render(Canvas c, HudData data) {
        for (HudElement e : elements) {
            if (!e.enabled.get() || !e.hasContent(data)) continue;
            draw(e, c, data);
        }
    }

    void draw(HudElement e, Canvas c, HudData data) {
        float[] b = bounds(e, c, data);
        c.pushScale(e.scale.getFloat(), b[0], b[1]);
        e.render(c, data, b[0], b[1]);
        c.popScale();
    }
}
