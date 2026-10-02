package com.gluonhq.netbeans.nbfx.docking;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;

/**
 * What a {@link DockArea} needs from the application whose panes it arranges - the panes being nodes
 * of type {@code N}, typically tab panes, whose content the area itself never looks at: how to create
 * a pane to dock next to the existing ones, how to dispose of it once it empties, when a pane is
 * empty, and how to move the content of one pane into another.
 *
 * @param <N> the type of the panes
 */
public interface DockHost<N extends Node> {

    /** A new, empty pane to dock next to the existing ones. */
    N createDockedPane();

    /**
     * Called once a docked pane created by {@link #createDockedPane()} has been removed from the
     * area, for the application to forget it.
     */
    void disposeDockedPane(N pane);

    /**
     * Whether {@code pane} holds nothing, as an observable the area follows: an emptied docked pane is
     * removed, an emptied permanent pane hidden, and a permanent pane that receives content shown
     * again. For a tab pane, {@code Bindings.isEmpty(pane.getTabs())}; the area asks once per pane and
     * keeps the value it gets for as long as the pane is in the area, so a fresh binding is fine.
     */
    ObservableValue<Boolean> isEmpty(N pane);

    /** Moves everything {@code from} holds {@code into} the other pane, after its own content. */
    void moveContent(N from, N into);
}
