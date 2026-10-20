package dev.noir.network;

import dev.noir.NoirCore;
import dev.noir.module.Module;
import dev.noir.sim.SimulatedClient;
import dev.noir.sim.TestModules;
import dev.noir.sim.VanillaBudget;
import dev.noir.testutil.FakePlatform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The packet test: runs each module for 200 ticks against a client that behaves like
 * vanilla on the wire, and fails if a tick carries more packets than vanilla could
 * send, if packets are out of vanilla order, or if a read-only module sends anything.
 */
class PacketBudgetTest {
    private static final int TICKS = 200;

    @TempDir
    Path dir;

    private NoirCore core;
    private SimulatedClient sim;

    @BeforeEach
    void setUp() {
        core = new NoirCore(new FakePlatform(dir));
        sim = new SimulatedClient(core);
        sim.targets.add(new SimulatedClient.Target(1, 1.5, 64, 2.0));
        // Walk back and forth so movement packets aren't only heartbeats.
        sim.playerInput = c -> c.x += (c.tick / 25) % 2 == 0 ? 0.05 : -0.05;
    }

    static Stream<Arguments> modules() {
        return Stream.of(
            Arguments.of("Aura", (Function<SimulatedClient, Module>) TestModules.Aura::new),
            Arguments.of("RotationSpam", (Function<SimulatedClient, Module>) s -> new TestModules.RotationSpam()),
            Arguments.of("ClickSpam", (Function<SimulatedClient, Module>) s -> new TestModules.ClickSpam()),
            Arguments.of("UseSpam", (Function<SimulatedClient, Module>) s -> new TestModules.UseSpam()),
            Arguments.of("Observer", (Function<SimulatedClient, Module>) s -> new TestModules.Observer()));
    }

    private Module start(Function<SimulatedClient, Module> factory) {
        Module m = factory.apply(sim);
        core.register(m);
        core.start();
        m.setEnabled(true);
        return m;
    }

    @ParameterizedTest(name = "{0} stays within vanilla budgets for 200 ticks")
    @MethodSource("modules")
    void eachModuleStaysWithinVanillaBudgets(String name, Function<SimulatedClient, Module> factory) {
        Module m = start(factory);
        sim.run(TICKS);

        List<String> problems = VanillaBudget.check(sim.wire);
        assertTrue(problems.isEmpty(), name + " exceeded vanilla: " + problems);
        assertTrue(core.packets().violations().isEmpty(), name + " violations: " + core.packets().violations());
        if (m.isReadOnly()) {
            long sent = sim.wire.stream().filter(w -> w.source().equals(m.name())).count();
            assertEquals(0, sent, "read-only module " + name + " sent packets");
        }
    }

    @Test
    void allModulesTogetherStayWithinVanillaBudgets() {
        core.register(new TestModules.Aura(sim), new TestModules.RotationSpam(), new TestModules.ClickSpam(),
            new TestModules.UseSpam(), new TestModules.Observer());
        core.start();
        core.modules().all().forEach(m -> m.setEnabled(true));
        sim.run(TICKS);
        assertEquals(List.of(), VanillaBudget.check(sim.wire));
        assertTrue(core.packets().violations().isEmpty(), core.packets().violations().toString());
    }

    @Test
    void idleClientOnlySendsTheVanillaHeartbeat() {
        sim.playerInput = c -> {};
        core.start();
        sim.run(TICKS);
        assertEquals(TICKS / 20, sim.wire.size(), "expected one position heartbeat every 20 ticks: " + sim.wire);
        assertTrue(sim.wire.stream().allMatch(w -> w.type().endsWith("$Pos")));
        for (SimulatedClient.Wire w : sim.wire) assertEquals(0, w.tick() % 20);
    }

    @Test
    void readOnlyModuleCannotPutAnythingOnTheWire() {
        Module rogue = start(TestModules.RogueRender::new);
        sim.playerInput = c -> {};
        sim.run(TICKS);
        assertEquals(0, sim.wire.stream().filter(w -> w.source().equals(rogue.name())).count());
        assertTrue(sim.wire.stream().noneMatch(w -> w.kind() == PacketKind.INTERACT || w.kind() == PacketKind.SWING));
        assertTrue(sim.wire.stream().noneMatch(w -> w.type().contains("Rot")), "read-only module rotated the player");
        assertFalse(core.packets().violations().isEmpty(), "attempts should be recorded");
    }

    @Test
    void directSendsAreBlocked() {
        Module direct = start(TestModules.DirectSender::new);
        sim.run(TICKS);
        assertEquals(0, sim.wire.stream().filter(w -> w.source().equals(direct.name())).count());
        assertTrue(sim.wire.stream().noneMatch(w -> w.kind() == PacketKind.CUSTOM_PAYLOAD || w.kind() == PacketKind.INTERACT));
        assertEquals(List.of(), VanillaBudget.check(sim.wire));
        assertEquals(TICKS * 3, core.packets().violations().size());
        assertTrue(core.packets().log().snapshot().stream().anyMatch(PacketLog.Entry::blocked));
    }

