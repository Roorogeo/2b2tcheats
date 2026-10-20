package dev.noir.network;

/** Coarse classification of serverbound packets, used for budgets, guarding and log filters. */
public enum PacketKind {
    MOVEMENT("Movement"),
    INTERACT("Interact"),
    SWING("Swing"),
    USE_ITEM("Use item"),
    PLAYER_ACTION("Player action"),
    PLAYER_COMMAND("Player command"),
    CONTAINER("Container"),
    HELD_ITEM("Held item"),
    CHAT("Chat"),
    CUSTOM_PAYLOAD("Custom payload"),
    KEEP_ALIVE("Keep-alive"),
    OTHER("Other");

    private final String label;

    PacketKind(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
