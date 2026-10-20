package dev.noir.network;

/**
 * Something a module wants done this tick. Actions carry platform objects
 * (entities, hit results) as opaque handles; only the {@link PacketSink} looks inside.
 */
public sealed interface Action {
    /** Execution slot. Slots run in vanilla's input order: hotbar, inventory, swap, attack, use. */
    Slot slot();

    enum Slot {
        HOTBAR,
        INVENTORY,
        SWAP_HANDS,
        ATTACK,
        USE
    }

    /** Select a hotbar slot (0-8). Sent by vanilla's carried-item sync. */
    record SelectHotbar(int hotbarSlot) implements Action {
        public SelectHotbar {
            if (hotbarSlot < 0 || hotbarSlot > 8) throw new IllegalArgumentException("hotbar slot " + hotbarSlot);
        }

        public Slot slot() { return Slot.HOTBAR; }
    }

    /** A real container click in the currently open menu. */
    record ContainerClick(int containerId, int slotIndex, int button, ClickType type) implements Action {
        public Slot slot() { return Slot.INVENTORY; }
    }

    /** Swap main/offhand items (vanilla F key). */
    record SwapHands() implements Action {
        public Slot slot() { return Slot.SWAP_HANDS; }
    }

    /** Attack an entity. Executes only if the server-side rotation already aims at it. */
    record Attack(Object target, boolean throughWalls) implements Action {
        public Slot slot() { return Slot.ATTACK; }
    }

    /** Use the held item on a block face. Executes only if the server-side rotation aims at the hit. */
    record UseItemOn(Hand hand, Object hit, boolean throughWalls) implements Action {
        public Slot slot() { return Slot.USE; }
    }

    /** Use the held item in the air. */
    record UseItem(Hand hand) implements Action {
        public Slot slot() { return Slot.USE; }
    }
}
