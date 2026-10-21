package dev.noir;

import dev.noir.util.Clock;

import java.nio.file.Path;

/** What the core needs from the game. The Minecraft layer implements it; tests fake it. */
public interface Platform {
    /** {@code .minecraft/config/noir}. */
    Path configDir();

    /** Prints a client-side chat line. Never sent to the server. */
    void chat(String message);

    void log(String message);

    void logError(String message, Throwable t);

    Clock clock();

    String clipboard();

    void setClipboard(String text);

    void openClickGui();

    void openHudEditor();

    /** Address of the current server ("singleplayer" in single player). Used for local logs/waypoints only. */
    default String serverId() {
        return "local";
    }
}
