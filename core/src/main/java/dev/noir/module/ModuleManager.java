package dev.noir.module;

import dev.noir.NoirCore;
import dev.noir.module.settings.Setting;
import dev.noir.util.Keys;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModuleManager {
    private final NoirCore core;
    private final Map<String, Module> byKey = new LinkedHashMap<>();
    private final List<Module> modules = new ArrayList<>();

    public ModuleManager(NoirCore core) {
        this.core = core;
    }

    public void register(Module module) {
        String key = Setting.normalize(module.name());
        if (byKey.containsKey(key)) throw new IllegalStateException("Duplicate module " + module.name());
        module.bind(core);
        byKey.put(key, module);
        modules.add(module);
    }

    public List<Module> all() {
        return Collections.unmodifiableList(modules);
    }

    public List<Module> byCategory(Category category) {
        List<Module> out = new ArrayList<>();
        for (Module m : modules) if (m.category() == category) out.add(m);
        return out;
    }

    public List<Module> enabled() {
        List<Module> out = new ArrayList<>();
        for (Module m : modules) if (m.isEnabled()) out.add(m);
        return out;
    }

    /** Case/space-insensitive lookup ("killaura", "Kill Aura", "KillAura"). */
    public Module find(String name) {
        return byKey.get(Setting.normalize(name));
    }

    @SuppressWarnings("unchecked")
    public <M extends Module> M get(Class<M> type) {
        for (Module m : modules) if (type.isInstance(m)) return (M) m;
        return null;
    }

    /**
     * Handles a key for module binds. Binds don't fire while a screen is open
     * (typing in chat or the GUI must not toggle modules); hold-mode releases still apply.
     */
    public void onKey(int key, int action, boolean inScreen) {
        if (key == Keys.NONE || action == Keys.ACTION_REPEAT) return;
        for (Module m : modules) {
            if (m.keybind.code() != key) continue;
            if (m.bindMode.get() == BindMode.HOLD) {
                if (action == Keys.ACTION_PRESS && !inScreen) m.setEnabled(true);
                else if (action == Keys.ACTION_RELEASE) m.setEnabled(false);
            } else if (action == Keys.ACTION_PRESS && !inScreen) {
                m.toggle();
            }
        }
    }

    public void disableAll() {
        for (Module m : modules) m.setEnabled(false);
    }
}
