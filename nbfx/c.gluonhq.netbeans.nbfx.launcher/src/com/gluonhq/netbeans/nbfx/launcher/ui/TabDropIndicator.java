package com.gluonhq.netbeans.nbfx.launcher.ui;

import com.gluonhq.netbeans.nbfx.docking.DockArea;
import com.gluonhq.netbeans.nbfx.docking.DropTarget;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;

import java.util.Objects;

/**
 * Drop-target highlight + placeholder-tab management for {@link NbfxTabPane}.
 *
 * <p>While a drag is hovering over a TabPane, its decoration changes, with a
 * dashed line that shows the boundary that the dragged tab would take when dropped,
 * in combination with a temporary tab inserted at the computed drop index, that shifts the existing
 * tabs to the right.</p>
 */
public final class TabDropIndicator {

    static final String PLACEHOLDER_TAB_CLASS = "nbfx-placeholder-tab";

    private static final PseudoClass DROP_TARGET_PC = PseudoClass.getPseudoClass("drop-target");
    private static final PseudoClass HAS_PLACEHOLDER_PC = PseudoClass.getPseudoClass("has-placeholder");

    private static Tab placeholderTab;
    private static TabPane targetTabPane;
    /** The area currently drawing a drop line, if any. */
    private static DockArea<TabPane> splitArea;

    private TabDropIndicator() {}

    /** Activates the pseudo-class on {@code tabPane} and inserts/moves the placeholder. */
    static void show(TabPane tabPane, double localX, Tab dragged) {
        Objects.requireNonNull(tabPane).pseudoClassStateChanged(DROP_TARGET_PC, true);
        clearSplit();
        insertOrMovePlaceholder(tabPane, localX, dragged);
    }

    /**
     * Marks {@code target} - an edge of the hovered {@code tabPane}, or of the {@link DockArea} it
     * sits in - as about to be split: the drop docks the dragged tab in a new pane there. The area
     * draws the line; no placeholder is shown, since the tab does not join the hovered pane's header.
     */
    static void showSplit(TabPane tabPane, DropTarget<TabPane> target) {
        Objects.requireNonNull(tabPane).pseudoClassStateChanged(DROP_TARGET_PC, false);
        if (targetTabPane == tabPane) {
            removePlaceholder();
        }
        DockArea<TabPane> area = Docking.area();
        if (area == null || !area.contains(tabPane)) {
            return;
        }
        if (splitArea != area) {
            clearSplit();
        }
        splitArea = area;
        area.showDropLine(target);
    }

    private static void clearSplit() {
        if (splitArea != null) {
            splitArea.hideDropLine();
            splitArea = null;
        }
    }

    /**
     * Activates the pseudo-class on {@code tabPane} without any placeholder tab, for a drag that
     * does not land at a given tab position (files being dropped to be opened).
     */
    static void showHighlight(TabPane tabPane) {
        Objects.requireNonNull(tabPane).pseudoClassStateChanged(DROP_TARGET_PC, true);
    }

    /** Deactivates the pseudo-classes and removes the placeholder if present in the {@code tabPane}. */
    static void hide(TabPane tabPane) {
        Objects.requireNonNull(tabPane).pseudoClassStateChanged(DROP_TARGET_PC, false);
        clearSplit();
        if (targetTabPane == tabPane) {
            removePlaceholder();
        }
    }

    /** Clears the indicator on every TabPane and removes any active placeholder. */
    static void hideAll(Iterable<TabPane> tabPanes) {
        for (TabPane tp : Objects.requireNonNull(tabPanes)) {
            tp.pseudoClassStateChanged(DROP_TARGET_PC, false);
        }
        clearSplit();
        removePlaceholder();
    }

    /**
     * Returns the index at which a drop should land in {@code tabPane}. If the placeholder tab is
     * currently in {@code tabPane} its position is returned, and it is removed; otherwise the
     * cursor location is used.
     */
    static int getDropIndex(TabPane tabPane, double localX) {
        if (placeholderTab != null && targetTabPane == tabPane) {
            int index = tabPane.getTabs().indexOf(placeholderTab);
            removePlaceholder();
            return index;
        }
        return computeDropIndex(tabPane, localX);
    }

    private static int computeDropIndex(TabPane tabPane, double x) {
        ObservableList<Tab> tabs = tabPane.getTabs();
        int dropIndex = 0;
        for (Tab tab : tabs) {
            if (tab == placeholderTab) {
                continue;
            }
            if (tab.getGraphic() instanceof Label label) {
                Bounds b = tabPane.sceneToLocal(label.localToScene(label.getBoundsInLocal()));
                if (x < b.getCenterX()) {
                    return dropIndex;
                }
            }
            dropIndex++;
        }
        return dropIndex;
    }

    private static void insertOrMovePlaceholder(TabPane tabPane, double localX, Tab dragged) {
        int dropIndex = computeDropIndex(tabPane, localX);
        tabPane.pseudoClassStateChanged(HAS_PLACEHOLDER_PC, false);

        // Same-pane drop right next to the source tab: no placeholder needed.
        if (dragged.getTabPane() == tabPane) {
            int currentIndex = tabPane.getTabs().indexOf(dragged);
            if (currentIndex >= 0 && (dropIndex == currentIndex || dropIndex == currentIndex + 1)) {
                removePlaceholder();
                return;
            }
        }

        if (placeholderTab == null) {
            placeholderTab = createPlaceholderTab(dragged);
        } else if (targetTabPane != null && targetTabPane != tabPane) {
            targetTabPane.getTabs().remove(placeholderTab);
        }

        tabPane.pseudoClassStateChanged(HAS_PLACEHOLDER_PC, true);
        targetTabPane = tabPane;
        int currentPos = tabPane.getTabs().indexOf(placeholderTab);
        int targetPos = Math.clamp(dropIndex, 0, tabPane.getTabs().size() - (currentPos >= 0 ? 1 : 0));
        if (currentPos == targetPos) {
            return;
        }
        if (currentPos >= 0) {
            tabPane.getTabs().remove(currentPos);
            targetPos = Math.clamp(targetPos, 0, tabPane.getTabs().size());
        }
        tabPane.getTabs().add(targetPos, placeholderTab);
    }

    private static Tab createPlaceholderTab(Tab source) {
        Tab tab = new Tab();
        tab.setClosable(false);
        tab.getStyleClass().add(PLACEHOLDER_TAB_CLASS);
        if (source != null && source.getGraphic() instanceof Label label
                && label.getScene() != null && label.getScene().getWindow() != null) {
            tab.setGraphic(snapshotAsImageView(label));
        }
        return tab;
    }

    private static ImageView snapshotAsImageView(Label label) {
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        double scale = label.getScene().getWindow().getOutputScaleY();
        params.setTransform(Transform.scale(scale, scale));
        WritableImage img = label.snapshot(params, null);
        ImageView iv = new ImageView(img);
        iv.setFitWidth(img.getWidth() / scale);
        iv.setFitHeight(img.getHeight() / scale);
        return iv;
    }

    private static void removePlaceholder() {
        if (placeholderTab != null && targetTabPane != null) {
            targetTabPane.getTabs().remove(placeholderTab);
        }
        placeholderTab = null;
        targetTabPane = null;
    }
}

