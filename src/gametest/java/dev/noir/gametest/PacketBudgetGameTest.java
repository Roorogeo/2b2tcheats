package dev.noir.gametest;

import dev.noir.NoirClient;
import dev.noir.NoirCore;
import dev.noir.module.Category;
import dev.noir.module.Module;
import dev.noir.network.PacketAudit;
import dev.noir.network.PacketKind;
import dev.noir.network.PacketLog;
import dev.noir.network.PacketManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The in-game packet test. In a real singleplayer world it records 200 idle ticks, then
 * enables each module alone for 200 ticks (with a few NoAI mobs around for combat
 * modules) and fails if any tick carries more packets than a vanilla client could send,
 * if a read-only module put anything on the wire, or if the PacketManager had to block
 * anything. Run with {@code ./gradlew runClientGameTest}.
 */
public final class PacketBudgetGameTest implements FabricClientGameTest {
    private static final int TICKS = 200;

    @Override
    public void runTest(ClientGameTestContext context) {
        NoirCore core = NoirClient.core();
        List<String> failures = new ArrayList<>();
        try (TestSingleplayerContext sp = context.worldBuilder().create()) {
            sp.getConnection().waitForChunksRender();

            // Idle baseline: vanilla only sends the movement heartbeat.
            List<PacketLog.Entry> idle = record(context, core, null);
            failures.addAll(PacketAudit.audit(idle, null));
            long idleMoves = idle.stream().filter(e -> e.kind() == PacketKind.MOVEMENT).count();
            if (idleMoves > TICKS / 20 + 2) failures.add("idle: " + idleMoves + " movement packets in " + TICKS + " ticks (vanilla heartbeat is 1 per 20)");

            for (Module m : core.modules().all()) {
                if (m.category() == Category.COMBAT) {
                    sp.getServer().runCommand("/kill @e[type=!player]");
                    sp.getServer().runCommand("/execute at @p run summon minecraft:zombie ~2 ~ ~ {NoAI:1b,PersistenceRequired:1b}");
                    sp.getServer().runCommand("/execute at @p run summon minecraft:zombie ~-2 ~ ~1 {NoAI:1b,PersistenceRequired:1b}");
                }
                List<PacketLog.Entry> entries = record(context, core, m);
                List<PacketManager.Violation> violations = context.computeOnClient(mc -> new ArrayList<>(core.packets().violations()));
                for (String p : PacketAudit.audit(entries, m)) failures.add(m.name() + ": " + p);
                for (PacketManager.Violation v : violations) failures.add(m.name() + ": blocked " + v.type() + " (" + v.reason() + ")");
            }
        }
        if (!failures.isEmpty()) {
            throw new AssertionError("Packet budget test failed:\n  " + String.join("\n  ", failures));
        }
    }

    private static List<PacketLog.Entry> record(ClientGameTestContext context, NoirCore core, Module module) {
        context.runOnClient(mc -> {
            core.packets().log().clear();
            core.packets().violations().clear();
            if (module != null) module.setEnabled(true);
        });
        context.waitTicks(TICKS);
        return context.computeOnClient(mc -> {
            if (module != null) module.setEnabled(false);
            return core.packets().log().snapshot();
        });
    }
}
