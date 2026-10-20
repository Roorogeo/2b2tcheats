package dev.noir.command.commands;

import dev.noir.NoirCore;
import dev.noir.command.Command;
import dev.noir.command.CommandException;
import dev.noir.module.Module;

import java.util.List;
import java.util.Locale;

public final class ToggleCommand extends Command {
    public ToggleCommand() {
        super("toggle", List.of("t"), "toggle <module> [on|off]", "Toggles a module, or sets it on/off.");
    }

    @Override
    public void execute(NoirCore core, String[] args) throws CommandException {
        require(args.length >= 1, "Which module?");
        Module m = Targets.module(core, args[0]);
        if (args.length >= 2) {
            String s = args[1].toLowerCase(Locale.ROOT);
            require(s.equals("on") || s.equals("off"), "Expected on or off.");
            m.setEnabled(s.equals("on"));
        } else {
            m.toggle();
        }
        core.platform().chat(m.name() + " " + (m.isEnabled() ? "enabled" : "disabled") + ".");
    }

    @Override
    public List<String> suggest(NoirCore core, String[] args, int index) {
        if (index == 0) return Targets.moduleNames(core);
        if (index == 1) return List.of("on", "off");
        return List.of();
    }
}
