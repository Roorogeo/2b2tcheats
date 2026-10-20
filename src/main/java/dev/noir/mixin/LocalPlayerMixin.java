package dev.noir.mixin;

import dev.noir.NoirClient;
import dev.noir.NoirCore;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Silent rotations. Around vanilla's own movement-packet code the player's rotation is
 * swapped for the PacketManager's server-side rotation, then restored. Vanilla still
 * decides whether and which movement packet to send (heartbeat, position-only, ...),
 * so there is never more than one movement packet per tick and nothing when idle.
 */
@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Unique
    private float noir$realYaw;
    @Unique
    private float noir$realPitch;
    @Unique
    private boolean noir$swapped;

    @Unique
    private void noir$swapIn() {
        NoirCore core = NoirClient.core();
        if (core == null) return;
        LocalPlayer self = (LocalPlayer) (Object) this;
        noir$realYaw = self.getYRot();
        noir$realPitch = self.getXRot();
        float[] rot = core.packets().resolveRotation(noir$realYaw, noir$realPitch);
        if (rot[0] != noir$realYaw || rot[1] != noir$realPitch) {
            self.setYRot(rot[0]);
            self.setXRot(rot[1]);
            noir$swapped = true;
        }
    }

    @Unique
    private void noir$swapOut() {
        if (!noir$swapped) return;
        LocalPlayer self = (LocalPlayer) (Object) this;
        self.setYRot(noir$realYaw);
        self.setXRot(noir$realPitch);
        noir$swapped = false;
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void noir$beforeSendPosition(CallbackInfo ci) {
        noir$swapIn();
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void noir$afterSendPosition(CallbackInfo ci) {
        noir$swapOut();
    }

    // While riding, vanilla sends a rotation packet from tick() instead of sendPosition().
    @Inject(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V", ordinal = 1))
    private void noir$beforeVehicleRotation(CallbackInfo ci) {
        noir$swapIn();
    }

    @Inject(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;send(Lnet/minecraft/network/protocol/Packet;)V", ordinal = 1,
        shift = At.Shift.AFTER))
    private void noir$afterVehicleRotation(CallbackInfo ci) {
        noir$swapOut();
    }
}
