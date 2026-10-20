package dev.noir.sim;

import dev.noir.NoirCore;
import dev.noir.event.Events;
import dev.noir.module.Module;
import dev.noir.network.ClickType;
import dev.noir.network.Hand;
import dev.noir.network.PacketKind;
import dev.noir.network.PacketSink;
import dev.noir.util.MathUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A headless stand-in for the game used by the packet tests. It drives the core in
 * vanilla's tick order and reproduces the vanilla client's packet behaviour:
 * <ul>
 *   <li>Input handling (attacks, uses, clicks) happens before the player tick.</li>
 *   <li>{@code LocalPlayer.sendPosition}: position sent when moved more than 2e-4 blocks
 *       or every 20 ticks (heartbeat); rotation sent when it changed; status-only packet when
 *       only on-ground changed; nothing otherwise.</li>
 *   <li>Attack = interact packet then main-hand swing; use-on-block = use packet then swing.</li>
 * </ul>
 * Everything the client "sends" goes through {@link dev.noir.network.PacketManager#onOutgoing}
 * exactly like the real connection hook, and lands in {@link #wire} only if allowed.
 */
public final class SimulatedClient implements PacketSink {
    public record Wire(long tick, String type, PacketKind kind, String source, float yaw, float pitch) {}

    public record Target(int id, double x, double y, double z) {}

    public final NoirCore core;
    public final List<Wire> wire = new ArrayList<>();
    public final List<Target> targets = new ArrayList<>();
    public long tick;

    public double x, y = 64, z;
    public float viewYaw, viewPitch;
    public boolean onGround = true;
    public boolean usingItem;
    public boolean clientScreenOpen;
    public int openContainerId;
    public float sensitivity = 0.5f;
    public Consumer<SimulatedClient> playerInput = c -> {};

    private double xLast, yLast, zLast;
    private float yRotLast, xRotLast;
    private int positionReminder;
    private boolean lastOnGround = true;
    private int selected;

    public SimulatedClient(NoirCore core) {
        this.core = core;
        core.packets().setSink(this);
        xLast = x;
        yLast = y;
        zLast = z;
    }

    /** One client tick in vanilla order. */
    public void tick() {
        tick++;
        core.packets().beginTick(tick);
        core.events().post(new Events.TickPre(tick));
        core.packets().flushInteractions();
        playerInput.accept(this);
        float[] rot = core.packets().resolveRotation(viewYaw, viewPitch);
        sendPosition(rot[0], rot[1]);
        core.events().post(new Events.TickPost(tick));
        core.packets().endTick();
    }

    public void run(int ticks) {
        for (int i = 0; i < ticks; i++) tick();
    }

    private void sendPosition(float yaw, float pitch) {
        double dx = x - xLast, dy = y - yLast, dz = z - zLast;
        double dYaw = yaw - yRotLast, dPitch = pitch - xRotLast;
        positionReminder++;
        boolean move = dx * dx + dy * dy + dz * dz > 2.0E-4 * 2.0E-4 || positionReminder >= 20;
        boolean rotate = dYaw != 0.0 || dPitch != 0.0;
        if (move && rotate) send("ServerboundMovePlayerPacket$PosRot", PacketKind.MOVEMENT, yaw, pitch);
        else if (move) send("ServerboundMovePlayerPacket$Pos", PacketKind.MOVEMENT, yaw, pitch);
        else if (rotate) send("ServerboundMovePlayerPacket$Rot", PacketKind.MOVEMENT, yaw, pitch);
        else if (lastOnGround != onGround) send("ServerboundMovePlayerPacket$StatusOnly", PacketKind.MOVEMENT, yaw, pitch);
        if (move) {
            xLast = x;
            yLast = y;
            zLast = z;
            positionReminder = 0;
        }
        if (rotate) {
            yRotLast = yaw;
            xRotLast = pitch;
        }
        lastOnGround = onGround;
    }

    private void send(String type, PacketKind kind) {
        send(type, kind, Float.NaN, Float.NaN);
    }

    private void send(String type, PacketKind kind, float yaw, float pitch) {
        if (!core.packets().onOutgoing(type, kind)) return;
        Module m = core.packets().currentSource();
        String source = m == null ? "vanilla" : m.name();
        if (kind == PacketKind.MOVEMENT && m == null && core.packets().rotations().owner() != null) {
            source = core.packets().rotations().owner().name();
        }
        wire.add(new Wire(tick, type, kind, source, yaw, pitch));
    }

    /** What a module would do if it bypassed the PacketManager and wrote to the connection. */
    public void rawSend(String type, PacketKind kind) {
        send(type, kind);
    }

    // ---- geometry ---------------------------------------------------------------------

    public float[] anglesTo(Target t) {
        double ex = x, ey = y + 1.62, ez = z;
        double tx = t.x - ex, ty = t.y + 0.9 - ey, tz = t.z - ez;
        double h = Math.sqrt(tx * tx + tz * tz);
        float yaw = (float) Math.toDegrees(Math.atan2(tz, tx)) - 90f;
        float pitch = (float) -Math.toDegrees(Math.atan2(ty, h));
        return new float[] {yaw, pitch};
    }

    public double distanceTo(Target t) {
        double dx = t.x - x, dy = t.y - y, dz = t.z - z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ---- PacketSink ---------------------------------------------------------------------

    @Override public boolean ready() { return true; }

    @Override public boolean isUsingItem() { return usingItem; }

    @Override
    public boolean validateAttack(Object target, boolean throughWalls) {
        return !clientScreenOpen && target instanceof Target t && distanceTo(t) <= 3.0;
    }

    @Override
    public boolean aimsAt(Object target, float yaw, float pitch) {
        if (!(target instanceof Target t)) return false;
        float[] need = anglesTo(t);
        return Math.abs(MathUtil.wrapDegrees(need[0] - yaw)) < 8f && Math.abs(need[1] - pitch) < 12f;
    }

    @Override
    public void attack(Object target) {
        send("ServerboundInteractPacket", PacketKind.INTERACT);
        send("ServerboundSwingPacket", PacketKind.SWING);
    }

    @Override
    public boolean validateUseOn(Object hit, boolean throughWalls) {
        return !clientScreenOpen && hit != null;
    }

    @Override
    public boolean aimsAtBlock(Object hit, float yaw, float pitch) {
        return true;
    }

    @Override
    public void useItemOn(Hand hand, Object hit) {
        send("ServerboundUseItemOnPacket", PacketKind.USE_ITEM);
        send("ServerboundSwingPacket", PacketKind.SWING);
    }

    @Override
    public void useItem(Hand hand) {
        send("ServerboundUseItemPacket", PacketKind.USE_ITEM);
    }

    @Override
    public boolean inventoryAvailable(int containerId) {
        return !clientScreenOpen && containerId == openContainerId;
    }

    @Override
    public void containerClick(int containerId, int slot, int button, ClickType type) {
        send("ServerboundContainerClickPacket", PacketKind.CONTAINER);
    }

    @Override public int selectedHotbar() { return selected; }

    @Override
    public void selectHotbar(int slot) {
        selected = slot;
        send("ServerboundSetCarriedItemPacket", PacketKind.HELD_ITEM);
    }

    @Override
    public void swapHands() {
        send("ServerboundPlayerActionPacket", PacketKind.PLAYER_ACTION);
    }

    @Override
    public float rotationStep() {
        double f = sensitivity * 0.6 + 0.2;
        return (float) (f * f * f * 8.0 * 0.15);
    }
}
