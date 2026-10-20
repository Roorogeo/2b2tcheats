package dev.noir.gui;

import dev.noir.module.settings.SettingsHolder;

import java.util.function.BiFunction;

/** A panel showing one settings holder (Client, Theme) plus optional action buttons. */
final class HolderPanel extends Panel {
    private final ClickGui gui;
    private final SettingsHolder holder;
    private final Footer footer;

    interface Footer extends BiFunction<Ui, float[], Float> {}

    HolderPanel(ClickGui gui, String id, String title, SettingsHolder holder, boolean collapsed, Footer footer) {
        super(id, title, 260, collapsed);
        this.gui = gui;
        this.holder = holder;
        this.footer = footer;
    }

    boolean visibleFor(String query) {
        return query == null || SettingsView.anyMatch(holder, query) || holder.name().toLowerCase().contains(query);
    }

    @Override
    protected float content(Ui ui, float x, float y, float w) {
        String q = gui.query();
        if (q != null && holder.name().toLowerCase().contains(q)) q = null;
        float h = SettingsView.render(ui, holder, x, y, w, q);
        if (footer != null) h += footer.apply(ui, new float[] {x, y + h, w});
        return h;
    }
}
