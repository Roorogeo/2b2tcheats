package dev.noir.network;

import dev.noir.NoirCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Executes PacketManager actions through vanilla's own code paths, so every packet
 * has vanilla's shape, order and sequence/state ids:
 * attack = MultiPlayerGameMode.attack + LocalPlayer.swing, container clicks go through
 * MultiPlayerGameMode.handleContainerInput (which uses the menu's state id), etc.
 */
public final class MinecraftPacketSink implements PacketSink {
    private final Minecraft mc;
    private final NoirCore core;

    public MinecraftPacketSink(Minecraft mc, NoirCore core) {
        this.mc = mc;
        this.core = core;
    }

    private LocalPlayer player() {
        return mc.player;
    }

    @Override
    public boolean ready() {
        return mc.player != null && mc.level != null && mc.gameMode != null && mc.getConnection() != null;
    }

    @Override
    public boolean isUsingItem() {
        return player().isUsingItem();
    }

    private boolean noScreen() {
        return mc.gui.screen() == null;
    }

    @Override
    public boolean validateAttack(Object target, boolean throughWalls) {
        if (!(target instanceof Entity e) || !noScreen()) return false;
        LocalPlayer p = player();
        if (e == p || !e.isAlive() || e.isRemoved()) return false;
        if (e instanceof net.minecraft.world.entity.player.Player other && core.friends().isFriend(other.getGameProfile().name())) return false;
        double range = p.entityInteractionRange();
        Vec3 eye = p.getEyePosition();
        AABB box = e.getBoundingBox();
        Vec3 closest = new Vec3(
            Math.max(box.minX, Math.min(eye.x, box.maxX)),
            Math.max(box.minY, Math.min(eye.y, box.maxY)),
            Math.max(box.minZ, Math.min(eye.z, box.maxZ)));
        if (eye.distanceToSqr(closest) > range * range) return false;
        return throughWalls || !(e instanceof LivingEntity) || p.hasLineOfSight(e);
    }

    @Override
    public boolean aimsAt(Object target, float yaw, float pitch) {
        if (!(target instanceof Entity e)) return false;
        LocalPlayer p = player();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(Vec3.directionFromRotation(pitch, yaw).scale(p.entityInteractionRange()));
        AABB box = e.getBoundingBox();
        return box.contains(eye) || box.clip(eye, end).isPresent();
    }

    @Override
    public void attack(Object target) {
        Entity e = (Entity) target;
        // Same as Minecraft.startAttack: attack, then swing the main hand.
        mc.gameMode.attack(player(), e);
        player().swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public boolean validateUseOn(Object hit, boolean throughWalls) {
        if (!(hit instanceof BlockHitResult b) || b.getType() != HitResult.Type.BLOCK || !noScreen()) return false;
        LocalPlayer p = player();
        Vec3 eye = p.getEyePosition();
        double range = p.blockInteractionRange();
        if (eye.distanceToSqr(b.getLocation()) > range * range) return false;
        if (throughWalls) return true;
        BlockHitResult ray = mc.level.clip(new ClipContext(eye, b.getLocation(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return ray.getType() == HitResult.Type.MISS || ray.getBlockPos().equals(b.getBlockPos());
    }

    @Override
    public boolean aimsAtBlock(Object hit, float yaw, float pitch) {
        if (!(hit instanceof BlockHitResult b)) return false;
        LocalPlayer p = player();
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(Vec3.directionFromRotation(pitch, yaw).scale(p.blockInteractionRange()));
        return new AABB(b.getBlockPos()).clip(eye, end).isPresent();
    }

    private static InteractionHand hand(Hand h) {
        return h == Hand.OFF_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    @Override
    public void useItemOn(Hand hand, Object hit) {
        InteractionHand h = hand(hand);
        InteractionResult result = mc.gameMode.useItemOn(player(), h, (BlockHitResult) hit);
        swingLikeVanilla(result, h);
    }

    @Override
    public void useItem(Hand hand) {
        InteractionHand h = hand(hand);
        InteractionResult result = mc.gameMode.useItem(player(), h);
        swingLikeVanilla(result, h);
    }

    /** Minecraft.startUseItem swings only when the result asks the client to. */
    private void swingLikeVanilla(InteractionResult result, InteractionHand hand) {
        if (result instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SwingSource.CLIENT) {
            player().swing(hand);
        }
    }

    @Override
    public boolean inventoryAvailable(int containerId) {
        Screen screen = mc.gui.screen();
        boolean serverKnown = screen == null || screen instanceof AbstractContainerScreen<?>;
        return serverKnown && player().containerMenu.containerId == containerId;
    }

    @Override
    public void containerClick(int containerId, int slot, int button, ClickType type) {
        mc.gameMode.handleContainerInput(containerId, slot, button, ContainerInput.valueOf(type.name()), player());
    }

    @Override
    public int selectedHotbar() {
        return player().getInventory().getSelectedSlot();
    }

    /**
     * Changes the selected slot exactly like a scroll/number key: vanilla sends the
     * carried-item packet itself on its next game-mode tick.
     */
    @Override
    public void selectHotbar(int slot) {
        player().getInventory().setSelectedSlot(slot);
    }

    /** Same packet the vanilla swap-offhand key sends. */
    @Override
    public void swapHands() {
        if (player().isSpectator()) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ZERO, Direction.DOWN, 0));
    }

    /** MouseHandler.turnPlayer: (sens * 0.6 + 0.2)^3 * 8, times Entity.turn's 0.15. */
    @Override
    public float rotationStep() {
        double s = mc.options.sensitivity().get();
        double f = s * 0.6 + 0.2;
        return (float) (f * f * f * 8.0 * 0.15);
    }
}
