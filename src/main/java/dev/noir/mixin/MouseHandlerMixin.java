package dev.noir.mixin;

import dev.noir.NoirClient;
import dev.noir.event.Events;
import dev.noir.util.Keys;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse buttons for module binds (mouse4, mouse5, ...). */
@Mixin(net.minecraft.client.MouseHandler.class)
abstract class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onButton", at = @At("HEAD"))
    private void noir$onButton(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo ci) {
        if (NoirClient.core() == null) return;
        NoirClient.core().events().post(new Events.Key(Keys.mouse(rawButtonInfo.button()), action, 0, minecraft.gui.screen() != null));
    }
}
