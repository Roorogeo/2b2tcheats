package dev.noir.event;

import dev.noir.gui.Canvas;
import dev.noir.module.Module;
import dev.noir.network.PacketKind;

/**
 * Platform-independent events. World rendering lives in the Minecraft layer
 * ({@code dev.noir.event.RenderWorldEvent}) because it carries renderer types.
 */
public final class Events {
    private Events() {}

    /** Start of a client tick, before vanilla input handling. Modules queue actions here. */
    public record TickPre(long tick) {}

    /** End of a client tick, after the player's movement packet was decided. */
    public record TickPost(long tick) {}

    /** HUD pass, drawn after vanilla HUD. {@code camera} lets modules project world positions. */
    public record RenderHud(Canvas canvas, float partialTick, dev.noir.render.CameraInfo camera) {}

    /** An outgoing packet. {@code packet} is the platform packet object. Cancelling drops it. */
    public static final class PacketSend extends Cancellable {
        public final Object packet;
        public final String type;
        public final PacketKind kind;

        public PacketSend(Object packet, String type, PacketKind kind) {
            this.packet = packet;
            this.type = type;
            this.kind = kind;
        }
    }

    /** An incoming packet, observed before vanilla handles it. Read-only: cannot be cancelled. */
    public record PacketReceive(Object packet, String type) {}

    /** A chunk arrived from the server. {@code view} reads it (also off-thread). */
    public record ChunkLoad(int chunkX, int chunkZ, Object chunk, dev.noir.basehunt.ChunkView view) {}

    public record ChunkUnload(int chunkX, int chunkZ, Object chunk) {}

    /** A block changed client-side (server update). {@code flowingFluid} if the new state is a flowing liquid. */
    public record BlockChange(String dimension, int x, int y, int z, String oldId, String newId, boolean flowingFluid) {}

    /** World pass for 3D shapes (boxes, outlines, tracers). */
    public record RenderWorld3D(dev.noir.render.WorldCanvas canvas, float partialTick) {}

    /** Raw key input. {@code action} is one of Keys.ACTION_*. {@code inScreen} is true when a screen is open. */
    public record Key(int key, int action, int modifiers, boolean inScreen) {}

    public record ModuleToggle(Module module, boolean enabled) {}

    /** World joined or left; caches keyed by world must reset. */
    public record WorldChange(boolean joined) {}
}
