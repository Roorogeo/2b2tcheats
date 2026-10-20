package dev.noir.mixin;

import dev.noir.NoirClient;
import dev.noir.event.Events;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Raw key input for module binds and the GUI key. */
@Mixin(net.minecraft.client.KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void noir$onKey(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (NoirClient.core() == null || event.key() < 0) return;
        NoirClient.core().events().post(new Events.Key(event.key(), action, event.modifiers(), minecraft.gui.screen() != null));
    }
}
