package dev.noir.module.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A collapsible section of settings. The unnamed root group of a holder has no header.
 * A group can be shown only while a parent setting is on ({@link #showWhen}).
 */
public final class SettingGroup {
    private final String name;
    private final String description;
    private final List<Setting<?, ?>> settings = new ArrayList<>();
    private BooleanSupplier visibility = () -> true;
    private boolean collapsed;

    public SettingGroup(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String name() { return name; }

    public String description() { return description; }

    public boolean isRoot() { return name.isEmpty(); }

    public List<Setting<?, ?>> settings() { return Collections.unmodifiableList(settings); }

    public <S extends Setting<?, ?>> S add(S setting) {
        setting.attach(this);
        settings.add(setting);
        return setting;
    }

    public SettingGroup showWhen(BoolSetting parent) {
        this.visibility = parent::get;
        return this;
    }

    public SettingGroup visibleWhen(BooleanSupplier condition) {
        this.visibility = condition;
        return this;
    }

    public SettingGroup collapsedByDefault() {
        this.collapsed = true;
        return this;
    }

    public boolean isVisible() { return visibility.getAsBoolean(); }

    public boolean isCollapsed() { return collapsed; }

    public void setCollapsed(boolean collapsed) { this.collapsed = collapsed; }
}
