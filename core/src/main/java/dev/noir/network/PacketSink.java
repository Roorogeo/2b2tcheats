package dev.noir.network;

/**
 * The only thing allowed to touch the connection. The Minecraft implementation
 * performs each action through vanilla's own code paths (MultiPlayerGameMode,
 * LocalPlayer.swing, ...) so packet shape, order and state ids are exactly vanilla's.
 */
public interface PacketSink {
    /** In a world with a live player, and nothing is blocking input. */
    boolean ready();

    boolean isUsingItem();

    /** Reach, alive, line of sight (unless {@code throughWalls}). */
    boolean validateAttack(Object target, boolean throughWalls);

    /** Whether a look direction of (yaw, pitch) from the eyes hits the target's hitbox within reach. */
    boolean aimsAt(Object target, float yaw, float pitch);

    /** Vanilla attack followed by the vanilla main-hand swing. */
    void attack(Object target);

    boolean validateUseOn(Object hit, boolean throughWalls);

    boolean aimsAtBlock(Object hit, float yaw, float pitch);

    /** Vanilla use-on-block, including the swing vanilla sends on success. */
    void useItemOn(Hand hand, Object hit);

    void useItem(Hand hand);

    /**
     * True only if the menu with this id is the one the server has open for us and
     * no client-only screen (chat, our GUI, ...) is open.
     */
    boolean inventoryAvailable(int containerId);

    /** Vanilla container click, which carries the menu's current state id. */
    void containerClick(int containerId, int slot, int button, ClickType type);

    int selectedHotbar();

    void selectHotbar(int slot);

    void swapHands();

    /** Vanilla mouse rotation step (degrees) for the current sensitivity; 0 disables quantization. */
    float rotationStep();
}
