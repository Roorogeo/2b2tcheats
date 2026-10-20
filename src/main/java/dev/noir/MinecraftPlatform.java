package dev.noir;

import dev.noir.gui.NoirScreen;
import dev.noir.hud.HudEditorScreen;
import dev.noir.util.Clock;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

final class MinecraftPlatform implements Platform {
    private static final Logger LOG = LoggerFactory.getLogger("Noir");
    private final Minecraft mc;

    MinecraftPlatform(Minecraft mc) {
        this.mc = mc;
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("noir");
    }

    /** Local chat line only: LocalPlayer.sendSystemMessage displays it and sends nothing. */
    @Override
    public void chat(String message) {
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("[Noir] " + message));
        else LOG.info(message);
    }

    @Override
    public void log(String message) {
        LOG.info(message);
    }

    @Override
    public void logError(String message, Throwable t) {
        LOG.error(message, t);
    }

    @Override
    public Clock clock() {
        return Clock.SYSTEM;
    }

    @Override
    public String clipboard() {
        return mc.keyboardHandler.getClipboard();
    }

    @Override
    public void setClipboard(String text) {
        mc.keyboardHandler.setClipboard(text);
    }

    @Override
    public void openClickGui() {
        mc.execute(() -> mc.gui.setScreen(new NoirScreen(NoirClient.core())));
    }

    @Override
    public void openHudEditor() {
        mc.execute(() -> mc.gui.setScreen(new HudEditorScreen(NoirClient.core(), NoirClient.hudData())));
    }
}
