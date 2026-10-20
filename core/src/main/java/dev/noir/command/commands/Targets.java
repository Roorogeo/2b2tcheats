package dev.noir.command.commands;

import dev.noir.NoirCore;
import dev.noir.command.CommandException;
import dev.noir.hud.HudElement;
import dev.noir.module.Module;
import dev.noir.module.settings.Setting;
import dev.noir.module.settings.SettingsHolder;

import java.util.ArrayList;
import java.util.List;

/** Resolves command arguments to modules and settings holders. */
final class Targets {
    private Targets() {}

    static Module module(NoirCore core, String name) throws CommandException {
        Module m = core.modules().find(name);
        if (m == null) throw new CommandException("No module named \"" + name + "\".");
        return m;
    }

    /** A module, "client", "theme", or "hud.&lt;element&gt;". */
    static SettingsHolder holder(NoirCore core, String name) throws CommandException {
        String key = Setting.normalize(name);
        if (key.equals("client")) return core.client();
        if (key.equals("theme")) return core.theme();
        if (name.toLowerCase().startsWith("hud.")) {
            HudElement e = core.hud().find(name.substring(4));
            if (e == null) throw new CommandException("No HUD element named \"" + name.substring(4) + "\".");
            return e;
        }
        return module(core, name);
    }

    static List<String> holderNames(NoirCore core) {
        List<String> out = new ArrayList<>();
        for (Module m : core.modules().all()) out.add(m.name().replace(" ", ""));
        out.add("client");
        out.add("theme");
        for (HudElement e : core.hud().elements()) out.add("hud." + e.id());
        return out;
    }

    static List<String> moduleNames(NoirCore core) {
        List<String> out = new ArrayList<>();
        for (Module m : core.modules().all()) out.add(m.name().replace(" ", ""));
        return out;
    }

    static String join(String[] args, int from) {
        StringBuilder b = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) b.append(' ');
            b.append(args[i]);
        }
        return b.toString();
    }
}
