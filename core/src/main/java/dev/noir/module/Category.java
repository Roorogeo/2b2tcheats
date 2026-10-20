package dev.noir.module;

public enum Category {
    COMBAT("Combat", false),
    MOVEMENT("Movement", false),
    RENDER("Render", true),
    BASE_HUNTING("Base Hunting", true);

    private final String displayName;
    private final boolean readOnly;

    Category(String displayName, boolean readOnly) {
        this.displayName = displayName;
        this.readOnly = readOnly;
    }

    /**
     * Read-only categories may only observe data the server already sent; the
     * PacketManager refuses (and the packet test fails on) any packet they cause.
     */
    public boolean readOnly() {
        return readOnly;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
