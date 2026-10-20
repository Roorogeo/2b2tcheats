package dev.noir.module;

/**
 * Every module in the client. Stage 1 ships the core systems, PacketManager and GUI;
 * Base Hunting (stage 2) and Combat/Movement/Render (stage 3) modules are added here.
 */
public final class Modules {
    private Modules() {}

    public static Module[] create() {
        return new Module[] {};
    }
}
