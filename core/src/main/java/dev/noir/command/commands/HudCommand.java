package dev.noir.command.commands;

import dev.noir.NoirCore;
import dev.noir.command.Command;

import java.util.List;

public final class HudCommand extends Command {
    public HudCommand() {
        super("hud", List.of("hudeditor"), "hud", "Opens the HUD editor (drag to move, right-click to toggle, scroll to scale).");
    }

    @Override
    public void execute(NoirCore core, String[] args) {
        core.platform().openHudEditor();
    }
}
