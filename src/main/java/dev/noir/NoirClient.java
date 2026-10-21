package dev.noir;

import dev.noir.basehunt.McChunkSource;
import dev.noir.basehunt.McChunkView;
import dev.noir.event.Events;
import dev.noir.event.RenderWorldEvent;
import dev.noir.gui.McCanvas;
import dev.noir.hud.HudEditorScreen;
import dev.noir.hud.McHudData;
import dev.noir.module.Modules;
import dev.noir.module.settings.BlockListSetting;
import dev.noir.module.settings.EntityListSetting;
import dev.noir.network.MinecraftPacketSink;
import dev.noir.network.TpsTracker;
import dev.noir.render.CameraInfo;
import dev.noir.render.McWorldCanvas;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Fabric entrypoint. Wires the platform-independent core to the game:
 * ticks, chunks, chat commands, HUD and world rendering. Packet hooks and input
 * live in the mixins; every outgoing packet is reported to the PacketManager.
 */
public final class NoirClient implements ClientModInitializer {
    private static NoirCore core;
    private static McHudData hudData;
    private static final TpsTracker TPS = new TpsTracker();
    private static McChunkSource chunks;
    private static long tick;
    private static volatile boolean rotationResyncPending;

    public static NoirCore core() {
        return core;
    }

    public static TpsTracker tps() {
        return TPS;
    }

    /** Called from the network thread when the server sets our position/rotation. */
    public static void requestRotationResync() {
        rotationResyncPending = true;
    }

    public static boolean inWorld(Minecraft mc) {
        return mc.player != null && mc.level != null;
    }

    @Override
    public void onInitializeClient() {
        Minecraft mc = Minecraft.getInstance();
        core = new NoirCore(new MinecraftPlatform(mc));

        BlockListSetting.VALIDATOR = id -> {
            Identifier i = Identifier.tryParse(id);
            return i != null && i.equals(BuiltInRegistries.BLOCK.getKey(BuiltInRegistries.BLOCK.getValue(i)));
        };
        EntityListSetting.VALIDATOR = id -> {
            Identifier i = Identifier.tryParse(id);
            return i != null && i.equals(BuiltInRegistries.ENTITY_TYPE.getKey(BuiltInRegistries.ENTITY_TYPE.getValue(i)));
        };

        core.register(Modules.create());
        core.packets().setSink(new MinecraftPacketSink(mc, core));
        hudData = new McHudData(mc, core, TPS);
        chunks = new McChunkSource(mc);
        core.baseHunt().setSource(chunks);
        core.start();

        ClientTickEvents.START_CLIENT_TICK.register(NoirClient::onTickStart);
        ClientTickEvents.END_CLIENT_TICK.register(NoirClient::onTickEnd);

        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            McChunkView view = new McChunkView(level, chunk);
            chunks.onLoad(view);
            core.events().post(new Events.ChunkLoad(view.chunkX(), view.chunkZ(), chunk, view));
        });
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
            chunks.onUnload(chunk.getPos().x(), chunk.getPos().z());
            core.events().post(new Events.ChunkUnload(chunk.getPos().x(), chunk.getPos().z(), chunk));
        });

        // Commands are handled locally and never reach the server.
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> !core.commands().handle(message));

        ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> {
            core.packets().rotations().reset();
            TPS.reset();
            core.events().post(new Events.WorldChange(true));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> {
            core.packets().rotations().reset();
            chunks.clear();
            core.events().post(new Events.WorldChange(false));
            core.config().saveAll();
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("noir", "hud"), (graphics, deltaTracker) -> {
            if (!inWorld(mc) || mc.gui.screen() instanceof HudEditorScreen) return;
            McCanvas canvas = McCanvas.begin(graphics, core);
            try {
                core.hud().render(canvas, hudData);
                core.events().post(new Events.RenderHud(canvas, deltaTracker.getGameTimeDeltaPartialTick(false), hudData.camera()));
            } finally {
                canvas.end();
            }
        });

        LevelRenderEvents.END_MAIN.register(context -> core.events().post(new RenderWorldEvent(context)));
        // 3D shapes from modules, submitted as translucent quads (same hook Fabric's 26.2 render tests use).
        LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(context -> {
            if (!inWorld(mc)) return;
            CameraInfo cam = hudData.camera(McWorldCanvas.cameraPos(context));
            McWorldCanvas canvas = new McWorldCanvas(cam);
            core.events().post(new Events.RenderWorld3D(canvas, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)));
            canvas.submit(context);
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> core.shutdown());
    }

    private static void onTickStart(Minecraft mc) {
        tick++;
        core.packets().beginTick(tick);
        if (!inWorld(mc)) return;
        if (rotationResyncPending) {
            rotationResyncPending = false;
            core.packets().rotations().sync(mc.player.getYRot(), mc.player.getXRot());
        }
        core.events().post(new Events.TickPre(tick));
    }

    private static void onTickEnd(Minecraft mc) {
        if (inWorld(mc)) core.events().post(new Events.TickPost(tick));
        core.packets().endTick();
        core.config().tick();
    }

    public static McHudData hudData() {
        return hudData;
    }
}
