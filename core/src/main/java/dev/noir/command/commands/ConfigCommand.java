package dev.noir.command.commands;

import dev.noir.NoirCore;
import dev.noir.command.Command;
import dev.noir.command.CommandException;
import dev.noir.config.ConfigManager;
import dev.noir.module.Module;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConfigCommand extends Command {
    public ConfigCommand() {
        super("config", List.of("cfg", "profile"),
            "config <save [name]|load <name>|list|delete <name>|reset <module|all>|current>",
            "Manages config profiles and resets modules to defaults.");
    }

    @Override
    public void execute(NoirCore core, String[] args) throws CommandException {
        require(args.length >= 1, "Missing action.");
        ConfigManager cfg = core.config();
        String action = args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "save" -> {
                String name = args.length >= 2 ? args[1] : cfg.activeProfile();
                require(ConfigManager.isValidProfileName(name), "Profile names use letters, digits, _ and - (max 32).");
                require(cfg.saveProfile(name), "Could not save profile " + name + ".");
                core.platform().chat("Saved profile " + name + ".");
            }
            case "load" -> {
                require(args.length >= 2, "Which profile?");
                require(cfg.loadProfile(args[1]), "No profile named " + args[1] + ".");
                core.platform().chat("Loaded profile " + args[1] + ".");
            }
            case "list" -> core.platform().chat("Profiles: " + String.join(", ", cfg.profiles())
                + " (active: " + cfg.activeProfile() + ")");
            case "delete" -> {
                require(args.length >= 2, "Which profile?");
                require(!args[1].equals(cfg.activeProfile()), "Can't delete the active profile.");
                require(cfg.deleteProfile(args[1]), "No profile named " + args[1] + ".");
                core.platform().chat("Deleted profile " + args[1] + ".");
            }
            case "reset" -> {
                require(args.length >= 2, "Reset which module (or all)?");
                if (args[1].equalsIgnoreCase("all")) {
                    for (Module m : core.modules().all()) cfg.resetModule(m);
                    core.platform().chat("All modules reset to defaults.");
                } else {
                    Module m = Targets.module(core, args[1]);
                    cfg.resetModule(m);
                    core.platform().chat(m.name() + " reset to defaults.");
                }
            }
            case "current" -> core.platform().chat("Active profile: " + cfg.activeProfile());
            default -> throw new CommandException("Unknown action \"" + args[0] + "\".");
        }
    }

    @Override
    public List<String> suggest(NoirCore core, String[] args, int index) {
        if (index == 0) return List.of("save", "load", "list", "delete", "reset", "current");
        if (index == 1) {
            String a = args[0].toLowerCase(Locale.ROOT);
            if (a.equals("load") || a.equals("delete") || a.equals("save")) return core.config().profiles();
            if (a.equals("reset")) {
                List<String> out = new ArrayList<>(Targets.moduleNames(core));
                out.add("all");
                return out;
            }
        }
        return List.of();
    }
}
