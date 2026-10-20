package dev.noir.command.commands;

import dev.noir.NoirCore;
import dev.noir.command.Command;
import dev.noir.command.CommandException;
import dev.noir.config.Friends;

import java.util.List;
import java.util.Locale;

public final class FriendCommand extends Command {
    public FriendCommand() {
        super("friend", List.of("f", "friends"), "friend <add|remove> <name> | friend <list|clear>",
            "Manages the friends list. Combat modules never target friends.");
    }

    @Override
    public void execute(NoirCore core, String[] args) throws CommandException {
        require(args.length >= 1, "Missing action.");
        Friends friends = core.friends();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                require(args.length >= 2, "Which player?");
                require(Friends.isValidName(args[1]), "\"" + args[1] + "\" is not a valid player name.");
                require(friends.add(args[1]), args[1] + " is already a friend.");
                core.platform().chat("Added " + args[1] + " to friends.");
            }
            case "remove", "del" -> {
                require(args.length >= 2, "Which player?");
                require(friends.remove(args[1]), args[1] + " is not a friend.");
                core.platform().chat("Removed " + args[1] + " from friends.");
            }
            case "list" -> {
                List<String> l = friends.list();
                core.platform().chat(l.isEmpty() ? "No friends added." : "Friends (" + l.size() + "): " + String.join(", ", l));
            }
            case "clear" -> {
                friends.clear();
                core.platform().chat("Friends list cleared.");
            }
            default -> throw new CommandException("Unknown action \"" + args[0] + "\".");
        }
    }

    @Override
    public List<String> suggest(NoirCore core, String[] args, int index) {
        if (index == 0) return List.of("add", "remove", "list", "clear");
        if (index == 1 && args[0].equalsIgnoreCase("remove")) return core.friends().list();
        return List.of();
    }
}
