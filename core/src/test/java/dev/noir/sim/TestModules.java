package dev.noir.sim;

import dev.noir.event.Events;
import dev.noir.module.Category;
import dev.noir.module.Module;
import dev.noir.network.ClickType;
import dev.noir.network.Hand;
import dev.noir.network.PacketKind;

import java.util.Random;

/**
 * Modules that push the PacketManager as hard as real modules could: every-tick
 * attacks, rotation spam with invalid angles, multiple clicks per tick, and some
 * that try to cheat the system (direct sends, read-only modules acting).
 */
public final class TestModules {
    private TestModules() {}

    /** Aura-like: rotates toward the nearest target and attacks every tick. */
    public static final class Aura extends Module {
        public final SimulatedClient sim;

        public Aura(SimulatedClient sim) {
            super("Test Aura", Category.COMBAT, "Rotates and attacks every tick.");
            this.sim = sim;
            on(Events.TickPre.class, e -> {
                SimulatedClient.Target best = null;
                for (SimulatedClient.Target t : sim.targets) if (best == null || sim.distanceTo(t) < sim.distanceTo(best)) best = t;
                if (best == null) return;
                float[] a = sim.anglesTo(best);
                actions().rotate(a[0], a[1], 35f, 10);
                actions().attack(best, false, 10);
            });
        }
    }

    /** Spams rotations, including invalid pitch and NaN, several per tick. */
    public static final class RotationSpam extends Module {
        private final Random random = new Random(7);

        public RotationSpam() {
            super("Test Rotation Spam", Category.COMBAT, "Requests several wild rotations per tick.");
            on(Events.TickPre.class, e -> {
                for (int i = 0; i < 4; i++) {
                    actions().rotate(random.nextFloat() * 720f - 360f, random.nextFloat() * 400f - 200f, 0f, i);
                }
                actions().rotate(Float.NaN, 10f, 0f, 99);
            });
        }
    }

    /** AutoTotem-like: asks for several container clicks and a hand swap every tick. */
    public static final class ClickSpam extends Module {
        public ClickSpam() {
            super("Test Click Spam", Category.COMBAT, "Requests 5 clicks and a swap per tick.");
            on(Events.TickPre.class, e -> {
                for (int i = 0; i < 5; i++) actions().click(0, 9 + i, 0, ClickType.PICKUP, 0);
                actions().swapHands(0);
            });
        }
    }

    /** Uses items and swaps hotbar slots every tick. */
    public static final class UseSpam extends Module {
        public UseSpam() {
            super("Test Use Spam", Category.MOVEMENT, "Uses on a block, uses in air and switches slots every tick.");
            on(Events.TickPre.class, e -> {
                actions().useItemOn(Hand.MAIN_HAND, "hit", false, 1);
                actions().useItem(Hand.OFF_HAND, 0);
                actions().selectHotbar((int) (e.tick() % 9), 0);
                actions().selectHotbar((int) ((e.tick() + 4) % 9), 0);
            });
        }
    }

    /** A well-behaved read-only module: reads state, never acts. */
    public static final class Observer extends Module {
        public int ticks;

        public Observer() {
            super("Test Observer", Category.RENDER, "Only reads.");
            on(Events.TickPre.class, e -> ticks++);
            on(Events.TickPost.class, e -> ticks++);
        }
    }

    /** A read-only module that misbehaves: tries to attack and to write to the connection. */
    public static final class RogueRender extends Module {
        private final SimulatedClient sim;

        public RogueRender(SimulatedClient sim) {
            super("Test Rogue Render", Category.RENDER, "Tries to send packets.");
            this.sim = sim;
            on(Events.TickPre.class, e -> {
                actions().attack(new SimulatedClient.Target(1, 0, 64, 1), true, 100);
                actions().rotate(90, 0, 0, 100);
                sim.rawSend("ServerboundSwingPacket", PacketKind.SWING);
            });
        }
    }

    /** A combat module that bypasses the PacketManager. */
    public static final class DirectSender extends Module {
        private final SimulatedClient sim;

        public DirectSender(SimulatedClient sim) {
            super("Test Direct Sender", Category.COMBAT, "Writes to the connection directly.");
            this.sim = sim;
            on(Events.TickPre.class, e -> {
                sim.rawSend("ServerboundInteractPacket", PacketKind.INTERACT);
                sim.rawSend("ServerboundMovePlayerPacket$Rot", PacketKind.MOVEMENT);
                sim.rawSend("ServerboundCustomPayloadPacket", PacketKind.CUSTOM_PAYLOAD);
            });
        }
    }
}
