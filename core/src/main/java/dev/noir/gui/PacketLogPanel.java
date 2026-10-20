package dev.noir.gui;

import dev.noir.module.Module;
import dev.noir.network.PacketKind;
import dev.noir.network.PacketLog;
import dev.noir.util.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Every outgoing packet, grouped by tick, with the module that requested it.
 * Filter by source (click/right-click the chip to cycle), by kind, and by type text.
 */
final class PacketLogPanel extends Panel {
    private static final float ENTRY = 20;
    private static final int MAX_ROWS = 160;

    private final ClickGui gui;
    private int sourceIndex;
    private int kindIndex;
    private String typeFilter = "";

    PacketLogPanel(ClickGui gui) {
        super("packetlog", "Packet Log", 360, true);
        this.gui = gui;
    }

    @Override
    protected String subtitle() {
        PacketLog log = gui.core.packets().log();
        return log.paused() ? "paused" : "tick " + gui.core.packets().tick();
    }

    private List<String> sources() {
        List<String> s = new ArrayList<>();
        s.add("All sources");
        s.add("vanilla");
        for (Module m : gui.core.modules().all()) if (!m.isReadOnly()) s.add(m.name());
        return s;
    }

    @Override
    protected float content(Ui ui, float x, float y, float w) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        PacketLog log = gui.core.packets().log();
        float pad = t.padC();
        float cy = y;

        List<String> sources = sources();
        sourceIndex = Math.floorMod(sourceIndex, sources.size());
        PacketKind[] kinds = PacketKind.values();
        kindIndex = Math.floorMod(kindIndex, kinds.length + 1);
        String sourceLabel = sources.get(sourceIndex);
        String kindLabel = kindIndex == 0 ? "All kinds" : kinds[kindIndex - 1].toString();

        float chipX = x + pad;
        float sw = chip(ui, "src", chipX, cy, sourceLabel);
        if (ui.click(chipX, cy, sw, 22, 0)) sourceIndex++;
        if (ui.click(chipX, cy, sw, 22, 1)) sourceIndex--;
        ui.tooltip("plog:src", chipX, cy, sw, 22, "Filter by the module that requested the packet.", "Click: next · Right-click: previous");
        float kx = chipX + sw + t.gap() * 2;
        float kw = chip(ui, "kind", kx, cy, kindLabel);
        if (ui.click(kx, cy, kw, 22, 0)) kindIndex++;
        if (ui.click(kx, cy, kw, 22, 1)) kindIndex--;
        ui.tooltip("plog:kind", kx, cy, kw, 22, "Filter by packet kind.", "Click: next · Right-click: previous");
        cy += 30;

        typeFilter = ui.textField("plog:type", x + pad, cy, w - pad * 2, 26, typeFilter, "Filter by packet type", 48);
        cy += 34;

        float bw = (w - pad * 2 - t.gap() * 2) / 2;
        if (ui.button("plog:pause", x + pad, cy, bw, 24, log.paused() ? "Resume" : "Pause", log.paused())) log.setPaused(!log.paused());
        if (ui.button("plog:clear", x + pad + bw + t.gap() * 2, cy, bw, 24, "Clear", false)) log.clear();
        cy += 34;

        String src = sourceIndex == 0 ? null : sourceLabel;
        PacketKind kind = kindIndex == 0 ? null : kinds[kindIndex - 1];
        String tf = typeFilter.trim().toLowerCase(Locale.ROOT);
        List<PacketLog.Entry> entries = log.snapshot();
        int shown = 0;
        long currentTick = Long.MIN_VALUE;
        for (int i = entries.size() - 1; i >= 0 && shown < MAX_ROWS; i--) {
            PacketLog.Entry e = entries.get(i);
            if (src != null && !e.source().equals(src) && !e.source().startsWith(src + " ") && !e.source().contains(": " + src + ")")) continue;
            if (kind != null && e.kind() != kind) continue;
            if (!tf.isEmpty() && !e.type().toLowerCase(Locale.ROOT).contains(tf)) continue;
            if (e.tick() != currentTick) {
                currentTick = e.tick();
                cy += 4;
                c.text("TICK " + e.tick(), x + pad, cy, FontRole.LABEL, t.subtle());
                cy += 18;
            }
            boolean hov = ui.hover(x, cy, w, ENTRY);
            if (hov) c.roundRect(x, cy, w, ENTRY, t.rChip(), t.raised());
            String type = shortType(e.type());
            float srcW = c.textWidth(e.source(), FontRole.CAPTION);
            float typeMax = w - pad * 2 - Math.min(srcW, w * 0.45f) - (e.blocked() ? 70 : 12);
            c.text(c.ellipsize(type, FontRole.BODY, typeMax), x + pad, cy + (ENTRY - c.lineHeight(FontRole.BODY)) / 2, FontRole.BODY,
                e.blocked() ? t.muted() : t.ink());
            float rx = x + w - pad;
            String shownSrc = c.ellipsize(e.source(), FontRole.CAPTION, w * 0.45f);
            rx -= c.textWidth(shownSrc, FontRole.CAPTION);
            c.text(shownSrc, rx, cy + (ENTRY - c.lineHeight(FontRole.CAPTION)) / 2, FontRole.CAPTION, t.muted());
            if (e.blocked()) {
                String b = "BLOCKED";
                float bw2 = c.textWidth(b, FontRole.LABEL) + 10;
                float bx = rx - bw2 - 6;
                c.roundRect(bx, cy + 2, bw2, ENTRY - 4, t.rChip(), t.ink());
                c.text(b, bx + 5, cy + (ENTRY - c.lineHeight(FontRole.LABEL)) / 2, FontRole.LABEL, t.onInk());
            }
            ui.tooltip(e, x, cy, w, ENTRY, e.type() + " · " + e.kind(), e.blocked() ? "Blocked: " + e.reason() : "Requested by " + e.source());
            cy += ENTRY;
            shown++;
        }
        if (shown == 0) {
            c.text(entries.isEmpty() ? "No packets yet" : "Nothing matches the filters", x + pad, cy + 4, FontRole.CAPTION, t.subtle());
            cy += 26;
        }
        return cy - y;
    }

    private float chip(Ui ui, String id, float x, float y, String label) {
        Canvas c = ui.canvas();
        Theme t = ui.t;
        float w = c.textWidth(label, FontRole.LABEL) + t.padC() * 2;
        boolean hov = ui.hover(x, y, w, 22);
        c.roundRect(x, y, w, 22, t.rChip(), hov ? t.raised() : t.surface());
        c.roundOutline(x, y, w, 22, t.rChip(), 1, hov ? t.edgeStrong() : t.edge());
        c.text(label, x + t.padC(), y + (22 - c.lineHeight(FontRole.LABEL)) / 2, FontRole.LABEL, Colors.mulAlpha(t.ink(), hov ? 1 : 0.9f));
        return w;
    }

    /** "ServerboundMovePlayerPacket$PosRot" -> "MovePlayer.PosRot". */
    static String shortType(String type) {
        String s = type.replace('$', '.');
        int dot = s.lastIndexOf('.', s.indexOf("Packet") > 0 ? s.indexOf("Packet") : s.length());
        if (dot >= 0 && s.substring(0, dot).contains(".")) s = s.substring(dot + 1);
        return s.replace("Serverbound", "").replace("Packet", "");
    }
}
