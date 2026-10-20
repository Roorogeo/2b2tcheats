package dev.noir.mixin;

import dev.noir.ClientSettings;
import dev.noir.NoirClient;
import dev.noir.NoirCore;
import dev.noir.event.Events;
import dev.noir.network.PacketClassifier;
import dev.noir.network.PacketKind;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The single outgoing-packet checkpoint. Every packet is classified, attributed by the
 * PacketManager (vanilla or the module whose code caused it), logged, and dropped if a
 * module caused it outside the PacketManager or a read-only module caused it.
 */
@Mixin(Connection.class)
abstract class ConnectionMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
    private void noir$onSend(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        NoirCore core = NoirClient.core();
        if (core == null) return;
        PacketKind kind = PacketClassifier.classify(packet);

        // Safety net: a command typed in chat never leaves the client.
        if (packet instanceof ServerboundChatPacket chat && core.commands().isCommand(chat.message())) {
            ci.cancel();
            return;
        }
        // "Vanilla" brand: no channel registration or other mod payloads, only the brand itself.
        if (packet instanceof ServerboundCustomPayloadPacket custom
            && core.client().brand.get() == ClientSettings.Brand.VANILLA
            && !(custom.payload() instanceof BrandPayload)) {
            ci.cancel();
            return;
        }

        Events.PacketSend event = core.events().post(new Events.PacketSend(packet, PacketClassifier.typeName(packet), kind));
        if (!core.packets().onOutgoing(event.type, kind) || event.isCancelled()) ci.cancel();
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"))
    private void noir$onReceive(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        NoirCore core = NoirClient.core();
        if (core == null) return;
        if (packet instanceof ClientboundSetTimePacket) NoirClient.tps().onTimeUpdate();
        if (packet instanceof ClientboundPlayerPositionPacket) NoirClient.requestRotationResync();
        core.events().post(new Events.PacketReceive(packet, PacketClassifier.typeName(packet)));
    }
}
