package dev.noir.module.settings;

import java.util.Map;
import java.util.function.Predicate;

/** A list of entity type ids with a color each. */
public final class EntityListSetting extends IdListSetting<EntityListSetting> {
    /** Installed by the Minecraft layer to check ids against the entity type registry. */
    public static volatile Predicate<String> VALIDATOR = id -> id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public EntityListSetting(String name, Map<String, Integer> defaults, int defaultEntryColor, String description) {
        super(name, defaults, defaultEntryColor, description);
    }

    @Override
    protected Predicate<String> validator() {
        return VALIDATOR;
    }

    @Override
    public String typeName() {
        return "entities: " + super.typeName();
    }
}
