package dev.noir.command;

import dev.noir.NoirCore;
import dev.noir.command.commands.BindCommand;
import dev.noir.command.commands.ConfigCommand;
import dev.noir.command.commands.FriendCommand;
import dev.noir.command.commands.HelpCommand;
import dev.noir.command.commands.HudCommand;
import dev.noir.command.commands.SetCommand;
import dev.noir.command.commands.ToggleCommand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Local chat commands. {@link #handle} must be called for every outgoing chat message;
 * when it returns true the message is a command and must not be sent.
 */
public final class CommandManager {
    private final NoirCore core;
    private final Map<String, Command> byName = new LinkedHashMap<>();
    private final List<Command> commands = new ArrayList<>();

    public CommandManager(NoirCore core) {
        this.core = core;
        register(new HelpCommand());
        register(new ToggleCommand());
        register(new BindCommand());
        register(new SetCommand());
        register(new ConfigCommand());
        register(new FriendCommand());
        register(new HudCommand());
    }

    public void register(Command c) {
        commands.add(c);
        byName.put(c.name(), c);
        for (String a : c.aliases()) byName.putIfAbsent(a, c);
    }

    public List<Command> all() {
        return Collections.unmodifiableList(commands);
    }

    public Command find(String name) {
        return byName.get(name.toLowerCase(Locale.ROOT));
    }

    public String prefix() {
        String p = core.client().commandPrefix.get();
        return p.isBlank() ? "." : p;
    }

    public boolean isCommand(String message) {
        return message != null && message.startsWith(prefix());
    }

    /**
     * Returns true if {@code message} was a command (handled locally, even if it
     * failed). A command message is never sent to the server.
     */
    public boolean handle(String message) {
        if (!isCommand(message)) return false;
        String body = message.substring(prefix().length()).trim();
        if (body.isEmpty()) {
            core.platform().chat("Type " + prefix() + "help for commands.");
            return true;
        }
        String[] parts = body.split("\\s+");
        Command c = find(parts[0]);
        if (c == null) {
            core.platform().chat("Unknown command \"" + parts[0] + "\". Type " + prefix() + "help.");
            return true;
        }
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        try {
            c.execute(core, args);
        } catch (CommandException e) {
            core.platform().chat(e.getMessage());
            core.platform().chat("Usage: " + prefix() + c.usage());
        } catch (RuntimeException e) {
            core.platform().logError("Command " + c.name() + " failed", e);
            core.platform().chat("Command failed: " + e.getMessage());
        }
        return true;
    }

    /** Completions for a partially typed command line (without the prefix). */
    public List<String> suggest(String body) {
        String[] parts = body.split("\\s+", -1);
        List<String> out = new ArrayList<>();
        if (parts.length <= 1) {
            String p = parts.length == 0 ? "" : parts[0].toLowerCase(Locale.ROOT);
            for (Command c : commands) if (c.name().startsWith(p)) out.add(c.name());
            return out;
        }
        Command c = find(parts[0]);
        if (c == null) return out;
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        for (String s : c.suggest(core, args, args.length - 1)) {
            if (s.toLowerCase(Locale.ROOT).startsWith(last)) out.add(s);
        }
        return out;
    }
}
