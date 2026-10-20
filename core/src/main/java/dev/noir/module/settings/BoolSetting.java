package dev.noir.module.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

public final class BoolSetting extends Setting<Boolean, BoolSetting> {
    public BoolSetting(String name, boolean defaultValue, String description) {
        super(name, description, defaultValue);
    }

    public boolean isOn() {
        return get();
    }

    public void toggle() {
        set(!rawValue());
    }

    @Override
    protected Boolean strictFallback() {
        // A blocked "on" falls back to "off" regardless of default.
        return isStrictBlocked(defaultValue()) ? Boolean.FALSE : defaultValue();
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(rawValue());
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json != null && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) set(json.getAsBoolean());
    }

    @Override
    public Boolean preview(String text) {
        String s = text.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "on", "true", "yes", "1", "enable", "enabled" -> true;
            case "off", "false", "no", "0", "disable", "disabled" -> false;
            case "toggle" -> !rawValue();
            default -> null;
        };
    }

    @Override
    public String displayValue(Boolean v) {
        return v ? "on" : "off";
    }

    @Override
    public String typeName() {
        return "on|off";
    }
}
