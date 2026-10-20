package dev.noir.command;

import dev.noir.NoirCore;
import dev.noir.module.Category;
import dev.noir.module.Module;
import dev.noir.module.settings.BlockListSetting;
import dev.noir.module.settings.EnumSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.testutil.FakePlatform;
import dev.noir.util.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandManagerTest {
    enum CritMode {
        JUMP("Jump"), PACKET("Packet");

        private final String l;

        CritMode(String l) { this.l = l; }

        @Override public String toString() { return l; }
    }

    static final class Crits extends Module {
        final EnumSetting<CritMode> mode = mode("Mode", CritMode.JUMP, "x").strictValues("Sends a fake position packet.", CritMode.PACKET);
        final NumberSetting delay = number("Attack Delay", 2, 0, 20, 1, "x");
        final BlockListSetting blocks = blocks("Blocks", 0xFFFFFFFF, "x", "chest");

        Crits() {
            super("Criticals", Category.COMBAT, "x");
        }
    }

    @TempDir
    Path dir;
    FakePlatform platform;
    NoirCore core;
    Crits crits;

    @BeforeEach
    void setUp() {
        platform = new FakePlatform(dir);
        core = new NoirCore(platform);
        crits = new Crits();
        core.register(crits);
        core.start();
    }

    @Test
    void nonCommandsPassThrough() {
        assertFalse(core.commands().handle("hello"));
        assertFalse(core.commands().handle("/home"));
    }

    @Test
    void everyPrefixedMessageIsConsumedEvenWhenInvalid() {
        assertTrue(core.commands().handle(".nonsense"));
        assertTrue(core.commands().handle("."));
        assertTrue(core.commands().handle(".set"));
        assertTrue(core.commands().handle(".toggle doesnotexist"));
    }

    @Test
    void toggle() {
        assertTrue(core.commands().handle(".toggle criticals"));
        assertTrue(crits.isEnabled());
        core.commands().handle(".t Criticals off");
        assertFalse(crits.isEnabled());
    }

    @Test
    void bind() {
        core.commands().handle(".bind criticals g hold");
        assertEquals(Keys.A + 6, crits.keybind.code());
        assertEquals("Hold", crits.bindMode.get().toString());
        core.commands().handle(".bind criticals none");
        assertFalse(crits.keybind.isBound());
    }

    @Test
    void setValuesAndStrictRefusal() {
        core.commands().handle(".set criticals attackdelay 7");
        assertEquals(7.0, crits.delay.get());
        core.commands().handle(".set criticals mode packet");
        assertEquals(CritMode.JUMP, crits.mode.rawValue(), "strict mode must refuse");
        assertTrue(platform.chat.stream().anyMatch(s -> s.contains("Strict mode")));
        core.commands().handle(".set client strictmode off");
        core.commands().handle(".set criticals mode packet");
        assertEquals(CritMode.PACKET, crits.mode.rawValue());
        core.commands().handle(".set criticals mode reset");
        assertEquals(CritMode.JUMP, crits.mode.rawValue());
        core.commands().handle(".set criticals blocks add barrel #FF00FF00");
        assertEquals(0xFF00FF00, crits.blocks.colorOf("barrel"));
        core.commands().handle(".set theme ink #FFEEEEEE");
        assertEquals(0xFFEEEEEE, core.theme().ink.argb());
        core.commands().handle(".set hud.watermark text VOID");
        assertEquals("VOID", core.hud().find("watermark").setting("text").display());
    }

    @Test
    void invalidValueKeepsTheOldOne() {
        core.commands().handle(".set criticals attackdelay 99");
        assertEquals(2.0, crits.delay.get());
        assertTrue(platform.chat.stream().anyMatch(s -> s.contains("not valid")));
    }

    @Test
    void friends() {
        core.commands().handle(".friend add Steve_1");
        assertTrue(core.friends().isFriend("steve_1"));
        core.commands().handle(".friend add bad-name!");
        assertEquals(1, core.friends().list().size());
        core.commands().handle(".friend remove STEVE_1");
        assertFalse(core.friends().isFriend("Steve_1"));
    }

    @Test
    void configCommands() {
        crits.delay.set(9.0);
        core.commands().handle(".config save pvp");
        crits.delay.set(1.0);
        core.commands().handle(".config load pvp");
        assertEquals(9.0, crits.delay.get());
        core.commands().handle(".config reset criticals");
        assertEquals(2.0, crits.delay.get());
    }

    @Test
    void customPrefix() {
        core.client().commandPrefix.set("-");
        assertFalse(core.commands().handle(".toggle criticals"));
        assertTrue(core.commands().handle("-toggle criticals"));
        assertTrue(crits.isEnabled());
    }

    @Test
    void suggestions() {
        assertTrue(core.commands().suggest("to").contains("toggle"));
        assertTrue(core.commands().suggest("set crit").contains("Criticals"));
        assertTrue(core.commands().suggest("set Criticals att").contains("attackdelay"));
    }
}
