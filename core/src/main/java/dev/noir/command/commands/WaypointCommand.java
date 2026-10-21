package dev.noir.command.commands;

import dev.noir.NoirCore;
import dev.noir.basehunt.WaypointStore;
import dev.noir.command.Command;
import dev.noir.command.CommandException;
import dev.noir.module.basehunting.Waypoints;

import java.util.List;
import java.util.Locale;

public final class WaypointCommand extends Command {
    public WaypointCommand() {
        super("waypoint", List.of("wp"), "waypoint add <name> <x> <y> <z> | remove <name> | list | clear",
            "Manages local waypoints (stored per server, never sent).");
    }

    @Override
    public void execute(NoirCore core, String[] args) throws CommandException {
        require(args.length >= 1, "Missing action.");
        Waypoints module = core.modules().get(Waypoints.class);
        WaypointStore store = core.baseHunt().waypoints();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                require(module != null, "The Waypoints module is not available.");
                require(args.length >= 5, "Usage: waypoint add <name> <x> <y> <z>");
                try {
                    int x = Integer.parseInt(args[2]), y = Integer.parseInt(args[3]), z = Integer.parseInt(args[4]);
                    require(module.addManual(args[1], x, y, z), "Could not add waypoint.");
                    core.platform().chat("Waypoint " + args[1] + " added at " + x + ", " + y + ", " + z + ".");
                } catch (NumberFormatException e) {
                    throw new CommandException("Coordinates must be whole numbers.");
                }
            }
            case "remove", "del" -> {
                require(args.length >= 2, "Which waypoint?");
                require(store.remove(args[1]), "No waypoint named " + args[1] + ".");
                core.platform().chat("Removed waypoint " + args[1] + ".");
            }
            case "list" -> {
                List<WaypointStore.Waypoint> all = store.all();
                if (all.isEmpty()) core.platform().chat("No waypoints.");
                for (WaypointStore.Waypoint w : all) {
                    core.platform().chat("  " + w.name() + " (" + w.type() + ", " + w.dimension() + ") " + w.x() + ", " + w.y() + ", " + w.z());
                }
            }
            case "clear" -> {
                store.clear();
                core.platform().chat("Waypoints cleared.");
            }
            default -> throw new CommandException("Unknown action \"" + args[0] + "\".");
        }
    }

    @Override
    public List<String> suggest(NoirCore core, String[] args, int index) {
        if (index == 0) return List.of("add", "remove", "list", "clear");
        if (index == 1 && args[0].equalsIgnoreCase("remove")) {
            return core.baseHunt().waypoints().all().stream().map(WaypointStore.Waypoint::name).toList();
        }
        return List.of();
    }
}
