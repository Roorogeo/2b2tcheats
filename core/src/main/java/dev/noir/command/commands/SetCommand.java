package dev.noir.command.commands;

import dev.noir.NoirCore;
import dev.noir.command.Command;
import dev.noir.command.CommandException;
import dev.noir.module.settings.Setting;
import dev.noir.module.settings.SettingsHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code .set <module|client|theme|hud.element> <setting> <value>}. Setting names are
 * matched ignoring case, spaces and punctuation ("attackdelay", "Attack_Delay").
 * "reset" restores the default. Strict-blocked values are refused with the reason.
 */
public final class SetCommand extends Command {
    public SetCommand() {
        super("set", List.of("s"), "set <module> <setting> [value|reset]",
            "Changes a setting. Without a value, shows it. Use client/theme/hud.<element> for non-module settings.");
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void execute(NoirCore core, String[] args) throws CommandException {
        require(args.length >= 1, "Which module?");
        SettingsHolder holder = Targets.holder(core, args[0]);
        if (args.length == 1) {
            core.platform().chat(holder.name() + " settings:");
            for (Setting<?, ?> s : holder.settings()) {
                core.platform().chat("  " + s.key() + " = " + s.display() + "  (" + s.typeName() + ")");
            }
            return;
        }
        Setting setting = holder.setting(args[1]);
        require(setting != null, holder.name() + " has no setting \"" + args[1] + "\".");
        if (args.length == 2) {
            core.platform().chat(holder.name() + " " + setting.name() + " = " + setting.display()
                + " (" + setting.typeName() + "). " + setting.description());
            return;
        }
        String value = Targets.join(args, 2);
        if (value.equalsIgnoreCase("reset") || value.equalsIgnoreCase("default")) {
            setting.reset();
            core.platform().chat(holder.name() + " " + setting.name() + " reset to " + setting.display() + ".");
            return;
        }
        Object parsed = setting.preview(value);
        require(parsed != null, "\"" + value + "\" is not valid for " + setting.name() + " (" + setting.typeName() + ").");
        if (setting.isStrictBlocked(parsed)) {
            throw new CommandException("Blocked by Strict mode: " + setting.strictReason());
        }
        setting.set(parsed);
        core.platform().chat(holder.name() + " " + setting.name() + " = " + setting.display() + ".");
    }

    @Override
    public List<String> suggest(NoirCore core, String[] args, int index) {
        if (index == 0) return Targets.holderNames(core);
        if (index == 1) {
            try {
                List<String> out = new ArrayList<>();
                for (Setting<?, ?> s : Targets.holder(core, args[0]).settings()) out.add(s.key());
                return out;
            } catch (CommandException e) {
                return List.of();
            }
        }
        return List.of();
    }
}
