package dev.noir.module.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class StringSetting extends Setting<String, StringSetting> {
    private final int maxLength;

    public StringSetting(String name, String defaultValue, int maxLength, String description) {
        super(name, description, defaultValue);
        this.maxLength = maxLength;
    }

    public int maxLength() {
        return maxLength;
    }

    @Override
    protected String sanitize(String v) {
        String s = v.replace('\n', ' ').replace('\r', ' ');
        return s.length() > maxLength ? s.substring(0, maxLength) : s;
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(rawValue());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) set(json.getAsString());
    }

    @Override
    public String preview(String text) {
        return sanitize(text);
    }

    @Override
    public String displayValue(String v) {
        return v;
    }

    @Override
    public String typeName() {
        return "text";
    }
}
