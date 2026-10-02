package com.gluonhq.netbeans.nbfx.api.view;

import javafx.scene.Node;

/**
 * A tool view shown in its own tab: a navigator tree, a search result, a usages window...
 * <p>
 * A view's tab behaves like every other tab: the user can drag it between panes, dock it below the
 * navigators or the editors, or detach it into its own window, and where it was left is persisted
 * with the session layout under {@link #getId()}. Views published in the global {@code Lookup} under
 * this interface are resolved by that id when a layout is restored; views that are also
 * {@link com.gluonhq.netbeans.nbfx.api.NavigatorProvider navigators} are docked at start-up, the
 * others only appear when something calls {@link ViewManager#show(ViewProvider)}.
 */
public interface ViewProvider {

    /**
     * The stable identifier of this view, used to persist and restore its tab. Defaults to the
     * implementation class name; a view with several instances must return a distinct id per instance.
     */
    default String getId() {
        return getClass().getName();
    }

    /** The title of the tab holding this view. */
    String getTitle();

    /** A short description of this view, used for tooltips. Defaults to {@link #getTitle()}. */
    default String getDescription() {
        return getTitle();
    }

    /** The content of the tab holding this view. */
    Node getView();

    /**
     * Called on the FX thread once the tab holding this view has been closed (not moved to another
     * pane or window), so the view can release or reset what it shows. Does nothing by default.
     */
    default void viewClosed() {
    }

    /** Where the tab is docked the first time the view is shown. Defaults to {@link DockLocation#LEFT}. */
    default DockLocation getDefaultLocation() {
        return DockLocation.LEFT;
    }
}
