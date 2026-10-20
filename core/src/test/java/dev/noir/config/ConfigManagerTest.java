package dev.noir.config;

import dev.noir.NoirCore;
import dev.noir.module.Category;
import dev.noir.module.Module;
import dev.noir.module.settings.EnumSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.testutil.FakePlatform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {
    enum Prio { HEALTH, DISTANCE }

    static final class Demo extends Module {
        final NumberSetting range = number("Range", 4.2, 1, 6, 0.1, "Reach.");
        final EnumSetting<Prio> prio = mode("Priority", Prio.DISTANCE, "Target order.");

        Demo() {
            super("Demo Aura", Category.COMBAT, "demo");
        }
    }

    @TempDir
    Path dir;

    private record Env(NoirCore core, FakePlatform platform, Demo demo) {}

    private Env boot() {
        FakePlatform p = new FakePlatform(dir);
        NoirCore core = new NoirCore(p);
        Demo d = new Demo();
        core.register(d);
        core.start();
        return new Env(core, p, d);
    }

    @Test
    void autosavesAfterTheDelayAndReloads() {
        Env a = boot();
        a.demo.range.set(5.5);
        a.demo.setEnabled(true);
        a.core.friends().add("Notch");
        a.core.theme().ink.set(0xFFEEEEEE);
        assertTrue(a.core.config().isDirty());
        a.core.config().tick();
        assertTrue(a.core.config().isDirty(), "must wait for the autosave delay");
        a.platform.clock.advance(2500);
        a.core.config().tick();
        assertFalse(a.core.config().isDirty());
        assertTrue(Files.exists(dir.resolve("profiles/default.json")));

        Env b = boot();
        assertEquals(5.5, b.demo.range.get());
        assertTrue(b.demo.isEnabled());
        assertTrue(b.core.friends().isFriend("notch"));
        assertEquals(0xFFEEEEEE, b.core.theme().ink.argb());
    }

    @Test
    void profilesSaveLoadDelete() {
        Env a = boot();
        a.demo.range.set(3.0);
        assertTrue(a.core.config().saveProfile("pvp"));
        a.demo.range.set(6.0);
        assertTrue(a.core.config().saveProfile("far"));
        assertTrue(a.core.config().loadProfile("pvp"));
        assertEquals(3.0, a.demo.range.get());
        assertEquals("pvp", a.core.config().activeProfile());
        assertTrue(a.core.config().profiles().containsAll(java.util.List.of("pvp", "far")));
        assertFalse(a.core.config().deleteProfile("pvp"), "can't delete the active profile");
        assertTrue(a.core.config().deleteProfile("far"));
        assertFalse(a.core.config().loadProfile("far"));
        assertFalse(a.core.config().saveProfile("../evil"));
    }

    @Test
    void resetModuleRestoresDefaults() {
        Env a = boot();
        a.demo.range.set(2.0);
        a.demo.prio.set(Prio.HEALTH);
        a.core.config().resetModule(a.demo);
        assertEquals(4.2, a.demo.range.get());
        assertEquals(Prio.DISTANCE, a.demo.prio.get());
    }

    @Test
    void corruptFilesDoNotCrash() throws Exception {
        Files.createDirectories(dir.resolve("profiles"));
        Files.writeString(dir.resolve("client.json"), "{not json");
        Files.writeString(dir.resolve("profiles/default.json"), "[]");
        Env a = boot();
        assertEquals(4.2, a.demo.range.get());
        assertTrue(Files.exists(dir.resolve("client.json.corrupt")));
    }

    @Test
    void guiLayoutPersists() {
        Env a = boot();
        com.google.gson.JsonObject layout = new com.google.gson.JsonObject();
        com.google.gson.JsonObject panels = new com.google.gson.JsonObject();
        com.google.gson.JsonObject combat = new com.google.gson.JsonObject();
        combat.addProperty("x", 321);
        combat.addProperty("y", 99);
        combat.addProperty("collapsed", true);
        panels.add("category:COMBAT", combat);
        layout.add("panels", panels);
        a.core.gui().layoutFromJson(layout);
        a.core.config().saveAll();
        Env b = boot();
        com.google.gson.JsonObject saved = b.core.gui().layoutToJson().getAsJsonObject("panels").getAsJsonObject("category:COMBAT");
        assertEquals(321, saved.get("x").getAsInt());
        assertTrue(saved.get("collapsed").getAsBoolean());
    }
}
