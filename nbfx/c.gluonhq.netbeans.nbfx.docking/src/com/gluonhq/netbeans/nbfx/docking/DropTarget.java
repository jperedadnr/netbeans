package com.gluonhq.netbeans.nbfx.docking;

import javafx.geometry.Side;
import javafx.scene.Node;

/**
 * Where an item dragged over a pane of a {@link DockArea} would dock: in a new pane on one
 * {@code side} of the hovered {@code pane}, or - when {@code pane} is {@code null} - in a new pane
 * along one side of the whole area. See {@link DockArea#dropTargetAt}.
 *
 * @param <N> the type of the area's panes
 */
public record DropTarget<N extends Node>(N pane, Side side) {

    /** Whether the target is an edge of the whole area rather than of a pane. */
    public boolean isArea() {
        return pane == null;
    }
}
