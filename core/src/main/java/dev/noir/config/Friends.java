package dev.noir.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Player names that combat modules skip. Case-insensitive; original casing kept for display. */
public final class Friends {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private final Map<String, String> names = new LinkedHashMap<>();
    private Runnable onChange = () -> {};

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    void onChange(Runnable r) {
        this.onChange = r;
    }

    public synchronized boolean add(String name) {
        if (!isValidName(name)) return false;
        if (names.putIfAbsent(name.toLowerCase(Locale.ROOT), name) != null) return false;
        onChange.run();
        return true;
    }

    public synchronized boolean remove(String name) {
        if (name == null || names.remove(name.toLowerCase(Locale.ROOT)) == null) return false;
        onChange.run();
        return true;
    }

    public synchronized boolean isFriend(String name) {
        return name != null && names.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public synchronized List<String> list() {
        return Collections.unmodifiableList(new ArrayList<>(names.values()));
    }

    public synchronized void clear() {
        if (names.isEmpty()) return;
        names.clear();
        onChange.run();
    }

    synchronized void replaceAll(List<String> list) {
        names.clear();
        for (String n : list) if (isValidName(n)) names.putIfAbsent(n.toLowerCase(Locale.ROOT), n);
    }
}
