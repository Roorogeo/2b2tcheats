package dev.noir.network;

/**
 * A module's handle to the PacketManager. Requests are per-tick intents: a module
 * that still wants something next tick asks again. Nothing here sends a packet
 * directly; the manager decides when (and whether) vanilla does.
 */
public interface ActionClient {
    /**
     * Requests a server-side rotation this tick. Highest priority wins; the rotation
     * moves toward the target at most {@code speed} degrees per tick (0 = instant).
     * Silent rotations don't move the camera.
     */
    void rotate(float yaw, float pitch, float speed, int priority);

    void attack(Object target, boolean throughWalls, int priority);

    void useItemOn(Hand hand, Object hit, boolean throughWalls, int priority);

    void useItem(Hand hand, int priority);

    void click(int containerId, int slot, int button, ClickType type, int priority);

    void selectHotbar(int slot, int priority);

    void swapHands(int priority);

    /** Server-side rotation as of the last movement packet decision. */
    float serverYaw();

    float serverPitch();
}
