package dev.noir.module.settings;

import com.google.gson.JsonElement;
import dev.noir.network.StrictMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A typed, persisted, user-editable value.
 *
 * <p>{@link #get()} is the <em>effective</em> value: if Strict mode is on and the stored
 * value would need non-vanilla packets, a safe fallback is returned instead, so a
 * config saved with Strict mode off can never leak a blocked behaviour once it's on.
 *
 * @param <T> value type
 * @param <S> self type, so fluent configuration keeps the concrete setting type
 */
public abstract class Setting<T, S extends Setting<T, S>> {
    private final String name;
    private final String description;
    private final T defaultValue;
    private T value;
    private BooleanSupplier visibility = () -> true;
    private SettingGroup group;
    private Predicate<T> strictBlocked;
    private String strictReason;
    private final List<Consumer<T>> listeners = new ArrayList<>(2);

    protected Setting(String name, String description, T defaultValue) {
        this.name = Objects.requireNonNull(name);
        this.description = Objects.requireNonNull(description);
        this.defaultValue = Objects.requireNonNull(defaultValue);
        this.value = defaultValue;
    }

    public final String name() { return name; }

    /** Tooltip text. Every setting has one. */
    public final String description() { return description; }

    public final T defaultValue() { return defaultValue; }

    /** Normalized lookup key: lowercase alphanumerics only ("Attack Delay" -> "attackdelay"). */
    public final String key() { return normalize(name); }

    /** The effective value (respecting Strict mode). */
    public T get() {
        T v = value;
        return isStrictBlocked(v) ? strictFallback() : v;
    }

    /** The stored value, ignoring Strict mode. Used by config and the GUI. */
    public final T rawValue() { return value; }

    /** Stores a value (sanitized). Returns true if it changed. Does not check Strict mode; callers that take user input should. */
    public final boolean set(T newValue) {
        if (newValue == null) return false;
        T s = sanitize(newValue);
        if (Objects.equals(s, value)) return false;
        value = s;
        for (Consumer<T> l : listeners) l.accept(s);
        return true;
    }

    public final void reset() { set(defaultValue); }

    public final boolean isDefault() { return Objects.equals(value, defaultValue); }

    protected T sanitize(T v) { return v; }

    /** Value used when the stored one is Strict-blocked. Defaults to the default value. */
    protected T strictFallback() { return defaultValue; }

    @SuppressWarnings("unchecked")
    protected final S self() { return (S) this; }

    public final S onChange(Consumer<T> listener) {
        listeners.add(listener);
        return self();
    }

    /** Shows this setting only while {@code condition} holds. */
    public final S visibleWhen(BooleanSupplier condition) {
        this.visibility = condition;
        return self();
    }

    /** Shows this setting only while {@code parent} is on. */
    public final S showWhen(BoolSetting parent) {
        return visibleWhen(parent::get);
    }

    /**
     * Declares values that need non-vanilla packets. While Strict mode is on they are
     * greyed out in the GUI, refused by commands, and replaced by {@link #strictFallback()}.
     */
    public final S strict(Predicate<T> blockedWhen, String reason) {
        this.strictBlocked = blockedWhen;
        this.strictReason = reason;
        return self();
    }

    public final boolean hasStrictRule() { return strictBlocked != null; }

    /** Whether {@code candidate} is currently blocked by Strict mode. */
    public final boolean isStrictBlocked(T candidate) {
        return strictBlocked != null && candidate != null && StrictMode.enabled() && strictBlocked.test(candidate);
    }

    public final String strictReason() { return strictReason; }

    public boolean isVisible() {
        return visibility.getAsBoolean() && (group == null || group.isVisible());
    }

    final void attach(SettingGroup g) {
        if (this.group != null) throw new IllegalStateException("Setting " + name + " already belongs to group " + group.name());
        this.group = g;
    }

    public final SettingGroup group() { return group; }

    /** Serializes the stored (raw) value. */
    public abstract JsonElement toJson();

    /** Loads a value, ignoring malformed input so a bad config never crashes the client. */
    public abstract void fromJson(JsonElement json);

    /** Parses user text without applying it. Returns null when the text is not valid for this setting. */
    public abstract T preview(String text);

    /** Parses and applies user text (".set" command). Returns false when invalid. */
    public final boolean parse(String text) {
        T v = preview(text);
        if (v == null) return false;
        set(v);
        return true;
    }

    /** Human-readable stored value. */
    public final String display() {
        return displayValue(value);
    }

    /** Human-readable form of any value of this setting. */
    public abstract String displayValue(T v);

    /** Whether the stored value is currently replaced because of Strict mode. */
    public final boolean isCurrentlyBlockedRaw() {
        return isStrictBlocked(value);
    }

    /** Short type label used in help output. */
    public abstract String typeName();

    public static String normalize(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(c)) b.append(c);
        }
        return b.toString();
    }

    @Override
    public String toString() {
        return name + "=" + display();
    }
}
