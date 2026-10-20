package dev.noir.gui;

import dev.noir.NoirCore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Hosts the {@link ClickGui}. A client-only screen: while it is open the PacketManager
 * refuses inventory actions (the server doesn't know about it) and binds don't fire.
 */
public final class NoirScreen extends Screen {
    private final NoirCore core;

    public NoirScreen(NoirCore core) {
        super(Component.literal("Noir"));
        this.core = core;
        core.gui().open();
    }

    private float d(double guiCoord) {
        return McCanvas.toDesign(minecraft, core, guiCoord);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        McCanvas canvas = McCanvas.begin(graphics, core);
        try {
            core.gui().render(canvas);
        } finally {
            canvas.end();
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // The ClickGUI draws its own backdrop (Theme background at Backdrop Opacity).
    }

    @Override
    public void tick() {
        if (core.gui().isClosed()) minecraft.gui.setScreen(null);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        core.gui().mouseMoved(d(mouseX), d(mouseY));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        core.gui().mouseDown(d(event.x()), d(event.y()), event.buttonInfo().button());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        core.gui().mouseUp(d(event.x()), d(event.y()), event.buttonInfo().button());
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        core.gui().scroll((float) verticalAmount);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        core.gui().keyPressed(event.key(), event.modifiers());
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        core.gui().charTyped(event.codepoint());
        return true;
    }

    /** Esc runs the close animation instead of closing instantly. */
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
