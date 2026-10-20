package dev.noir.module;

import dev.noir.NoirCore;
import dev.noir.event.EventBus;
import dev.noir.event.Events;
import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.EnumSetting;
import dev.noir.module.settings.KeybindSetting;
import dev.noir.module.settings.SettingGroup;
import dev.noir.module.settings.SettingsHolder;
import dev.noir.network.ActionClient;
import dev.noir.util.Keys;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Base class for every feature.
 *
 * <p>Modules never talk to the network. Event handlers registered with {@link #on}
 * only run while the module is enabled, and they run inside the module's packet
 * attribution context, so anything they cause on the wire is traced back to them.
 * Actions that need packets go through {@link #actions()}.
 */
public abstract class Module extends SettingsHolder {
    private final Category category;
    private final SettingGroup bindGroup = new SettingGroup("Bind", "Keybind and list display for this module.").collapsedByDefault();
    public final KeybindSetting keybind;
    public final EnumSetting<BindMode> bindMode;
    public final BoolSetting showInArrayList;

    private final List<Handler<?>> handlers = new ArrayList<>();
    private final List<EventBus.Subscription> subscriptions = new ArrayList<>();
    private boolean enabled;
    private NoirCore core;

    protected Module(String name, Category category, String description) {
        super(name, description);
        this.category = category;
        this.keybind = addTo(bindGroup, new KeybindSetting("Keybind", Keys.NONE, "Key that toggles (or, in Hold mode, activates) this module."));
        this.bindMode = addTo(bindGroup, new EnumSetting<>("Bind Mode", BindMode.TOGGLE, "Toggle: press to switch on/off. Hold: on only while the key is held."));
        this.showInArrayList = addTo(bindGroup, new BoolSetting("Show In ArrayList", true, "List this module in the HUD ArrayList while it is enabled."));
    }

    @Override
    public List<SettingGroup> groups() {
        List<SettingGroup> all = new ArrayList<>(super.groups());
        all.add(bindGroup);
        return all;
    }

    public final Category category() {
        return category;
    }

    public final boolean isReadOnly() {
        return category.readOnly();
    }

    public final boolean isEnabled() {
        return enabled;
    }

    /** Called once by the module manager. */
    final void bind(NoirCore core) {
        if (this.core != null) throw new IllegalStateException(name() + " registered twice");
        this.core = core;
    }

    protected final NoirCore core() {
        return core;
    }

    /** Packet-free action requests. For read-only modules this handle refuses everything. */
    protected final ActionClient actions() {
        return core.packets().client(this);
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    public final void setEnabled(boolean on) {
        if (on == enabled || core == null) return;
        enabled = on;
        if (on) {
            for (Handler<?> h : handlers) subscriptions.add(h.subscribe());
            try {
                core.packets().runAs(this, this::onEnable);
            } catch (Throwable t) {
                core.platform().logError(name() + " failed to enable", t);
                setEnabled(false);
                return;
            }
        } else {
            try {
                core.packets().runAs(this, this::onDisable);
            } catch (Throwable t) {
                core.platform().logError(name() + " failed to disable cleanly", t);
            }
            subscriptions.forEach(EventBus.Subscription::unsubscribe);
            subscriptions.clear();
            core.packets().release(this);
        }
        core.events().post(new Events.ModuleToggle(this, on));
    }

    protected void onEnable() {}

    protected void onDisable() {}

    /** Optional suffix shown next to the name in the ArrayList (e.g. current mode). */
    public String info() {
        return null;
    }

    protected final <E> void on(Class<E> type, Consumer<E> handler) {
        on(type, 0, handler);
    }

    protected final <E> void on(Class<E> type, int priority, Consumer<E> handler) {
        handlers.add(new Handler<>(type, priority, handler));
    }

    private final class Handler<E> {
        final Class<E> type;
        final int priority;
        final Consumer<E> handler;

        Handler(Class<E> type, int priority, Consumer<E> handler) {
            this.type = type;
            this.priority = priority;
            this.handler = handler;
        }

        EventBus.Subscription subscribe() {
            return core.events().subscribe(type, priority, e -> core.packets().runAs(Module.this, () -> handler.accept(e)));
        }
    }
}
