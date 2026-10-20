package dev.noir.network;

import dev.noir.module.Category;
import dev.noir.module.Module;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketAuditTest {
    private static PacketLog.Entry e(long tick, PacketKind kind, String source, boolean blocked) {
        return new PacketLog.Entry(tick, kind.name(), kind, source, blocked, null);
    }

    @Test
    void flagsOverBudgetTicks() {
        List<String> p = PacketAudit.audit(List.of(
            e(1, PacketKind.MOVEMENT, "vanilla", false),
            e(1, PacketKind.MOVEMENT, "vanilla", false),
            e(2, PacketKind.INTERACT, "Aura", false)), null);
        assertEquals(1, p.size());
        assertTrue(p.get(0).contains("Movement"), p.get(0));
    }

    @Test
    void blockedEntriesDoNotCount() {
        assertTrue(PacketAudit.audit(List.of(
            e(1, PacketKind.INTERACT, "Aura", false),
            e(1, PacketKind.INTERACT, "Aura (direct)", true)), null).isEmpty());
    }

    @Test
    void readOnlyModulesMustBeSilent() {
        Module esp = new Module("ESP", Category.RENDER, "x") {};
        List<String> p = PacketAudit.audit(List.of(e(3, PacketKind.SWING, "ESP", false)), esp);
        assertEquals(1, p.size());
    }
}
