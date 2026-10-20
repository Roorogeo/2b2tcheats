package dev.noir.command;

import dev.noir.NoirCore;

import java.util.List;

public abstract class Command {
    private final String name;
    private final List<String> aliases;
    private final String usage;
    private final String description;

    protected Command(String name, List<String> aliases, String usage, String description) {
        this.name = name;
        this.aliases = aliases;
        this.usage = usage;
        this.description = description;
    }

    public String name() { return name; }

    public List<String> aliases() { return aliases; }

    public String usage() { return usage; }

    public String description() { return description; }

    /** Runs the command. Throw {@link CommandException} with a user-facing message on bad input. */
    public abstract void execute(NoirCore core, String[] args) throws CommandException;

    /** Suggestions for the argument at {@code index} given the earlier args. */
    public List<String> suggest(NoirCore core, String[] args, int index) {
        return List.of();
    }

    protected static void require(boolean condition, String message) throws CommandException {
        if (!condition) throw new CommandException(message);
    }
}
