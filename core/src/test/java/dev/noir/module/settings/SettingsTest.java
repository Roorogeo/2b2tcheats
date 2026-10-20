package dev.noir.module.settings;

import com.google.gson.JsonParser;
import dev.noir.network.StrictMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {
    enum Mode {
        JUMP("Jump"), PACKET("Packet"), NONE("None");

        private final String label;

        Mode(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    private boolean strict = true;

    {
        StrictMode.bind(() -> strict);
    }

    @AfterEach
    void restore() {
        StrictMode.bind(() -> true);
    }

    @Test
    void numberClampsAndSnaps() {
        NumberSetting n = new NumberSetting("Range", 4.2, 1, 6, 0.1, "Reach.");
        n.set(4.234);
        assertEquals(4.2, n.get());
        n.set(99.0);
        assertEquals(6.0, n.get());
        n.set(-3.0);
        assertEquals(1.0, n.get());
        assertEquals("1.0", n.display());
        assertNull(n.preview("7"));
        assertNull(n.preview("abc"));
        assertEquals(2.5, n.preview("2.5"));
    }

    @Test
    void numberRejectsBadDefault() {
        assertThrows(IllegalArgumentException.class, () -> new NumberSetting("X", 10, 0, 5, 1, "d"));
    }

    @Test
    void strictEnumFallsBackToAnAllowedMode() {
        EnumSetting<Mode> e = new EnumSetting<>("Mode", Mode.JUMP, "How.").strictValues("Needs a non-vanilla packet.", Mode.PACKET);
        e.set(Mode.PACKET);
        assertEquals(Mode.PACKET, e.rawValue());
        assertEquals(Mode.JUMP, e.get(), "strict on: effective value must fall back");
        assertTrue(e.isOptionBlocked(Mode.PACKET));
        strict = false;
        assertEquals(Mode.PACKET, e.get());
        assertFalse(e.isOptionBlocked(Mode.PACKET));
    }

    @Test
    void cycleSkipsBlockedModes() {
        EnumSetting<Mode> e = new EnumSetting<>("Mode", Mode.JUMP, "How.").strictValues("r", Mode.PACKET);
        e.cycle(1);
        assertEquals(Mode.NONE, e.rawValue());
        e.cycle(1);
        assertEquals(Mode.JUMP, e.rawValue());
    }

    @Test
    void strictBoolIsForcedOff() {
        BoolSetting b = new BoolSetting("Packet Spoof", false, "x").strict(v -> v, "Spoofs on-ground.");
        b.set(true);
        assertFalse(b.get());
        strict = false;
        assertTrue(b.get());
    }

    @Test
    void enumParsesNamesAndLabels() {
        EnumSetting<Mode> e = new EnumSetting<>("Mode", Mode.JUMP, "x");
        assertTrue(e.parse("packet"));
        assertEquals(Mode.PACKET, e.rawValue());
        assertTrue(e.parse("NONE"));
        assertFalse(e.parse("teleport"));
    }

    @Test
    void colorParsesFormats() {
        ColorSetting c = new ColorSetting("Color", 0xFFFFFFFF, "x");
        assertTrue(c.parse("#80FF0000"));
        assertEquals(0x80FF0000, c.get());
        assertTrue(c.parse("rgba(255,255,255,0.16)"));
        assertEquals(0x29FFFFFF, c.get());
        assertTrue(c.parse("#0f0"));
        assertEquals(0xFF00FF00, c.get());
        assertFalse(c.parse("blue"));
    }

    @Test
    void keybindParses() {
        KeybindSetting k = new KeybindSetting("Key", -1, "x");
        assertTrue(k.parse("rshift"));
        assertEquals(344, k.code());
        assertTrue(k.parse("mouse4"));
        assertEquals("mouse4", k.display());
        assertTrue(k.parse("none"));
        assertFalse(k.isBound());
    }

    @Test
    void blockListAddRemoveAndColors() {
        BlockListSetting b = new BlockListSetting("Blocks", Map.of(), 0xFFFFFFFF, "x");
        assertTrue(b.parse("add chest #FFAA00"));
        assertTrue(b.contains("minecraft:chest"));
        assertEquals(0xFFFFAA00, b.colorOf("chest"));
        assertTrue(b.parse("add minecraft:barrel"));
        assertEquals(List.of("minecraft:chest", "minecraft:barrel"), List.copyOf(b.ids()));
        assertTrue(b.parse("remove chest"));
        assertFalse(b.contains("chest"));
        assertFalse(b.parse("remove chest"));
        assertTrue(b.parse("obsidian, ender_chest"));
        assertEquals(2, b.size());
        assertFalse(b.parse("add Bad Id!"));
    }

    @Test
    void idListJsonRoundTrip() {
        BlockListSetting b = new BlockListSetting("Blocks", Map.of(), 0xFFFFFFFF, "x");
        b.put("chest", 0xFF112233);
        b.put("hopper", 0x80FFFFFF);
        BlockListSetting c = new BlockListSetting("Blocks", Map.of(), 0xFFFFFFFF, "x");
        c.fromJson(b.toJson());
        assertEquals(b.rawValue(), c.rawValue());
    }

    @Test
    void malformedJsonIsIgnored() {
        NumberSetting n = new NumberSetting("N", 2, 0, 5, 1, "x");
        n.fromJson(JsonParser.parseString("\"lots\""));
        assertEquals(2.0, n.get());
        BoolSetting b = new BoolSetting("B", true, "x");
        b.fromJson(JsonParser.parseString("[1,2]"));
        assertTrue(b.get());
    }

    @Test
    void visibilityFollowsParent() {
        BoolSetting parent = new BoolSetting("Through Walls", false, "x");
        NumberSetting child = new NumberSetting("Wall Range", 3, 0, 6, 0.5, "x").showWhen(parent);
        assertFalse(child.isVisible());
        parent.set(true);
        assertTrue(child.isVisible());
    }

    @Test
    void holderLookupIgnoresCaseAndSpaces() {
        SettingsHolder h = new SettingsHolder("H", "d") {
            final NumberSetting a = number("Attack Delay", 2, 0, 10, 1, "x");
        };
        assertTrue(h.setting("attackdelay") != null);
        assertTrue(h.setting("Attack_Delay") != null);
        assertTrue(h.setting("ATTACK DELAY") != null);
    }

    @Test
    void duplicateSettingNamesAreRejected() {
        assertThrows(IllegalStateException.class, () -> new SettingsHolder("H", "d") {
            final BoolSetting a = bool("Range", true, "x");
            final BoolSetting b = bool("range", true, "x");
        });
    }

    @Test
    void stringIsTrimmedToMaxLength() {
        StringSetting s = new StringSetting("Name", "x", 4, "d");
        s.set("abcdefg");
        assertEquals("abcd", s.get());
    }
}
