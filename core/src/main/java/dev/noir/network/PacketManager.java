package dev.noir.network;

import dev.noir.NoirCore;
import dev.noir.module.Module;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The single gateway between modules and the wire.
 *
 * <p>Tick contract (driven by the platform layer, matching vanilla's tick order):
 * <ol>
 *   <li>{@link #beginTick} — then modules run their TickPre handlers and queue requests.</li>
 *   <li>{@link #flushInteractions} — at vanilla's input-handling point. Queued actions run
 *       through the {@link PacketSink} (i.e. vanilla code) in vanilla order, within per-tick budgets.</li>
 *   <li>{@link #resolveRotation} — when vanilla builds the movement packet.</li>
 *   <li>{@link #endTick}.</li>
 * </ol>
 *
 * <p>Every outgoing packet is reported to {@link #onOutgoing}, which attributes it to the
 * module (if any) whose code caused it, logs it, and blocks it if a module caused it
 * outside this manager, a read-only module caused it, or it is a kind modules may never send.
 */
public final class PacketManager {
    public record Violation(long tick, String module, String type, PacketKind kind, String reason) {}

    private record Pending(Module module, Action action, int priority, long seq) {}

    private static final Comparator<Pending> ORDER = Comparator
        .comparing((Pending p) -> p.action.slot())
        .thenComparing(Comparator.comparingInt((Pending p) -> p.priority).reversed())
        .thenComparingLong(p -> p.seq);

    private final NoirCore core;
    private final RotationManager rotations = new RotationManager();
    private final PacketLog log;
    private final List<Pending> pending = new ArrayList<>();
    private final Map<Module, ActionClient> clients = new IdentityHashMap<>();
    private final List<Violation> violations = new CopyOnWriteArrayList<>();
    private final Map<Action.Slot, Integer> executedThisTick = new EnumMap<>(Action.Slot.class);
    private PacketSink sink;
    private long tick;
    private long seq;
    private int movementPacketsThisTick;
    private Module context;
    private boolean viaManager;

    public PacketManager(NoirCore core) {
        this.core = core;
        this.log = new PacketLog(core.client().packetLogCapacity.getInt());
        core.client().packetLogCapacity.onChange(v -> log.setCapacity(v.intValue()));
    }

    public void setSink(PacketSink sink) {
        this.sink = sink;
    }

    public RotationManager rotations() {
        return rotations;
    }

    public PacketLog log() {
        return log;
    }

    public List<Violation> violations() {
        return violations;
    }

    public long tick() {
        return tick;
    }

    public ActionClient client(Module module) {
        return clients.computeIfAbsent(module, m -> m.isReadOnly() ? new ReadOnlyClient(m) : new ModuleClient(m));
    }

    // ---- tick lifecycle -------------------------------------------------------------

    public void beginTick(long tick) {
        this.tick = tick;
        movementPacketsThisTick = 0;
        executedThisTick.clear();
    }

    /** Runs queued actions at vanilla's input point, in vanilla order, within budgets. */
    public void flushInteractions() {
        if (pending.isEmpty()) return;
        List<Pending> queue = new ArrayList<>(pending);
        pending.clear();
        if (sink == null || !sink.ready()) return;
        queue.sort(ORDER);
        for (Pending p : queue) {
            if (!p.module.isEnabled()) continue;
            Action.Slot slot = p.action.slot();
            if (executedThisTick.getOrDefault(slot, 0) >= budget(slot)) continue;
            if (execute(p)) executedThisTick.merge(slot, 1, Integer::sum);
        }
    }

    /** Decides this tick's movement-packet rotation. Call exactly once per movement packet decision. */
    public float[] resolveRotation(float realYaw, float realPitch) {
        float step = core.client().mouseStepRotations.get() && sink != null ? sink.rotationStep() : 0f;
        return rotations.resolve(realYaw, realPitch, step, core.client().rotationReturnSpeed.getFloat());
    }

    public void endTick() {
        // Requests are per-tick intents; anything not executed this tick is dropped.
        pending.clear();
    }

    public int budget(Action.Slot slot) {
        return slot == Action.Slot.INVENTORY ? core.client().inventoryActionsPerTick.getInt() : 1;
    }

    private boolean execute(Pending p) {
        Action a = p.action;
        return switch (a) {
            case Action.SelectHotbar s -> {
                if (sink.selectedHotbar() == s.hotbarSlot()) yield false;
                runVia(p.module, () -> sink.selectHotbar(s.hotbarSlot()));
                yield true;
            }
            case Action.ContainerClick c -> {
                if (!sink.inventoryAvailable(c.containerId())) yield false;
                runVia(p.module, () -> sink.containerClick(c.containerId(), c.slotIndex(), c.button(), c.type()));
                yield true;
            }
            case Action.SwapHands s -> {
                runVia(p.module, sink::swapHands);
                yield true;
            }
            case Action.Attack at -> {
                if (sink.isUsingItem() || !rotations.initialized()) yield false;
                if (!sink.validateAttack(at.target(), at.throughWalls())) yield false;
                if (!sink.aimsAt(at.target(), rotations.serverYaw(), rotations.serverPitch())) yield false;
                runVia(p.module, () -> sink.attack(at.target()));
                yield true;
            }
            case Action.UseItemOn u -> {
                if (!rotations.initialized() || !sink.validateUseOn(u.hit(), u.throughWalls())) yield false;
                if (!sink.aimsAtBlock(u.hit(), rotations.serverYaw(), rotations.serverPitch())) yield false;
                runVia(p.module, () -> sink.useItemOn(u.hand(), u.hit()));
                yield true;
            }
            case Action.UseItem u -> {
                runVia(p.module, () -> sink.useItem(u.hand()));
                yield true;
            }
        };
    }

    // ---- attribution ------------------------------------------------------------------

    /** Runs module code (event handlers, enable/disable) under that module's attribution. */
    public void runAs(Module module, Runnable r) {
        Module prevCtx = context;
        boolean prevVia = viaManager;
        context = module;
        viaManager = false;
        try {
            r.run();
        } finally {
            context = prevCtx;
            viaManager = prevVia;
        }
    }

    private void runVia(Module module, Runnable r) {
        Module prevCtx = context;
        boolean prevVia = viaManager;
        context = module;
        viaManager = true;
        try {
            r.run();
        } finally {
            context = prevCtx;
            viaManager = prevVia;
        }
    }

    /** The module currently attributed for outgoing packets, or null for vanilla. */
    public Module currentSource() {
        return context;
    }

    /**
     * Reports an outgoing packet. Returns false if it must be dropped.
     * Vanilla-originated packets are never blocked; only module-caused ones can be.
     */
    public boolean onOutgoing(String type, PacketKind kind) {
        Module m = context;
        boolean via = viaManager;
        String source;
        String reason = null;
        if (kind == PacketKind.MOVEMENT) {
            movementPacketsThisTick++;
            Module rot = rotations.owner();
            source = m != null ? m.name() : rot != null ? "vanilla (rotation: " + rot.name() + ")" : "vanilla";
            if (m != null) reason = "movement packets are only built by vanilla";
        } else {
            source = m == null ? "vanilla" : via ? m.name() : m.name() + " (direct)";
        }
        if (m != null && reason == null) {
            if (m.isReadOnly()) reason = "read-only module";
            else if (!via) reason = "sent outside the PacketManager";
            else if (kind == PacketKind.CUSTOM_PAYLOAD) reason = "modules never send custom payloads";
            else if (kind == PacketKind.CHAT) reason = "modules never send chat";
        }
        boolean blocked = reason != null;
        if (blocked) violations.add(new Violation(tick, m.name(), type, kind, reason));
        log.add(new PacketLog.Entry(tick, type, kind, source, blocked, reason));
        return !blocked;
    }

    public int movementPacketsThisTick() {
        return movementPacketsThisTick;
    }

    /** Drops everything a module had queued; called when it is disabled. */
    public void release(Module module) {
        pending.removeIf(p -> p.module == module);
        rotations.release(module);
    }

    private void submit(Module module, Action action, int priority) {
        if (!module.isEnabled()) return;
        pending.add(new Pending(module, action, priority, seq++));
    }

    private final class ModuleClient implements ActionClient {
        private final Module module;

        ModuleClient(Module module) {
            this.module = module;
        }

        @Override public void rotate(float yaw, float pitch, float speed, int priority) {
            if (module.isEnabled()) rotations.request(module, yaw, pitch, speed, priority);
        }

        @Override public void attack(Object target, boolean throughWalls, int priority) {
            submit(module, new Action.Attack(target, throughWalls), priority);
        }

        @Override public void useItemOn(Hand hand, Object hit, boolean throughWalls, int priority) {
            submit(module, new Action.UseItemOn(hand, hit, throughWalls), priority);
        }

        @Override public void useItem(Hand hand, int priority) {
            submit(module, new Action.UseItem(hand), priority);
        }

        @Override public void click(int containerId, int slot, int button, ClickType type, int priority) {
            submit(module, new Action.ContainerClick(containerId, slot, button, type), priority);
        }

        @Override public void selectHotbar(int slot, int priority) {
            submit(module, new Action.SelectHotbar(slot), priority);
        }

        @Override public void swapHands(int priority) {
            submit(module, new Action.SwapHands(), priority);
        }

        @Override public float serverYaw() { return rotations.serverYaw(); }

        @Override public float serverPitch() { return rotations.serverPitch(); }
    }

    /** Handle given to Render/Base Hunting modules: every request is refused and recorded. */
    private final class ReadOnlyClient implements ActionClient {
        private final Module module;

        ReadOnlyClient(Module module) {
            this.module = module;
        }

        private void refuse(String what) {
            violations.add(new Violation(tick, module.name(), what, PacketKind.OTHER, "read-only module requested an action"));
        }

        @Override public void rotate(float yaw, float pitch, float speed, int priority) { refuse("rotate"); }
        @Override public void attack(Object target, boolean throughWalls, int priority) { refuse("attack"); }
        @Override public void useItemOn(Hand hand, Object hit, boolean throughWalls, int priority) { refuse("useItemOn"); }
        @Override public void useItem(Hand hand, int priority) { refuse("useItem"); }
        @Override public void click(int containerId, int slot, int button, ClickType type, int priority) { refuse("click"); }
        @Override public void selectHotbar(int slot, int priority) { refuse("selectHotbar"); }
        @Override public void swapHands(int priority) { refuse("swapHands"); }
        @Override public float serverYaw() { return rotations.serverYaw(); }
        @Override public float serverPitch() { return rotations.serverPitch(); }
    }
}
