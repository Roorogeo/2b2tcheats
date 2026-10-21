package dev.noir;

import dev.noir.basehunt.BaseHunt;
import dev.noir.command.CommandManager;
import dev.noir.config.ConfigManager;
import dev.noir.config.Friends;
import dev.noir.event.EventBus;
import dev.noir.event.Events;
import dev.noir.gui.ClickGui;
import dev.noir.gui.Theme;
import dev.noir.hud.HudManager;
import dev.noir.module.Module;
import dev.noir.module.ModuleManager;
import dev.noir.network.PacketManager;
import dev.noir.network.StrictMode;

/** Root object holding every Noir system. One instance per game session. */
public final class NoirCore {
    public static final String NAME = "Noir";
    public static final String VERSION = "0.1.0";

    private static volatile NoirCore instance;

    private final Platform platform;
    private final EventBus events;
    private final ClientSettings client;
    private final Theme theme;
    private final ModuleManager modules;
    private final Friends friends;
    private final PacketManager packets;
    private final HudManager hud;
    private final CommandManager commands;
    private final ConfigManager config;
    private final ClickGui gui;
    private final BaseHunt baseHunt;
    private boolean started;

    public NoirCore(Platform platform) {
        this(platform, null);
    }

    /** @param scanExecutor where chunk scans run; null for a background thread (tests pass Runnable::run) */
    public NoirCore(Platform platform, java.util.concurrent.Executor scanExecutor) {
        this.platform = platform;
        this.events = new EventBus(platform::logError);
        this.client = new ClientSettings();
        StrictMode.bind(client.strictMode::rawValue);
        this.theme = new Theme();
        this.modules = new ModuleManager(this);
        this.friends = new Friends();
        this.packets = new PacketManager(this);
        this.hud = new HudManager(this);
        this.commands = new CommandManager(this);
        this.config = new ConfigManager(this, platform.configDir());
        this.gui = new ClickGui(this);
        this.baseHunt = new BaseHunt(this, scanExecutor);
        instance = this;
    }

    public static NoirCore get() {
        return instance;
    }

    /** Registers modules; must happen before {@link #start()}. */
    public NoirCore register(Module... list) {
        if (started) throw new IllegalStateException("Register modules before start()");
        for (Module m : list) modules.register(m);
        return this;
    }

    /** Loads config and wires autosave. */
    public void start() {
        if (started) return;
        started = true;
        config.load();
        config.attach();
        events.subscribe(Events.Key.class, 100, e -> {
            if (e.action() == dev.noir.util.Keys.ACTION_PRESS && !e.inScreen()) {
                if (e.key() == client.guiKey.code()) platform.openClickGui();
                else if (e.key() == client.hudEditorKey.code() && client.hudEditorKey.isBound()) platform.openHudEditor();
            }
            modules.onKey(e.key(), e.action(), e.inScreen());
        });
        events.subscribe(Events.ModuleToggle.class, e -> hud.onModuleToggle(e.module(), e.enabled()));
        platform.log("Noir " + VERSION + " started with " + modules.all().size() + " modules.");
    }

    public void shutdown() {
        if (config.isDirty() || started) config.saveAll();
        baseHunt.shutdown();
    }

    public Platform platform() { return platform; }
    public EventBus events() { return events; }
    public ClientSettings client() { return client; }
    public Theme theme() { return theme; }
    public ModuleManager modules() { return modules; }
    public Friends friends() { return friends; }
    public PacketManager packets() { return packets; }
    public HudManager hud() { return hud; }
    public CommandManager commands() { return commands; }
    public ConfigManager config() { return config; }
    public ClickGui gui() { return gui; }
    public BaseHunt baseHunt() { return baseHunt; }
}
