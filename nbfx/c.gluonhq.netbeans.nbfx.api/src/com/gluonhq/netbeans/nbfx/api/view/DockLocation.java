package com.gluonhq.netbeans.nbfx.api.view;

/**
 * The docked places of the main window a {@link ViewProvider view} can be shown in by default. Each
 * is a tab pane; all but {@link #LEFT} and {@link #CENTER} only exist while they hold a tab, splitting
 * the pane (or the whole window) next to them. Users may then drag the view anywhere.
 */
public enum DockLocation {
    /** The left pane, where the navigators live. */
    LEFT,
    /** Below the left pane. */
    LEFT_BOTTOM,
    /** The center pane, where the editors live. */
    CENTER,
    /** Below the center pane. */
    CENTER_BOTTOM,
    /** Along the bottom of the whole window, below the navigators and the editors. */
    BOTTOM,
    /** Along the right of the whole window, beside the editors. */
    RIGHT
}
