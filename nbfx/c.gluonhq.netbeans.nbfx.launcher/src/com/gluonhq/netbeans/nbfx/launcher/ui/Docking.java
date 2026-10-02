package com.gluonhq.netbeans.nbfx.launcher.ui;

import com.gluonhq.netbeans.nbfx.api.view.DockLocation;
import com.gluonhq.netbeans.nbfx.docking.DockArea;
import com.gluonhq.netbeans.nbfx.docking.DockHost;
import com.gluonhq.netbeans.nbfx.launcher.ui.NbfxTabPane.PaneRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import javafx.beans.binding.Bindings;
import javafx.geometry.Side;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

/**
 * The main window's {@link DockArea} and how it is bound to the application: its permanent panes are
 * the {@link PaneRole#NAVIGATOR} and {@link PaneRole#MAIN} {@link NbfxTabPane}s, keyed by their role
 * names in the persisted {@code DockTree}s, and the panes it docks next to them are
 * {@link PaneRole#DOCKED} ones. There is one area per application.
 */
public final class Docking implements DockHost<TabPane> {

    /** Default share of the window the navigator pane takes, next to the editor pane. */
    public static final double DEFAULT_NAVIGATOR_SHARE = 0.22;

    private static DockArea<TabPane> area;

    private Docking() {}

    /**
     * Creates the application's area from the given permanent panes (the navigator pane, the editor
     * pane, or both, in that order), replacing any previous one.
     */
    public static DockArea<TabPane> create(NbfxTabPane... primaries) {
        SequencedMap<String, TabPane> panes = new LinkedHashMap<>();
        for (NbfxTabPane primary : primaries) {
            PaneRole role = NbfxTabPane.roleOf(primary);
            if (!role.isPermanent()) {
                throw new IllegalArgumentException("Not a permanent pane: " + role);
            }
            panes.put(role.name(), primary);
        }
        List<Double> dividers = panes.size() == 2 ? List.of(DEFAULT_NAVIGATOR_SHARE) : List.of();
        area = new DockArea(new Docking(), panes, dividers);
        return area;
    }

    /** The application's area, or {@code null} before it is created (or headless). */
    public static DockArea<TabPane> area() {
        return area;
    }

    /** The permanent pane playing {@code role} in the application's area, or {@code null} if there is none. */
    public static NbfxTabPane primary(PaneRole role) {
        return area == null ? null : (NbfxTabPane) area.primary(role.name());
    }

    /**
     * The pane a view docked at {@code location} lives in: a permanent pane, the docked pane right
     * below it, or the one along the bottom or the right of the whole area - the docked panes being created if
     * there is none yet (a pane created here must receive a tab before the next pulse, or it removes
     * itself again). Returns {@code null} without an area, or when the location's permanent pane is
     * not in it.
     */
    public static NbfxTabPane paneAt(DockLocation location) {
        if (area == null) {
            return null;
        }
        TabPane pane = switch (location) {
            case LEFT -> primary(PaneRole.NAVIGATOR);
            case CENTER -> primary(PaneRole.MAIN);
            case LEFT_BOTTOM -> area.paneBelow(primary(PaneRole.NAVIGATOR));
            case CENTER_BOTTOM -> area.paneBelow(primary(PaneRole.MAIN));
            case BOTTOM -> area.edgePane(Side.BOTTOM);
            case RIGHT -> area.edgePane(Side.RIGHT);
        };
        return (NbfxTabPane) pane;
    }

    @Override
    public TabPane createDockedPane() {
        return new NbfxTabPane(PaneRole.DOCKED);
    }

    @Override
    public void disposeDockedPane(TabPane pane) {
        NbfxTabPane.unregister(pane);
    }

    @Override
    public ObservableValue<Boolean> isEmpty(TabPane pane) {
        return Bindings.isEmpty(pane.getTabs());
    }

    @Override
    public void moveContent(TabPane from, TabPane into) {
        for (Tab tab : List.copyOf(from.getTabs())) {
            NbfxTabPane.moveTab(into, tab, into.getTabs().size());
        }
    }
}
