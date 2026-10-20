package dev.noir.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.noir.NoirCore;
import dev.noir.hud.HudElement;
import dev.noir.module.Module;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * JSON config under {@code .minecraft/config/noir/}:
 * <pre>
 *   client.json            active profile, client settings, theme
 *   gui.json               ClickGUI panel layout
 *   friends.json           friend names
 *   profiles/&lt;name&gt;.json  modules (enabled + settings) and HUD layout
 * </pre>
 * Changes mark the config dirty; {@link #tick()} writes it once nothing has changed
 * for the autosave delay. Files are written atomically (temp file + move).
 */
public final class ConfigManager {
    public static final String DEFAULT_PROFILE = "default";
    private static final Pattern PROFILE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final int VERSION = 1;

    private final NoirCore core;
    private final Path root;
    private final Path profilesDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private String activeProfile = DEFAULT_PROFILE;
    private boolean dirty;
    private long lastChangeMs;
    private boolean suppress;

    public ConfigManager(NoirCore core, Path root) {
        this.core = core;
        this.root = root;
        this.profilesDir = root.resolve("profiles");
    }

    public Path root() {
        return root;
    }

    /** Hooks change listeners. Call after all modules and HUD elements are registered. */
    public void attach() {
        Runnable mark = this::markDirty;
        core.client().onAnySettingChange(mark);
        core.theme().onAnySettingChange(mark);
        for (Module m : core.modules().all()) m.onAnySettingChange(mark);
        for (HudElement e : core.hud().elements()) e.onAnySettingChange(mark);
        core.friends().onChange(mark);
        core.events().subscribe(dev.noir.event.Events.ModuleToggle.class, e -> markDirty());
    }

    public void markDirty() {
        if (suppress) return;
        dirty = true;
        lastChangeMs = core.platform().clock().millis();
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Called every client tick. */
    public void tick() {
        if (!dirty) return;
        long delayMs = (long) (core.client().autosaveDelay.getDouble() * 1000);
        if (core.platform().clock().millis() - lastChangeMs >= delayMs) saveAll();
    }

    // ---- load ---------------------------------------------------------------------------

    public void load() {
        suppress = true;
        try {
            JsonObject client = readObject(root.resolve("client.json"));
            if (client != null) {
                if (client.has("activeProfile") && isValidProfileName(client.get("activeProfile").getAsString())) {
                    activeProfile = client.get("activeProfile").getAsString();
                }
                core.client().settingsFromJson(obj(client, "client"));
                core.theme().settingsFromJson(obj(client, "theme"));
            }
            JsonObject gui = readObject(root.resolve("gui.json"));
            if (gui != null) core.gui().layoutFromJson(gui);
            JsonElement friends = readElement(root.resolve("friends.json"));
            if (friends != null && friends.isJsonArray()) {
                List<String> names = new ArrayList<>();
                for (JsonElement e : friends.getAsJsonArray()) if (e.isJsonPrimitive()) names.add(e.getAsString());
                core.friends().replaceAll(names);
            }
            JsonObject profile = readObject(profileFile(activeProfile));
            if (profile != null) applyProfile(profile);
        } finally {
            suppress = false;
        }
        dirty = false;
    }

    private void applyProfile(JsonObject profile) {
        JsonObject modules = obj(profile, "modules");
        for (Module m : core.modules().all()) {
            JsonObject mo = modules == null ? null : obj(modules, m.name());
            if (mo == null) {
                m.setEnabled(false);
                m.resetSettings();
                continue;
            }
            m.resetSettings();
            m.settingsFromJson(obj(mo, "settings"));
            boolean enabled = mo.has("enabled") && mo.get("enabled").isJsonPrimitive() && mo.get("enabled").getAsBoolean();
            m.setEnabled(enabled);
        }
        JsonObject hud = obj(profile, "hud");
        for (HudElement e : core.hud().elements()) {
            e.resetSettings();
            if (hud != null) e.settingsFromJson(obj(hud, e.id()));
        }
    }

    // ---- save ---------------------------------------------------------------------------

    public void saveAll() {
        try {
            Files.createDirectories(profilesDir);
            JsonObject client = new JsonObject();
            client.addProperty("version", VERSION);
            client.addProperty("activeProfile", activeProfile);
            client.add("client", core.client().settingsToJson());
            client.add("theme", core.theme().settingsToJson());
            write(root.resolve("client.json"), client);
            write(root.resolve("gui.json"), core.gui().layoutToJson());
            JsonArray friends = new JsonArray();
            core.friends().list().forEach(friends::add);
            write(root.resolve("friends.json"), friends);
            write(profileFile(activeProfile), profileJson());
            dirty = false;
        } catch (IOException e) {
            core.platform().logError("Failed to save Noir config", e);
        }
    }

    private JsonObject profileJson() {
        JsonObject profile = new JsonObject();
        profile.addProperty("version", VERSION);
        JsonObject modules = new JsonObject();
        for (Module m : core.modules().all()) {
            JsonObject mo = new JsonObject();
            mo.addProperty("enabled", m.isEnabled());
            mo.add("settings", m.settingsToJson());
            modules.add(m.name(), mo);
        }
        profile.add("modules", modules);
        JsonObject hud = new JsonObject();
        for (HudElement e : core.hud().elements()) hud.add(e.id(), e.settingsToJson());
        profile.add("hud", hud);
        return profile;
    }

    // ---- profiles -------------------------------------------------------------------------

    public static boolean isValidProfileName(String name) {
        return name != null && PROFILE_NAME.matcher(name).matches();
    }

    public String activeProfile() {
        return activeProfile;
    }

    /** Saves the current state as {@code name} and makes it active. */
    public boolean saveProfile(String name) {
        if (!isValidProfileName(name)) return false;
        activeProfile = name;
        saveAll();
        return Files.exists(profileFile(name));
    }

    /** Saves the current profile, then switches to {@code name}. */
    public boolean loadProfile(String name) {
        if (!isValidProfileName(name)) return false;
        Path file = profileFile(name);
        JsonObject profile = readObject(file);
        if (profile == null) return false;
        saveAll();
        activeProfile = name;
        suppress = true;
        try {
            applyProfile(profile);
        } finally {
            suppress = false;
        }
        saveAll();
        return true;
    }

    public boolean deleteProfile(String name) {
        if (!isValidProfileName(name) || name.equals(activeProfile)) return false;
        try {
            return Files.deleteIfExists(profileFile(name));
        } catch (IOException e) {
            core.platform().logError("Failed to delete profile " + name, e);
            return false;
        }
    }

    public List<String> profiles() {
        List<String> out = new ArrayList<>();
        if (Files.isDirectory(profilesDir)) {
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(profilesDir, "*.json")) {
                for (Path p : ds) {
                    String n = p.getFileName().toString();
                    out.add(n.substring(0, n.length() - 5));
                }
            } catch (IOException e) {
                core.platform().logError("Failed to list profiles", e);
            }
        }
        if (!out.contains(activeProfile)) out.add(activeProfile);
        Collections.sort(out);
        return out;
    }

    /** Resets one module's settings to defaults (keeps it enabled/disabled as is). */
    public void resetModule(Module module) {
        module.resetSettings();
        markDirty();
    }

    // ---- io -------------------------------------------------------------------------------

    private Path profileFile(String name) {
        return profilesDir.resolve(name + ".json");
    }

    private void write(Path file, JsonElement json) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, gson.toJson(json), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private JsonElement readElement(Path file) {
        if (!Files.isRegularFile(file)) return null;
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            core.platform().logError("Ignoring unreadable config file " + file.getFileName(), e);
            backupCorrupt(file);
            return null;
        }
    }

    private JsonObject readObject(Path file) {
        JsonElement e = readElement(file);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private void backupCorrupt(Path file) {
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            // best effort
        }
    }

    private static JsonObject obj(JsonObject parent, String key) {
        JsonElement e = parent.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }
}
