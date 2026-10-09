package com.gluonhq.netbeans.nbfx.findinprojects.ui;

import com.gluonhq.netbeans.nbfx.annotations.FxViewLocation;
import com.gluonhq.netbeans.nbfx.annotations.FxViewRegistration;
import com.gluonhq.netbeans.nbfx.api.view.DockLocation;
import com.gluonhq.netbeans.nbfx.api.view.ViewManager;
import com.gluonhq.netbeans.nbfx.api.view.ViewProvider;
import com.gluonhq.netbeans.nbfx.findinprojects.model.FindModel;
import com.gluonhq.netbeans.nbfx.findinprojects.query.FindQuery;
import javafx.scene.Node;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The "Search" view - NetBeans' Search Results window: a dockable tab, opening along the bottom of
 * the main area by default, that holds the results of every Find in Projects search as inner tabs.
 */
@ServiceProvider(service = ViewProvider.class)
@FxViewRegistration(id = SearchViewProvider.ID, displayName = "Search Results",
        location = FxViewLocation.CENTER_BOTTOM, position = 10)
public final class SearchViewProvider implements ViewProvider {

    /** The stable id of the view, used by the layout persistence and {@link ViewManager}. */
    public static final String ID = "search";

    private SearchView view;

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(SearchViewProvider.class, "TITLE_Search");
    }

    @Override
    public String getDescription() {
        return NbBundle.getMessage(SearchViewProvider.class, "DESC_Search");
    }

    @Override
    public DockLocation getDefaultLocation() {
        return DockLocation.CENTER_BOTTOM;
    }

    @Override
    public synchronized Node getView() {
        if (view == null) {
            FindModel model = Lookup.getDefault().lookup(FindModel.class);
            view = new SearchView(model == null ? new FindModel() : model);
        }
        return view;
    }

    /** Closing the Search tab drops every query: with one query there is no inner tab header to close it from. */
    @Override
    public synchronized void viewClosed() {
        if (view != null) {
            view.closeAll();
        }
    }

    /** Brings the view on screen and selects the tab of {@code query} (none for {@code null}). Must run on the FX thread. */
    public void show(FindQuery query) {
        ViewManager manager = Lookup.getDefault().lookup(ViewManager.class);
        if (manager != null) {
            manager.show(this);
        }
        if (query != null) {
            ((SearchView) getView()).select(query);
        }
    }

    /** The registered instance, or {@code null} when the module is not loaded. */
    public static SearchViewProvider instance() {
        return Lookup.getDefault().lookupAll(ViewProvider.class).stream()
                .filter(SearchViewProvider.class::isInstance)
                .map(SearchViewProvider.class::cast)
                .findFirst()
                .orElse(null);
    }
}
