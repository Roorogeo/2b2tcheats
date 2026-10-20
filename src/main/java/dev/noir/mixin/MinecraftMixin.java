package dev.noir.mixin;

import dev.noir.NoirClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Queued module actions run at the end of vanilla's input handling — the same point in
 * the tick where vanilla performs attacks, item use and hotbar/offhand keys — so their
 * packets come before this tick's movement packet, exactly like a player's clicks.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "handleKeybinds", at = @At("TAIL"))
    private void noir$afterKeybinds(CallbackInfo ci) {
        if (NoirClient.core() != null && NoirClient.inWorld((Minecraft) (Object) this)) {
            NoirClient.core().packets().flushInteractions();
        }
    }
}
