package dev.noir.module.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Anything with named settings: modules, HUD elements, client settings, the theme.
 * Provides the fluent setting builders and JSON (de)serialization.
 */
public abstract class SettingsHolder {
    private final String name;
    private final String description;
    private final List<SettingGroup> groups = new ArrayList<>();
    private final Map<String, Setting<?, ?>> byKey = new HashMap<>();
    private SettingGroup current;

    protected SettingsHolder(String name, String description) {
        this.name = name;
        this.description = description;
        this.current = new SettingGroup("", "");
        groups.add(current);
    }

    public final String name() { return name; }

    public final String description() { return description; }

    /** Groups in display order. */
    public List<SettingGroup> groups() { return groups; }

    public final List<Setting<?, ?>> settings() {
        List<Setting<?, ?>> all = new ArrayList<>();
        for (SettingGroup g : groups()) all.addAll(g.settings());
        return all;
    }

    /** Finds a setting by name, case/space/punctuation-insensitive. */
    public final Setting<?, ?> setting(String nameOrKey) {
        return byKey.get(Setting.normalize(nameOrKey));
    }

    /** Starts a new collapsible group; subsequent settings go into it. */
    protected final SettingGroup group(String groupName, String groupDescription) {
        current = new SettingGroup(groupName, groupDescription);
        groups.add(current);
        return current;
    }

    protected final <S extends Setting<?, ?>> S add(S setting) {
        return addTo(current, setting);
    }

    protected final <S extends Setting<?, ?>> S addTo(SettingGroup group, S setting) {
        if (byKey.putIfAbsent(setting.key(), setting) != null) {
            throw new IllegalStateException(name + ": duplicate setting " + setting.name());
        }
        group.add(setting);
        return setting;
    }

    protected final BoolSetting bool(String n, boolean def, String desc) {
        return add(new BoolSetting(n, def, desc));
    }

    protected final NumberSetting number(String n, double def, double min, double max, double step, String desc) {
        return add(new NumberSetting(n, def, min, max, step, desc));
    }

    protected final <E extends Enum<E>> EnumSetting<E> mode(String n, E def, String desc) {
        return add(new EnumSetting<>(n, def, desc));
    }

    protected final ColorSetting color(String n, int argb, String desc) {
        return add(new ColorSetting(n, argb, desc));
    }

    protected final KeybindSetting keybind(String n, int key, String desc) {
        return add(new KeybindSetting(n, key, desc));
    }

    protected final StringSetting text(String n, String def, int maxLength, String desc) {
        return add(new StringSetting(n, def, maxLength, desc));
    }

    protected final BlockListSetting blocks(String n, int entryColor, String desc, String... ids) {
        return add(new BlockListSetting(n, idMap(entryColor, ids), entryColor, desc));
    }

    protected final EntityListSetting entities(String n, int entryColor, String desc, String... ids) {
        return add(new EntityListSetting(n, idMap(entryColor, ids), entryColor, desc));
    }

    private static Map<String, Integer> idMap(int color, String... ids) {
        Map<String, Integer> m = new LinkedHashMap<>();
        Arrays.stream(ids).map(IdListSetting::normalizeId).forEach(id -> m.put(id, color));
        return m;
    }

    public void resetSettings() {
        for (Setting<?, ?> s : settings()) s.reset();
    }

    /** Calls {@code listener} whenever any setting changes. */
    public final void onAnySettingChange(Runnable listener) {
        for (Setting<?, ?> s : settings()) s.onChange(v -> listener.run());
    }

    public JsonObject settingsToJson() {
        JsonObject o = new JsonObject();
        for (Setting<?, ?> s : settings()) o.add(s.key(), s.toJson());
        return o;
    }

    public void settingsFromJson(JsonObject o) {
        if (o == null) return;
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            Setting<?, ?> s = setting(e.getKey());
            if (s != null) s.fromJson(e.getValue());
        }
    }
}
