package dev.noir.module.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.noir.util.Colors;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * An ordered set of registry ids (blocks or entity types), each with its own color so
 * render modules can color per entry. The value is an unmodifiable map; edits replace it.
 */
public abstract class IdListSetting<S extends IdListSetting<S>> extends Setting<Map<String, Integer>, S> {
    private final int defaultEntryColor;

    protected IdListSetting(String name, Map<String, Integer> defaults, int defaultEntryColor, String description) {
        super(name, description, Collections.unmodifiableMap(new LinkedHashMap<>(defaults)));
        this.defaultEntryColor = defaultEntryColor;
    }

    /** Registry check installed by the Minecraft layer; accepts everything until then. */
    protected abstract Predicate<String> validator();

    public int defaultEntryColor() { return defaultEntryColor; }

    public Set<String> ids() { return get().keySet(); }

    public boolean contains(String id) { return get().containsKey(normalizeId(id)); }

    public int colorOf(String id) {
        Integer c = get().get(normalizeId(id));
        return c != null ? c : defaultEntryColor;
    }

    public int size() { return get().size(); }

    /** Adds or recolors an entry. Returns false if the id is not valid. */
    public boolean put(String id, int color) {
        String n = normalizeId(id);
        if (n.isEmpty() || !validator().test(n)) return false;
        Map<String, Integer> m = new LinkedHashMap<>(rawValue());
        m.put(n, color);
        set(Collections.unmodifiableMap(m));
        return true;
    }

    public boolean add(String id) {
        return put(id, contains(id) ? colorOf(id) : defaultEntryColor);
    }

    public boolean remove(String id) {
        String n = normalizeId(id);
        if (!rawValue().containsKey(n)) return false;
        Map<String, Integer> m = new LinkedHashMap<>(rawValue());
        m.remove(n);
        set(Collections.unmodifiableMap(m));
        return true;
    }

    public void clear() {
        set(Collections.emptyMap());
    }

    public static String normalizeId(String id) {
        String s = id.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return s;
        return s.indexOf(':') >= 0 ? s : "minecraft:" + s;
    }

    @Override
    protected Map<String, Integer> sanitize(Map<String, Integer> v) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(v));
    }

    @Override
    public JsonElement toJson() {
        JsonObject o = new JsonObject();
        rawValue().forEach((id, c) -> o.addProperty(id, Colors.toHex(c)));
        return o;
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null) return;
        Map<String, Integer> m = new LinkedHashMap<>();
        if (json.isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject().entrySet()) {
                Integer c = e.getValue().isJsonPrimitive() ? Colors.parse(e.getValue().getAsString()) : null;
                m.put(normalizeId(e.getKey()), c != null ? c : defaultEntryColor);
            }
        } else if (json.isJsonArray()) {
            JsonArray a = json.getAsJsonArray();
            for (JsonElement e : a) if (e.isJsonPrimitive()) m.put(normalizeId(e.getAsString()), defaultEntryColor);
        } else {
            return;
        }
        m.keySet().removeIf(id -> id.isEmpty() || !validator().test(id));
        set(m);
    }

    /**
     * Accepts "add &lt;id&gt; [color]", "remove &lt;id&gt;", "clear", or a comma separated list
     * that replaces the contents. Returns the resulting list, or null if the text is invalid.
     */
    @Override
    public Map<String, Integer> preview(String text) {
        String t = text.trim();
        if (t.isEmpty()) return null;
        String[] parts = t.split("\\s+");
        String verb = parts[0].toLowerCase(Locale.ROOT);
        Map<String, Integer> m = new LinkedHashMap<>(rawValue());
        switch (verb) {
            case "add" -> {
                if (parts.length < 2) return null;
                String id = normalizeId(parts[1]);
                if (id.isEmpty() || !validator().test(id)) return null;
                Integer color = parts.length >= 3 ? Colors.parse(parts[2]) : m.getOrDefault(id, defaultEntryColor);
                if (color == null) return null;
                m.put(id, color);
            }
            case "remove", "del", "delete" -> {
                if (parts.length < 2 || m.remove(normalizeId(parts[1])) == null) return null;
            }
            case "clear" -> m.clear();
            default -> {
                Map<String, Integer> replaced = new LinkedHashMap<>();
                for (String raw : t.split(",")) {
                    String id = normalizeId(raw);
                    if (id.isEmpty()) continue;
                    if (!validator().test(id)) return null;
                    replaced.put(id, m.getOrDefault(id, defaultEntryColor));
                }
                m = replaced;
            }
        }
        return Collections.unmodifiableMap(m);
    }

    @Override
    public String displayValue(Map<String, Integer> v) {
        return String.join(", ", v.keySet());
    }

    @Override
    public String typeName() {
        return "add <id> [#color] | remove <id> | clear | id,id,...";
    }
}
