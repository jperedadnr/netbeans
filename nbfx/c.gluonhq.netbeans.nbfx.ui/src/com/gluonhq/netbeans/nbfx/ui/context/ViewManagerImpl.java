package com.gluonhq.netbeans.nbfx.ui.context;

import com.gluonhq.netbeans.nbfx.api.view.ViewManager;
import com.gluonhq.netbeans.nbfx.api.view.ViewProvider;
import com.gluonhq.netbeans.nbfx.ui.shell.Docking;
import com.gluonhq.netbeans.nbfx.ui.shell.NbfxTabPane;
import com.gluonhq.netbeans.nbfx.ui.shell.NbfxTabPane.PaneRole;
import com.gluonhq.netbeans.nbfx.ui.shell.ViewTabs;
import java.util.logging.Logger;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.openide.util.lookup.ServiceProvider;

/**
 * The {@link ViewManager}: shows a view's tab where it currently is - any pane, docked or detached -
 * or docks a new one at the view's default location.
 */
@ServiceProvider(service = ViewManager.class)
public class ViewManagerImpl implements ViewManager {

    private static final Logger LOG = Logger.getLogger(ViewManagerImpl.class.getName());

    @Override
    public void show(ViewProvider view) {
        Tab existing = NbfxTabPane.findViewTab(view.getId()).orElse(null);
        if (existing != null) {
            NbfxTabPane.selectTabAndMoveToFront(existing);
            NbfxTabPane.focusDocument(existing);
            return;
        }
        TabPane target = Docking.paneAt(view.getDefaultLocation());
        if (target == null) {
            // The dock area is not built (headless), or lacks the location's permanent pane: fall
            // back to the navigator pane.
            target = NbfxTabPane.paneWithRole(PaneRole.NAVIGATOR);
        }
        if (target == null) {
            LOG.warning(() -> "No pane to show the view " + view.getId() + " in");
            return;
        }
        Tab tab = ViewTabs.create(view, view.getDescription());
        NbfxTabPane.attachTab(target, tab);
        NbfxTabPane.focusDocument(tab);
    }

    @Override
    public void hide(ViewProvider view) {
        NbfxTabPane.findViewTab(view.getId()).ifPresent(NbfxTabPane::closeTab);
    }

    @Override
    public boolean isShowing(ViewProvider view) {
        return NbfxTabPane.findViewTab(view.getId()).isPresent();
    }
}
