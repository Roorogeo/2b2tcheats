package dev.noir;

import dev.noir.module.settings.BoolSetting;
import dev.noir.module.settings.EnumSetting;
import dev.noir.module.settings.KeybindSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.module.settings.SettingsHolder;
import dev.noir.module.settings.StringSetting;
import dev.noir.util.Keys;

/** Client-wide settings (shown in the GUI's Client panel, ".set client ..."). */
public final class ClientSettings extends SettingsHolder {
    public enum Brand {
        FABRIC("Fabric"),
        VANILLA("Vanilla");

        private final String label;

        Brand(String label) { this.label = label; }

        @Override public String toString() { return label; }
    }

    public final BoolSetting strictMode;
    public final EnumSetting<Brand> brand;
    public final KeybindSetting guiKey;
    public final KeybindSetting hudEditorKey;
    public final StringSetting commandPrefix;
    public final NumberSetting inventoryActionsPerTick;
    public final BoolSetting mouseStepRotations;
    public final NumberSetting rotationReturnSpeed;
    public final NumberSetting packetLogCapacity;
    public final NumberSetting animationSpeed;
    public final NumberSetting uiScale;
    public final BoolSetting followGuiScale;
    public final NumberSetting backdropOpacity;
    public final BoolSetting tooltips;
    public final NumberSetting tooltipDelay;
    public final BoolSetting useInter;
    public final NumberSetting autosaveDelay;
    public final BoolSetting chatFeedback;

    public ClientSettings() {
        super("Client", "Client-wide behaviour.");
        group("Network", "How the client behaves on the wire.");
        strictMode = bool("Strict Mode", true,
            "Blocks every module option that would need packets a vanilla client never sends. Blocked options are greyed out.");
        brand = mode("Brand", Brand.FABRIC,
            "Client brand reported to servers. Fabric is what any Fabric client reports; Vanilla reports \"vanilla\" and suppresses Fabric's channel registration.");
        inventoryActionsPerTick = number("Inventory Actions Per Tick", 1, 1, 4, 1,
            "Maximum container clicks per tick across all modules.")
            .strictMax(1, "A vanilla client clicks at most once per tick.");
        mouseStepRotations = bool("Mouse-Step Rotations", true,
            "Rounds rotation deltas to multiples of the mouse step for your sensitivity, like real mouse input.");
        rotationReturnSpeed = number("Rotation Return Speed", 40, 5, 180, 1,
            "Degrees per tick the server-side rotation moves back to your real view after a module stops rotating.").unit("°");

        group("Interface", "GUI and HUD behaviour.");
        guiKey = keybind("ClickGUI Key", Keys.RIGHT_SHIFT, "Opens the ClickGUI.");
        hudEditorKey = keybind("HUD Editor Key", Keys.NONE, "Opens the HUD editor.");
        commandPrefix = text("Command Prefix", ".", 3,
            "Chat messages starting with this are handled locally as commands and never sent.");
        animationSpeed = number("Animation Speed", 1.0, 0.0, 4.0, 0.05,
            "Multiplier for GUI/HUD animation speed. 0 turns animations off.").unit("x");
        uiScale = number("UI Scale", 1.0, 0.5, 3.0, 0.05,
            "Size of the GUI and HUD relative to the Noir design size.").unit("x");
        followGuiScale = bool("Follow GUI Scale", true,
            "Scales the UI with Minecraft's GUI Scale option (design size at GUI scale 2).");
        backdropOpacity = number("Backdrop Opacity", 0.6, 0.0, 1.0, 0.05,
            "How strongly the ClickGUI dims the game behind it.");
        tooltips = bool("Tooltips", true, "Show a tooltip when hovering a setting or module.");
        tooltipDelay = number("Tooltip Delay", 350, 0, 2000, 50,
            "Hover time before a tooltip appears.").unit("ms").showWhen(tooltips);
        useInter = bool("Inter Font", true, "Use the bundled Inter font. Off uses the vanilla font.");
        chatFeedback = bool("Chat Feedback", true, "Print a local chat line when a module is toggled by key.");

        group("Storage", "Config storage.");
        autosaveDelay = number("Autosave Delay", 2, 0.5, 30, 0.5,
            "Seconds after the last change before the config is written.").unit("s");
        packetLogCapacity = number("Packet Log Size", 2000, 100, 20000, 100,
            "Outgoing packets kept in the Packet Log panel.");
    }
}
