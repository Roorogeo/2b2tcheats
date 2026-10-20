package dev.noir.module.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.noir.util.Colors;

/** An ARGB color, alpha included. Stored as "#AARRGGBB". */
public final class ColorSetting extends Setting<Integer, ColorSetting> {
    public ColorSetting(String name, int defaultArgb, String description) {
        super(name, description, defaultArgb);
    }

    public int argb() {
        return get();
    }

    public int alpha() {
        return Colors.alpha(get());
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(Colors.toHex(rawValue()));
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonPrimitive()) return;
        Integer c = Colors.parse(json.getAsString());
        if (c != null) set(c);
    }

    @Override
    public Integer preview(String text) {
        return Colors.parse(text);
    }

    @Override
    public String displayValue(Integer v) {
        return Colors.toHex(v);
    }

    @Override
    public String typeName() {
        return "#AARRGGBB";
    }
}
