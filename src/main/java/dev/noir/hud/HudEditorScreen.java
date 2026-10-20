package dev.noir.hud;

import dev.noir.NoirCore;
import dev.noir.gui.McCanvas;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Hosts the {@link HudEditor}. */
public final class HudEditorScreen extends Screen {
    private final NoirCore core;
    private final HudEditor editor;
    private final HudData data;

    public HudEditorScreen(NoirCore core, HudData data) {
        super(Component.literal("Noir HUD Editor"));
        this.core = core;
        this.data = data;
        this.editor = new HudEditor(core);
        editor.open();
    }

    private float d(double guiCoord) {
        return McCanvas.toDesign(minecraft, core, guiCoord);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        McCanvas canvas = McCanvas.begin(graphics, core);
        try {
            editor.render(canvas, data);
        } finally {
            canvas.end();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // The editor dims the world itself so HUD elements stay readable.
    }

    @Override
    public void tick() {
        if (editor.closeRequested()) minecraft.gui.setScreen(null);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        editor.mouseMoved(d(mouseX), d(mouseY));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        editor.mouseDown(d(event.x()), d(event.y()), event.buttonInfo().button());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        editor.mouseUp(d(event.x()), d(event.y()), event.buttonInfo().button());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        editor.scroll((float) verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        editor.keyPressed(event.key(), event.modifiers());
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        editor.charTyped(event.codepoint());
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        core.config().markDirty();
    }
}
