package dev.noir.testutil;

import dev.noir.Platform;
import dev.noir.util.Clock;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FakePlatform implements Platform {
    public final Path dir;
    public final Clock.Manual clock = new Clock.Manual(1_000_000);
    public final List<String> chat = new ArrayList<>();
    public final List<String> errors = new ArrayList<>();
    public String clipboard = "";
    public int guiOpens;
    public int hudOpens;

    public FakePlatform(Path dir) {
        this.dir = dir;
    }

    @Override public Path configDir() { return dir; }
    @Override public void chat(String message) { chat.add(message); }
    @Override public void log(String message) {}

    @Override
    public void logError(String message, Throwable t) {
        errors.add(message + (t == null ? "" : ": " + t));
    }

    @Override public Clock clock() { return clock; }
    @Override public String clipboard() { return clipboard; }
    @Override public void setClipboard(String text) { clipboard = text; }
    @Override public void openClickGui() { guiOpens++; }
    @Override public void openHudEditor() { hudOpens++; }

    public String lastChat() {
        return chat.isEmpty() ? null : chat.get(chat.size() - 1);
    }
}