    @Test
    void attacksOnlyAfterTheServerRotationAims() {
        sim.targets.clear();
        sim.targets.add(new SimulatedClient.Target(1, 0, 64, -2.5)); // behind the player (yaw 180)
        sim.playerInput = c -> {};
        start(TestModules.Aura::new);
        sim.run(40);
        long firstAttack = sim.wire.stream().filter(w -> w.kind() == PacketKind.INTERACT).mapToLong(SimulatedClient.Wire::tick).min().orElse(-1);
        long firstRot = sim.wire.stream().filter(w -> w.type().contains("Rot")).mapToLong(SimulatedClient.Wire::tick).min().orElse(-1);
        assertTrue(firstRot > 0, "no rotation sent");
        assertTrue(firstAttack > firstRot, "attacked at tick " + firstAttack + " before rotating at " + firstRot);
        assertTrue(firstAttack >= 6, "a 180° turn at 35°/tick can't aim within " + firstAttack + " ticks");
        assertEquals(List.of(), VanillaBudget.check(sim.wire));
    }

    @Test
    void silentRotationSyncsBackToTheRealView() {
        sim.playerInput = c -> {};
        Module aura = start(TestModules.Aura::new);
        sim.run(40);
        aura.setEnabled(false);
        sim.run(60);
        SimulatedClient.Wire lastRot = null;
        for (SimulatedClient.Wire w : sim.wire) if (w.type().contains("Rot")) lastRot = w;
        assertEquals(sim.viewYaw, lastRot.yaw(), 0f);
        assertEquals(sim.viewPitch, lastRot.pitch(), 0f);
        long rotsInLast20 = sim.wire.stream().filter(w -> w.tick() > 80 && w.type().contains("Rot")).count();
        assertEquals(0, rotsInLast20, "rotation packets kept coming after sync");
        assertFalse(core.packets().rotations().isOverriding());
    }

    @Test
    void rotationDeltasAreMouseSteps() {
        sim.playerInput = c -> {};
        start(s -> new TestModules.RotationSpam());
        sim.run(TICKS);
        float step = sim.rotationStep();
        float prevYaw = 0, prevPitch = 0;
        for (SimulatedClient.Wire w : sim.wire) {
            if (!w.type().contains("Rot")) continue;
            float dy = w.yaw() - prevYaw, dp = w.pitch() - prevPitch;
            boolean clampedPitch = Math.abs(w.pitch()) == 90f;
            assertTrue(Math.abs(dy / step - Math.round(dy / step)) < 0.02, "yaw delta " + dy + " not a multiple of " + step);
            if (!clampedPitch) assertTrue(Math.abs(dp / step - Math.round(dp / step)) < 0.02, "pitch delta " + dp);
            prevYaw = w.yaw();
            prevPitch = w.pitch();
        }
    }

    @Test
    void noInventoryClicksWhileAClientOnlyScreenIsOpen() {
        start(s -> new TestModules.ClickSpam());
        sim.clientScreenOpen = true;
        sim.run(50);
        assertEquals(0, sim.wire.stream().filter(w -> w.kind() == PacketKind.CONTAINER).count());
    }

    @Test
    void inventoryBudgetRespectsStrictMode() {
        start(s -> new TestModules.ClickSpam());
        core.client().strictMode.set(false);
        core.client().inventoryActionsPerTick.set(3.0);
        sim.run(10);
        long perTickOff = sim.wire.stream().filter(w -> w.kind() == PacketKind.CONTAINER && w.tick() == 5).count();
        assertEquals(3, perTickOff);

        sim.wire.clear();
        core.client().strictMode.set(true);
        sim.run(10);
        long perTickOn = sim.wire.stream().filter(w -> w.kind() == PacketKind.CONTAINER && w.tick() == 15).count();
        assertEquals(1, perTickOn, "Strict mode must cap inventory actions at 1/tick");
        assertEquals(List.of(), VanillaBudget.check(sim.wire));
    }

    @Test
    void noAttacksWhileUsingAnItem() {
        start(TestModules.Aura::new);
        sim.usingItem = true;
        sim.run(60);
        assertEquals(0, sim.wire.stream().filter(w -> w.kind() == PacketKind.INTERACT).count());
    }

    @Test
    void packetLogAttributesRotationsAndAttacks() {
        sim.playerInput = c -> {};
        start(TestModules.Aura::new);
        sim.run(30);
        List<PacketLog.Entry> log = core.packets().log().snapshot();
        assertTrue(log.stream().anyMatch(e -> e.kind() == PacketKind.INTERACT && e.source().equals("Test Aura")));
        assertTrue(log.stream().anyMatch(e -> e.kind() == PacketKind.MOVEMENT && e.source().contains("rotation: Test Aura")));
    }

    @Test
    void disablingAModuleDropsItsQueuedActions() {
        Module aura = start(TestModules.Aura::new);
        sim.run(20);
        sim.wire.clear();
        aura.setEnabled(false);
        sim.run(20);
        assertEquals(0, sim.wire.stream().filter(w -> w.kind() == PacketKind.INTERACT).count());
    }
}
