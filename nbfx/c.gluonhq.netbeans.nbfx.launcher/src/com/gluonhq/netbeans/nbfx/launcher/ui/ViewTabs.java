package com.gluonhq.netbeans.nbfx.launcher.ui;

import com.gluonhq.netbeans.nbfx.api.view.ViewProvider;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import org.openide.util.NbBundle;

/** Builds the tab of a {@link ViewProvider} and maps its dock locations to the launcher's panes. */
public final class ViewTabs {

    private ViewTabs() {
    }

    /**
     * Builds the tab of {@code view}: a draggable {@link Label} graphic, its content, its stable id,
     * and {@code tooltip} on the label (the close button carries its own).
     */
    public static Tab create(ViewProvider view, String tooltip) {
        Tab tab = new Tab();
        // A Label graphic (not tab text) is required so the tab can be dragged/detached.
        tab.setGraphic(new Label(view.getTitle()));
        tab.setContent(view.getView());
        NbfxTabPane.setViewId(tab, view.getId());
        NbfxTabPane.installTabLabelTooltip(tab, tooltip);
        NbfxTabPane.installCloseButtonTooltip(tab,
                NbBundle.getMessage(ViewTabs.class, "Tab.view.close.tooltip"));
        TabContextMenu.installForView(tab);
        return tab;
    }
}
