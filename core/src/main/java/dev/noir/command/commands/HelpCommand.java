package dev.noir.command.commands;

import dev.noir.NoirCore;
import dev.noir.command.Command;
import dev.noir.command.CommandException;

import java.util.List;

public final class HelpCommand extends Command {
    public HelpCommand() {
        super("help", List.of("h", "?"), "help [command]", "Lists commands or shows one command's usage.");
    }

    @Override
    public void execute(NoirCore core, String[] args) throws CommandException {
        String p = core.commands().prefix();
        if (args.length > 0) {
            Command c = core.commands().find(args[0]);
            require(c != null, "No command named \"" + args[0] + "\".");
            core.platform().chat(p + c.usage() + " - " + c.description());
            return;
        }
        core.platform().chat("Noir commands (handled locally, never sent):");
        for (Command c : core.commands().all()) core.platform().chat("  " + p + c.usage() + " - " + c.description());
    }

    @Override
    public List<String> suggest(NoirCore core, String[] args, int index) {
        return index == 0 ? core.commands().all().stream().map(Command::name).toList() : List.of();
    }
}
