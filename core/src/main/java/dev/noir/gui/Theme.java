package dev.noir.gui;

import dev.noir.module.settings.ColorSetting;
import dev.noir.module.settings.NumberSetting;
import dev.noir.module.settings.SettingsHolder;
import dev.noir.util.Colors;

/**
 * The Noir design system tokens. Monochrome: hierarchy comes from lightness only.
 * Every token is an editable setting; {@link #resetToNoir()} restores the spec.
 */
public final class Theme extends SettingsHolder {
    public final ColorSetting background;
    public final ColorSetting surface;
    public final ColorSetting surfaceRaised;
    public final ColorSetting borderSubtle;
    public final ColorSetting border;
    public final ColorSetting borderStrong;
    public final ColorSetting ringSoft;
    public final ColorSetting ink;
    public final ColorSetting inkHover;
    public final ColorSetting inkMuted;
    public final ColorSetting inkSubtle;
    public final ColorSetting onInk;

    public final NumberSetting radiusChip;
    public final NumberSetting radiusControl;
    public final NumberSetting radiusPanel;
    public final NumberSetting gapIcon;
    public final NumberSetting padControl;
    public final NumberSetting padPanel;
    public final NumberSetting gapPanel;

    public Theme() {
        super("Theme", "Noir design tokens.");
        group("Surfaces", "Backgrounds.");
        background = color("Background", 0xFF000000, "Screen backdrop and dim.");
        surface = color("Surface", 0xFF0C0C0C, "Category panels, setting rows and HUD cards.");
        surfaceRaised = color("Surface Raised", 0xFF171717, "Hovered rows, dropdowns, tooltips and popups.");

        group("Borders", "1px edges. Borders, not shadows.");
        borderSubtle = color("Border Subtle", Colors.rgba(255, 255, 255, 0.08), "Dividers between module rows.");
        border = color("Border", Colors.rgba(255, 255, 255, 0.16), "Default 1px edge on every panel and card.");
        borderStrong = color("Border Strong", Colors.rgba(255, 255, 255, 0.40), "Sliders, inputs and hover edges.");
        ringSoft = color("Ring Soft", Colors.rgba(255, 255, 255, 0.24), "1px halo, 2px offset, around white elements.");

        group("Ink", "Text and white fills.");
        ink = color("Ink", 0xFFFFFFFF, "Primary text; fill of enabled modules, toggles and slider fill.");
        inkHover = color("Ink Hover", 0xFFE5E5E5, "Hover fill for white elements.");
        inkMuted = color("Ink Muted", 0xFFA3A3A3, "Secondary text, setting labels and disabled modules.");
        inkSubtle = color("Ink Subtle", 0xFF7A7A7A, "Captions and placeholders. Not used on Surface Raised.");
        onInk = color("On Ink", 0xFF000000, "Text on white fills.");

        group("Shape", "Corner radii and spacing in design pixels.").collapsedByDefault();
        radiusChip = number("Chip Radius", 6, 0, 16, 1, "Corner radius of chips and checkboxes.");
        radiusControl = number("Control Radius", 10, 0, 20, 1, "Corner radius of buttons, inputs and sliders.");
        radiusPanel = number("Panel Radius", 16, 0, 32, 1, "Corner radius of panels.");
        gapIcon = number("Icon Gap", 4, 0, 16, 1, "Gap between an icon and its label.");
        padControl = number("Control Padding", 8, 0, 24, 1, "Padding inside controls.");
        padPanel = number("Panel Padding", 16, 0, 32, 1, "Padding inside panels.");
        gapPanel = number("Panel Gap", 24, 0, 64, 1, "Space between panels.");
    }

    public void resetToNoir() {
        resetSettings();
    }

    // Shorthands used all over the GUI code.
    public int bg() { return background.argb(); }
    public int surface() { return surface.argb(); }
    public int raised() { return surfaceRaised.argb(); }
    public int divider() { return borderSubtle.argb(); }
    public int edge() { return border.argb(); }
    public int edgeStrong() { return borderStrong.argb(); }
    public int ring() { return ringSoft.argb(); }
    public int ink() { return ink.argb(); }
    public int inkHover() { return inkHover.argb(); }
    public int muted() { return inkMuted.argb(); }
    public int subtle() { return inkSubtle.argb(); }
    public int onInk() { return onInk.argb(); }
    public float rChip() { return radiusChip.getFloat(); }
    public float rControl() { return radiusControl.getFloat(); }
    public float rPanel() { return radiusPanel.getFloat(); }
    public float gap() { return gapIcon.getFloat(); }
    public float padC() { return padControl.getFloat(); }
    public float padP() { return padPanel.getFloat(); }
    public float gapP() { return gapPanel.getFloat(); }
}
