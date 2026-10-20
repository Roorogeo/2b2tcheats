package dev.noir.module.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** One of a fixed set of modes. Enum constants should override toString() with a display name. */
public final class EnumSetting<E extends Enum<E>> extends Setting<E, EnumSetting<E>> {
    private final List<E> options;

    public EnumSetting(String name, E defaultValue, String description) {
        super(name, description, defaultValue);
        this.options = List.of(defaultValue.getDeclaringClass().getEnumConstants());
    }

    public List<E> options() { return options; }

    /** Blocks the given modes in Strict mode (e.g. packet-based modes). */
    @SafeVarargs
    public final EnumSetting<E> strictValues(String reason, E... blocked) {
        Set<E> set = EnumSet.copyOf(Arrays.asList(blocked));
        return strict(set::contains, reason);
    }

    public boolean isOptionBlocked(E option) {
        return isStrictBlocked(option);
    }

    @Override
    protected E strictFallback() {
        if (!isStrictBlocked(defaultValue())) return defaultValue();
        for (E e : options) if (!isStrictBlocked(e)) return e;
        return rawValue();
    }

    /** Steps to the next (dir=1) or previous (dir=-1) mode, skipping Strict-blocked ones. */
    public void cycle(int dir) {
        int n = options.size();
        int i = rawValue().ordinal();
        for (int k = 0; k < n; k++) {
            i = Math.floorMod(i + (dir >= 0 ? 1 : -1), n);
            E candidate = options.get(i);
            if (!isStrictBlocked(candidate)) {
                set(candidate);
                return;
            }
        }
    }

    public boolean is(E e) {
        return get() == e;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(rawValue().name());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) return;
        E e = find(json.getAsString());
        if (e != null) set(e);
    }

    public E find(String text) {
        String key = normalize(text);
        for (E e : options) {
            if (normalize(e.name()).equals(key) || normalize(e.toString()).equals(key)) return e;
        }
        return null;
    }

    @Override
    public E preview(String text) {
        return find(text);
    }

    @Override
    public String displayValue(E v) {
        return v.toString();
    }

    @Override
    public String typeName() {
        return options.stream().map(Object::toString).collect(Collectors.joining("|"));
    }
}
