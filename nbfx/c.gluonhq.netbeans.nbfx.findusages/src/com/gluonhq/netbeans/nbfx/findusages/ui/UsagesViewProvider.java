package com.gluonhq.netbeans.nbfx.findusages.ui;

import com.gluonhq.netbeans.nbfx.annotations.FxViewLocation;
import com.gluonhq.netbeans.nbfx.annotations.FxViewRegistration;
import com.gluonhq.netbeans.nbfx.api.view.DockLocation;
import com.gluonhq.netbeans.nbfx.api.view.ViewManager;
import com.gluonhq.netbeans.nbfx.api.view.ViewProvider;
import com.gluonhq.netbeans.nbfx.findusages.model.UsagesModel;
import com.gluonhq.netbeans.nbfx.findusages.query.UsagesQuery;
import javafx.scene.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The "Usages" view: a dockable tab, opening along the bottom of the main area by default, that
 * holds the results of every Find Usages query as inner tabs.
 */
@ServiceProvider(service = ViewProvider.class)
@FxViewRegistration(id = UsagesViewProvider.ID, displayName = "Usages",
        location = FxViewLocation.CENTER_BOTTOM, position = 20)
public final class UsagesViewProvider implements ViewProvider {

    /** The stable id of the view, used by the layout persistence and {@link ViewManager}. */
    public static final String ID = "usages";

    private UsagesView view;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(UsagesViewProvider.class, "TITLE_Usages");
    }

    @Override
    public String getDescription() {
        return NbBundle.getMessage(UsagesViewProvider.class, "DESC_Usages");
    }

    @Override
    public DockLocation getDefaultLocation() {
        return DockLocation.CENTER_BOTTOM;
    }

    @Override
    public synchronized Node getView() {
        if (view == null) {
            UsagesModel model = Lookup.getDefault().lookup(UsagesModel.class);
            view = new UsagesView(model == null ? new UsagesModel() : model);
        }
        return view;
    }

    /** Closing the Usages tab drops every query: with one query there is no inner tab header to close it from. */
    @Override
    public synchronized void viewClosed() {
        if (view != null) {
            view.closeAll();
        }
    }

    /** Brings the view on screen and selects the tab of {@code query}. Must run on the FX thread. */
    public void show(UsagesQuery query) {
        ViewManager manager = Lookup.getDefault().lookup(ViewManager.class);
        if (manager != null) {
            manager.show(this);
        }
        if (query != null) {
            ((UsagesView) getView()).select(query);
        }
    }

    /** The registered instance, or {@code null} when the module is not loaded. */
    public static UsagesViewProvider instance() {
        return Lookup.getDefault().lookupAll(ViewProvider.class).stream()
                .filter(UsagesViewProvider.class::isInstance)
                .map(UsagesViewProvider.class::cast)
                .findFirst()
                .orElse(null);
    }
}
