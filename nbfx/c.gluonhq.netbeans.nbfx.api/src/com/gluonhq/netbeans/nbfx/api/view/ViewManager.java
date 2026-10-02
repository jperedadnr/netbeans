package com.gluonhq.netbeans.nbfx.api.view;

/**
 * Shows and hides {@link ViewProvider views}. The implementation lives in the launcher and is
 * registered in the global {@code Lookup}; it owns the tab of each view and knows which pane - or
 * detached window - that tab is currently in.
 */
public interface ViewManager {

    /**
     * Brings {@code view} on screen: selects its tab and fronts its window if it has one, otherwise
     * docks a new tab at {@link ViewProvider#getDefaultLocation()}. Must be called on the FX thread.
     */
    void show(ViewProvider view);

    /** Closes the tab of {@code view}, wherever it is; does nothing if it has none. */
    void hide(ViewProvider view);

    /** Whether {@code view} currently has a tab, docked or detached. */
    boolean isShowing(ViewProvider view);
}
