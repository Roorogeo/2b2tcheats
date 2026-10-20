package dev.noir.module.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.noir.util.Keys;

/** A key or mouse button. {@link Keys#NONE} means unbound. */
public final class KeybindSetting extends Setting<Integer, KeybindSetting> {
    public KeybindSetting(String name, int defaultKey, String description) {
        super(name, description, defaultKey);
    }

    public int code() {
        return get();
    }

    public boolean isBound() {
        return get() != Keys.NONE;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(Keys.name(rawValue()));
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) return;
        Integer k = Keys.parse(json.getAsString());
        if (k != null) set(k);
    }

    @Override
    public Integer preview(String text) {
        return Keys.parse(text);
    }

    @Override
    public String displayValue(Integer v) {
        return Keys.name(v);
    }

    @Override
    public String typeName() {
        return "key";
    }
}
