package dev.noir.network;

/**
 * Maps serverbound packet classes to {@link PacketKind} by name. Minecraft 26.x is
 * unobfuscated, so runtime class names are the real ones; matching by name keeps this
 * working if a packet class is added or renamed.
 */
public final class PacketClassifier {
    private PacketClassifier() {}

    /** "ServerboundMovePlayerPacket$PosRot" style name (no package). */
    public static String typeName(Object packet) {
        String n = packet.getClass().getName();
        return n.substring(n.lastIndexOf('.') + 1);
    }

    public static PacketKind classify(Object packet) {
        String n = typeName(packet);
        if (n.startsWith("ServerboundMovePlayerPacket") || n.startsWith("ServerboundMoveVehiclePacket")) return PacketKind.MOVEMENT;
        if (n.startsWith("ServerboundInteractPacket") || n.startsWith("ServerboundAttackPacket")) return PacketKind.INTERACT;
        if (n.startsWith("ServerboundSwingPacket")) return PacketKind.SWING;
        if (n.startsWith("ServerboundUseItemOnPacket") || n.startsWith("ServerboundUseItemPacket")) return PacketKind.USE_ITEM;
        if (n.startsWith("ServerboundPlayerActionPacket")) return PacketKind.PLAYER_ACTION;
        if (n.startsWith("ServerboundPlayerCommandPacket") || n.startsWith("ServerboundPlayerInputPacket")) return PacketKind.PLAYER_COMMAND;
        if (n.startsWith("ServerboundContainer")) return PacketKind.CONTAINER;
        if (n.startsWith("ServerboundSetCarriedItemPacket")) return PacketKind.HELD_ITEM;
        if (n.startsWith("ServerboundChat")) return PacketKind.CHAT;
        if (n.startsWith("ServerboundCustomPayloadPacket")) return PacketKind.CUSTOM_PAYLOAD;
        if (n.startsWith("ServerboundKeepAlivePacket") || n.startsWith("ServerboundPongPacket")) return PacketKind.KEEP_ALIVE;
        return PacketKind.OTHER;
    }
}
