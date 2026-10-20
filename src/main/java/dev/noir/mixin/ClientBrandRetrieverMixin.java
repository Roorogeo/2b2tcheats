package dev.noir.mixin;

import dev.noir.ClientSettings;
import dev.noir.NoirClient;
import net.minecraft.client.ClientBrandRetriever;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Brand option. "Fabric" leaves Fabric's brand ("fabric") alone; "Vanilla" reports
 * "vanilla". Noir never reports its own brand.
 */
@Mixin(ClientBrandRetriever.class)
abstract class ClientBrandRetrieverMixin {
    @Inject(method = "getClientModName", at = @At("HEAD"), cancellable = true)
    private static void noir$brand(CallbackInfoReturnable<String> cir) {
        if (NoirClient.core() != null && NoirClient.core().client().brand.get() == ClientSettings.Brand.VANILLA) {
            cir.setReturnValue("vanilla");
        }
    }
}
