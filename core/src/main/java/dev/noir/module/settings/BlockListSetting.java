package dev.noir.module.settings;

import java.util.Map;
import java.util.function.Predicate;

/** A list of block ids with a color each. */
public final class BlockListSetting extends IdListSetting<BlockListSetting> {
    /** Installed by the Minecraft layer to check ids against the block registry. */
    public static volatile Predicate<String> VALIDATOR = id -> id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public BlockListSetting(String name, Map<String, Integer> defaults, int defaultEntryColor, String description) {
        super(name, defaults, defaultEntryColor, description);
    }

    @Override
    protected Predicate<String> validator() {
        return VALIDATOR;
    }

    @Override
    public String typeName() {
        return "blocks: " + super.typeName();
    }
}
