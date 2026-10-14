package com.gluonhq.netbeans.nbfx.ui.shell;

import com.gluonhq.netbeans.nbfx.api.view.DockLocation;
import com.gluonhq.netbeans.nbfx.api.view.FxArea;
import com.gluonhq.netbeans.nbfx.docking.DockArea;
import com.gluonhq.netbeans.nbfx.docking.DockHost;
import com.gluonhq.netbeans.nbfx.ui.shell.NbfxTabPane.PaneRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
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

    /**
     * Default share of the window the navigator pane takes, next to the editor pane - the
     * {@link FxArea#EXPLORER explorer} area's weight, which mirrors the Swing {@code explorer} mode
     * weight (0.3).
     *
     * @deprecated derive the share from {@link FxArea#EXPLORER} instead.
     */
    @Deprecated
    public static final double DEFAULT_NAVIGATOR_SHARE = FxArea.EXPLORER.weight();

    private static DockArea<TabPane> area;
    /** The area with the sliding sides around it: what the window shows. */
    private static SlidingArea slides;
    /**
     * The minimized pane whose tab is on loan to the sliding panel, if any. Such a pane is not
     * empty though its tab list may be, so the area must not dispose of it meanwhile.
     */
    private static final ObjectProperty<TabPane> lending = new SimpleObjectProperty<>();

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
        List<Double> dividers = panes.size() == 2 ? List.of(FxArea.EXPLORER.weight()) : List.of();
        area = new DockArea(new Docking(), panes, dividers);
        slides = new SlidingArea(area);
        return area;
    }

    /** The application's area, or {@code null} before it is created (or headless). */
    public static DockArea<TabPane> area() {
        return area;
    }

    /**
     * The application's area with its sliding sides - the node the main window shows, and what
     * minimizes panes to those sides - or {@code null} before the area is created.
     */
    public static SlidingArea slides() {
        return slides;
    }

    /** The permanent pane playing {@code role} in the application's area, or {@code null} if there is none. */
    public static NbfxTabPane primary(PaneRole role) {
        return area == null ? null : (NbfxTabPane) area.primary(role.name());
    }

    /**
     * The pane a view docked at {@code location} lives in: a permanent pane, the docked pane right
     * below it, or the one along the bottom or the right of the whole area - the docked panes being
     * created if there is none yet (a pane created here must receive a tab before the next pulse, or
     * it removes itself again). Returns {@code null} without an area, or when the location's permanent
     * pane is not in it. Map an area to its pane with {@link #paneOf(FxArea) paneOf}. While the
     * navigator pane is minimized there is nothing below it: a view bound for there joins the
     * navigator pane's minimized group instead.
     */
    public static NbfxTabPane paneAt(DockLocation location) {
        if (area == null) {
            return null;
        }
        TabPane pane = switch (location) {
            case LEFT -> primary(PaneRole.NAVIGATOR);
            case CENTER -> primary(PaneRole.MAIN);
            case LEFT_BOTTOM -> {
                NbfxTabPane navigator = primary(PaneRole.NAVIGATOR);
                yield navigator != null && area.isMinimized(navigator) ? navigator : area.paneBelow(navigator);
            }
            case CENTER_BOTTOM -> area.paneBelow(primary(PaneRole.MAIN));
            case BOTTOM -> area.edgePane(Side.BOTTOM);
            case RIGHT -> area.edgePane(Side.RIGHT);
        };
        return (NbfxTabPane) pane;
    }

    /** The pane an {@link FxArea area} lives in; see {@link #paneAt(DockLocation)}. */
    public static NbfxTabPane paneOf(FxArea area) {
        return paneAt(area.location());
    }

    @Override
    public TabPane createDockedPane() {
        return new NbfxTabPane(PaneRole.DOCKED);
    }

    @Override
    public void disposeDockedPane(TabPane pane) {
        NbfxTabPane.unregister(pane);
    }

    /** Notes that {@code pane}'s tab is on loan to the sliding panel ({@code null}: none is). */
    static void setLending(TabPane pane) {
        lending.set(pane);
    }

    /** A pane is empty when it holds no tab and none of its tabs is slid in from a sliding side. */
    @Override
    public ObservableValue<Boolean> isEmpty(TabPane pane) {
        return Bindings.createBooleanBinding(() -> pane.getTabs().isEmpty() && lending.get() != pane,
                pane.getTabs(), lending);
    }

    @Override
    public void moveContent(TabPane from, TabPane into) {
        for (Tab tab : List.copyOf(from.getTabs())) {
            NbfxTabPane.moveTab(into, tab, into.getTabs().size());
        }
    }
}
