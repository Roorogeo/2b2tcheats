package dev.noir.command.commands;

import dev.noir.NoirCore;
import dev.noir.command.Command;
import dev.noir.command.CommandException;
import dev.noir.module.BindMode;
import dev.noir.module.Module;
import dev.noir.util.Keys;

import java.util.List;

public final class BindCommand extends Command {
    public BindCommand() {
        super("bind", List.of("b"), "bind <module> <key|none> [toggle|hold] | bind list",
            "Binds a module to a key (e.g. rshift, g, mouse4).");
    }

    @Override
    public void execute(NoirCore core, String[] args) throws CommandException {
        require(args.length >= 1, "Which module?");
        if (args[0].equalsIgnoreCase("list")) {
            boolean any = false;
            for (Module m : core.modules().all()) {
                if (!m.keybind.isBound()) continue;
                any = true;
                core.platform().chat("  " + m.name() + ": " + Keys.name(m.keybind.code()) + " (" + m.bindMode.get() + ")");
            }
            if (!any) core.platform().chat("No modules are bound.");
            return;
        }
        Module m = Targets.module(core, args[0]);
        if (args.length == 1) {
            core.platform().chat(m.name() + " is bound to " + Keys.name(m.keybind.code()) + " (" + m.bindMode.get() + ").");
            return;
        }
        Integer key = Keys.parse(args[1]);
        require(key != null, "Unknown key \"" + args[1] + "\".");
        m.keybind.set(key);
        if (args.length >= 3) {
            BindMode mode = m.bindMode.find(args[2]);
            require(mode != null, "Bind mode must be toggle or hold.");
            m.bindMode.set(mode);
        }
        core.platform().chat(key == Keys.NONE
            ? m.name() + " unbound."
            : m.name() + " bound to " + Keys.name(key) + " (" + m.bindMode.get() + ").");
    }

    @Override
    public List<String> suggest(NoirCore core, String[] args, int index) {
        if (index == 0) {
            List<String> names = new java.util.ArrayList<>(Targets.moduleNames(core));
            names.add("list");
            return names;
        }
        if (index == 1) return List.of("none", "rshift", "g", "r", "x", "mouse4", "mouse5");
        if (index == 2) return List.of("toggle", "hold");
        return List.of();
    }
}
