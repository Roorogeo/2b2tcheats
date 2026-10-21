package dev.noir.mixin;

import dev.noir.NoirClient;
import dev.noir.basehunt.McChunkView;
import dev.noir.event.Events;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side block changes (server block updates). Base Hunting uses them to rescan
 * changed chunks and to see flowing-liquid updates in freshly generated chunks.
 */
@Mixin(LevelChunk.class)
abstract class LevelChunkMixin {
    @Shadow
    @Final
    private Level level;

    @Inject(method = "setBlockState", at = @At("TAIL"))
    private void noir$onSetBlockState(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<BlockState> cir) {
        if (!level.isClientSide() || NoirClient.core() == null) return;
        BlockState old = cir.getReturnValue();
        NoirClient.core().events().post(new Events.BlockChange(McChunkView.dimensionName(level), pos.getX(), pos.getY(), pos.getZ(),
            old == null ? "minecraft:air" : McChunkView.id(old), McChunkView.id(state), McChunkView.flowing(state)));
    }
}
