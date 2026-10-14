/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.gluonhq.netbeans.nbfx.ui.shell;

import com.gluonhq.netbeans.nbfx.docking.DockArea;
import com.gluonhq.netbeans.nbfx.docking.DockArea.Anchor;
import com.gluonhq.netbeans.nbfx.ui.shell.NbfxTabPane.PaneRole;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import javafx.util.Subscription;
import org.openide.util.NbBundle;

/**
 * The main window's {@link DockArea} with the NetBeans <em>sliding sides</em> around it: a pane
 * minimized out of the area (a <em>window group</em> in NetBeans terms) shows as a group of buttons,
 * one per tab, in a bar along the left, right, bottom or top edge of the window - the side the pane
 * was nearest to, judged against the editor pane as {@code DefaultView.guessSlideSide} does (plus
 * the top, which NetBeans has no sliding side for). Hovering a
 * button slides the tab in over the area, in a panel that slides out again when the mouse leaves;
 * clicking the button slides it in to stay, with the focus, until the focus leaves it, the mouse is
 * pressed elsewhere, or Escape is pressed. The panel is a tab pane of its own (the
 * {@link PaneRole#SLIDE} pane) that borrows the tab itself while it shows, so the tab keeps its header
 * - with its close button, and draggable to any other pane, a split zone or a new window, exactly as
 * in NetBeans - and gets a <em>Dock</em> button, which puts that one tab back in the area; the
 * <em>Restore Window Group</em> button heading each group puts the whole pane back where it was. The
 * panel's inner edge drags to resize it.
 * <p>
 * What is minimized is the {@link DockArea}'s business ({@link DockArea#minimize},
 * {@link DockArea#restore}, {@link DockArea#minimizedPanes()}); this is the view of it. The side and
 * the slide-in size of each minimized pane are kept here and persisted with the layout. A tab on loan
 * to the panel still belongs to its group: {@link #tabsOf} lists a minimized pane's tabs with it, and
 * the pane does not count as empty without it (see {@link Docking#isEmpty}).
 */
public final class SlidingArea extends StackPane {

    private static final Logger LOG = Logger.getLogger(SlidingArea.class.getName());

    /** How long the mouse rests on a slide button before its tab slides in. */
    private static final Duration SLIDE_IN_DELAY = Duration.millis(200);
    /** How long the mouse is away from the button and the panel before a hovered-in tab slides out. */
    private static final Duration SLIDE_OUT_DELAY = Duration.millis(400);
    private static final Duration SLIDE_ANIMATION = Duration.millis(120);
    /** The least size (width or height) the slid-in panel may be resized to, and what it leaves the area. */
    private static final double MIN_SLIDE_SIZE = 120;
    /** The share of the area a panel takes when the pane's size at minimizing is unknown. */
    private static final double DEFAULT_SLIDE_SHARE = 0.3;
    private static final double RESIZER_THICKNESS = 5;

    private static final PseudoClass DOCK_FOCUSED = PseudoClass.getPseudoClass("dock-focused");

    private final DockArea<TabPane> area;
    private final BorderPane frame = new BorderPane();
    private final Map<Side, SlideBar> bars = new EnumMap<>(Side.class);
    /** The side each minimized pane slides from. */
    private final Map<TabPane, Side> sides = new HashMap<>();
    /** The slide-in size (width for the left and right sides, height for the bottom) of each pane. */
    private final Map<TabPane, Double> sizes = new HashMap<>();
    private final SlidePanel panel = new SlidePanel();
    private final PauseTransition slideInDelay = new PauseTransition(SLIDE_IN_DELAY);
    private final PauseTransition slideOutDelay = new PauseTransition(SLIDE_OUT_DELAY);
    /** The tab whose button the mouse rests on, sliding in once the delay elapses. */
    private Tab pending;
    /** The pane that held the focus before the panel took it, to give it back on slide-out. */
    private TabPane previousFocus;
    private Subscription sceneSubscription = Subscription.EMPTY;

    public SlidingArea(DockArea<TabPane> area) {
        this.area = area;
        getStyleClass().add("nbfx-sliding-area");
        for (Side side : Side.values()) {
            bars.put(side, new SlideBar(side, this));
        }
        frame.setCenter(area);
        frame.setLeft(bars.get(Side.LEFT));
        frame.setRight(bars.get(Side.RIGHT));
        frame.setBottom(bars.get(Side.BOTTOM));
        frame.setTop(bars.get(Side.TOP));
        panel.setManaged(false);
        panel.setVisible(false);
        getChildren().addAll(frame, panel);
        area.minimizedPanes().addListener((ListChangeListener<TabPane>) change -> {
            while (change.next()) {
                change.getRemoved().forEach(this::paneRestored);
                change.getAddedSubList().forEach(this::paneMinimized);
            }
        });
        slideInDelay.setOnFinished(_ -> {
            Tab tab = pending;
            pending = null;
            if (tab != null) {
                slideIn(tab, false);
            }
        });
        slideOutDelay.setOnFinished(_ -> {
            if (panel.tab != null && !panel.sticky && !panel.isHover() && !NbfxTabPane.isTabDragInProgress()
                    && !bars.get(panel.side).isHovering(panel.tab)) {
                slideOut();
            }
        });
        panel.hoverProperty().subscribe(hovered -> {
            if (hovered) {
                slideOutDelay.stop();
            } else if (panel.tab != null && !panel.sticky) {
                slideOutDelay.playFromStart();
            }
        });
        panel.focusWithinProperty().subscribe(focused ->
                panel.pane.pseudoClassStateChanged(DOCK_FOCUSED, focused));
        sceneProperty().subscribe(this::watchScene);
    }

    // --- Minimizing ----------------------------------------------------------------------------

    /**
     * Minimizes {@code pane} - one of the area's panes but the editor pane - to the side of the
     * window it is nearest to: its tabs show as buttons in that side's bar until the pane is
     * {@linkplain #restore restored}.
     */
    public void minimize(TabPane pane) {
        if (!isMinimizable(pane)) {
            return;
        }
        Side side = guessSide(pane);
        sides.put(pane, side);
        sizes.put(pane, isHorizontal(side) ? pane.getHeight() : pane.getWidth());
        boolean hadFocus = pane.isFocusWithin();
        area.minimize(pane);
        LOG.fine(() -> "Minimized the " + NbfxTabPane.roleOf(pane) + " pane to the " + side);
        if (hadFocus) {
            NbfxTabPane.focusSelectedTab(area.mainPane());
        }
    }

    /**
     * Minimizes {@code tab} alone: it leaves its pane for a minimized pane of its own, anchored back
     * into the pane it left, so docking or restoring it rejoins that pane. The last tab of a pane
     * minimizes the pane itself.
     */
    public void minimize(Tab tab) {
        TabPane source = tab == null ? null : tab.getTabPane();
        if (!isMinimizable(source)) {
            return;
        }
        if (source.getTabs().size() == 1) {
            minimize(source);
            return;
        }
        Side side = guessSide(source);
        TabPane pane = new NbfxTabPane(PaneRole.DOCKED);
        sides.put(pane, side);
        sizes.put(pane, isHorizontal(side) ? source.getHeight() : source.getWidth());
        boolean hadFocus = tab.getContent() != null && tab.getContent().isFocusWithin();
        NbfxTabPane.moveTab(pane, tab, 0);
        area.minimize(pane, Anchor.into(source));
        if (hadFocus) {
            NbfxTabPane.focusSelectedTab(source);
        }
    }

    /**
     * Minimizes {@code pane} as a persisted layout recorded it: to {@code side}, sliding in at
     * {@code size}, going back at {@code anchor} (or, with {@code null}, along the side). The pane
     * may be a permanent pane that is out of the area, or a new docked pane that was never in it.
     */
    public void minimize(TabPane pane, Side side, double size, Anchor<TabPane> anchor) {
        if (pane == null || pane == area.mainPane()) {
            return;
        }
        sides.put(pane, side == null ? Side.LEFT : side);
        if (size > 0) {
            sizes.put(pane, size);
        }
        area.minimize(pane, anchor);
    }

    /** Puts the minimized {@code pane} back in the area, with the focus on its selected tab. */
    public void restore(TabPane pane) {
        if (pane == null || !area.isMinimized(pane)) {
            return;
        }
        area.restore(pane);
        if (area.contains(pane)) {
            NbfxTabPane.focusSelectedTab(pane);
        }
    }

    /**
     * Puts {@code tab}, a tab of a minimized pane (slid in or not), back in the area on its own, the
     * way NetBeans docks a window back to the mode its group came from, so that the tabs of one
     * group docked one by one end up together again. The last tab of a group restores the group.
     * The first tab docked out of a group settles where the group goes back:
     * <ul>
     *   <li>a group minimized out of a pane (an anchor <em>into</em> it) rejoins that pane;</li>
     *   <li>a minimized permanent pane (Projects/Files) goes back to its place with that tab, and
     *       the rest of the group stays minimized in a pane of its own anchored into it;</li>
     *   <li>any other group gets a new pane at its anchor, which becomes the group's anchor.</li>
     * </ul>
     * Later tabs, and Restore Window Group, then join that pane.
     */
    public void dock(Tab tab) {
        TabPane pane = groupOf(tab);
        if (pane == null) {
            return;
        }
        if (tabsOf(pane).size() == 1) {
            restore(pane);
            return;
        }
        Anchor<TabPane> anchor = area.anchorOf(pane);
        if (anchor != null && anchor.joins()) {
            TabPane home = anchor.leaf();
            if (area.contains(home) || (area.isPrimary(home) && !area.isMinimized(home))) {
                // Into the pane the group came from (a hidden empty permanent pane comes back by itself).
                // A slid-in tab leaves the panel here, which notes the loss and slides out.
                NbfxTabPane.moveTab(home, tab, home.getTabs().size());
                NbfxTabPane.selectTabAndMoveToFront(tab);
                return;
            }
            if (area.isMinimized(home)) {
                // The pane the group came from is minimized too: it goes back with this tab, the
                // rest of both groups staying minimized.
                slideOut();
                NbfxTabPane.moveTab(home, tab, home.getTabs().size());
                dockOutOf(home, tab);
                return;
            }
        }
        dockOutOf(pane, tab);
    }

    /**
     * Docks {@code tab} out of the minimized {@code group}: the group's pane goes back to its place
     * holding that tab, while its other tabs stay minimized in a docked pane anchored into it - the
     * one already anchored there, or a new one on the same side with the same slide-in size - so
     * they rejoin it when docked or restored.
     */
    private void dockOutOf(TabPane group, Tab tab) {
        // The slid-in tab goes back to the group first, so every tab is in the pane to be dealt out.
        slideOut();
        TabPane rest = restOf(group);
        for (Tab other : List.copyOf(group.getTabs())) {
            if (other != tab) {
                NbfxTabPane.moveTab(rest, other, rest.getTabs().size());
            }
        }
        if (!area.isMinimized(rest)) {
            area.minimize(rest, Anchor.into(group));
        }
        area.restore(group);
        NbfxTabPane.selectTabAndMoveToFront(tab);
    }

    /** The minimized pane anchored into {@code group}, or a new docked pane on the group's side. */
    private TabPane restOf(TabPane group) {
        for (TabPane candidate : area.minimizedPanes()) {
            Anchor<TabPane> anchor = area.anchorOf(candidate);
            if (candidate != group && anchor != null && anchor.joins() && anchor.leaf() == group) {
                return candidate;
            }
        }
        TabPane rest = new NbfxTabPane(PaneRole.DOCKED);
        sides.put(rest, sideOf(group));
        sizes.put(rest, sizeOf(group));
        return rest;
    }

    /** Whether {@code pane} is minimized out of the area. */
    public boolean isMinimized(TabPane pane) {
        return pane != null && area.isMinimized(pane);
    }

    /** Whether {@code pane} can be minimized: a pane of the area other than the editor pane. */
    public boolean isMinimizable(TabPane pane) {
        return pane != null && area.contains(pane) && pane != area.mainPane();
    }

    /** The side the minimized {@code pane} slides from (the left for a pane that is not minimized). */
    public Side sideOf(TabPane pane) {
        return sides.getOrDefault(pane, Side.LEFT);
    }

    /** The slide-in size of {@code pane}: its width for the left and right sides, its height for the top and bottom. */
    public double sizeOf(TabPane pane) {
        Double size = sizes.get(pane);
        if (size != null && size > 0) {
            return size;
        }
        return (isHorizontal(sideOf(pane)) ? getHeight() : getWidth()) * DEFAULT_SLIDE_SHARE;
    }

    /** Whether the bar on {@code side} runs along the window (top, bottom) rather than down it. */
    static boolean isHorizontal(Side side) {
        return side == Side.TOP || side == Side.BOTTOM;
    }

    /**
     * The tabs of the minimized {@code pane}, in order, including the one on loan to the slid-in
     * panel at the place it holds in the group.
     */
    public List<Tab> tabsOf(TabPane pane) {
        List<Tab> tabs = new ArrayList<>(pane.getTabs());
        if (panel.from == pane && panel.tab != null && !tabs.contains(panel.tab)) {
            tabs.add(Math.clamp(panel.index, 0, tabs.size()), panel.tab);
        }
        return tabs;
    }

    /** The tab of the minimized {@code pane} that is slid in, else its selected tab. */
    public Tab selectedOf(TabPane pane) {
        return panel.from == pane && panel.tab != null ? panel.tab : pane.getSelectionModel().getSelectedItem();
    }

    /** The minimized pane {@code tab} belongs to - the one it is on loan from while slid in - or {@code null}. */
    public TabPane groupOf(Tab tab) {
        if (tab == null) {
            return null;
        }
        if (tab == panel.tab) {
            return panel.from;
        }
        TabPane pane = tab.getTabPane();
        return pane != null && area.isMinimized(pane) ? pane : null;
    }

    // --- Sliding ---------------------------------------------------------------------------------

    /** Slides {@code tab}, a tab of a minimized pane, in to stay, with the focus on its content. */
    public void slideIn(Tab tab) {
        slideIn(tab, true);
    }

    /** Slides the panel out, if a tab is slid in: the tab goes back to its group. */
    public void slideOut() {
        slideInDelay.stop();
        slideOutDelay.stop();
        pending = null;
        if (panel.tab == null) {
            return;
        }
        bars.get(panel.side).markSlid(null);
        panel.hide();
        requestLayout();
    }

    /** The tab slid in right now, or {@code null}. */
    public Tab slidTab() {
        return panel.tab;
    }

    /** Whether {@code tab} is the slid-in one. */
    public boolean isSlid(Tab tab) {
        return tab != null && panel.tab == tab;
    }

    /** Whether {@code node} is within the slid-in panel. */
    public boolean isWithinPanel(Node node) {
        return panel.isVisible() && isWithin(node, panel);
    }

    private void slideIn(Tab tab, boolean sticky) {
        TabPane pane = tab == null ? null : tab.getTabPane();
        if (pane == null || (!area.isMinimized(pane) && tab != panel.tab)) {
            return;
        }
        slideInDelay.stop();
        slideOutDelay.stop();
        pending = null;
        if (panel.tab == tab) {
            if (sticky && !panel.sticky) {
                rememberFocus();
            }
            panel.sticky |= sticky;
            if (sticky) {
                NbfxTabPane.focusSelectedTab(panel.pane);
            }
            return;
        }
        if (panel.tab != null) {
            bars.get(panel.side).markSlid(null);
            panel.hide();
        }
        if (sticky) {
            rememberFocus();
        }
        Side side = sideOf(pane);
        panel.show(tab, pane, side, sticky);
        bars.get(side).markSlid(tab);
        requestLayout();
        if (sticky) {
            NbfxTabPane.focusSelectedTab(panel.pane);
        }
        LOG.fine(() -> "Slid in " + NbfxTabPane.titleOf(tab) + (sticky ? " to stay" : ""));
    }

    /** The mouse entered ({@code hovered}) or left the slide button of {@code tab}. */
    void buttonHovered(Tab tab, boolean hovered) {
        if (hovered) {
            slideOutDelay.stop();
            if (panel.tab == tab) {
                return;
            }
            pending = tab;
            slideInDelay.playFromStart();
        } else {
            if (pending == tab) {
                slideInDelay.stop();
                pending = null;
            }
            if (panel.tab == tab && !panel.sticky) {
                slideOutDelay.playFromStart();
            }
        }
    }

    /** The slide button of {@code tab} was clicked: the tab slides in to stay, or out if it already was. */
    void buttonPressed(Tab tab) {
        if (panel.tab == tab && panel.sticky) {
            slideOut();
            returnFocus();
        } else {
            slideIn(tab, true);
        }
    }

    /** Notes the pane that holds the focus, before the panel takes it. */
    private void rememberFocus() {
        TabPane focused = NbfxTabPane.focusedPane();
        if (focused != null && focused != panel.pane) {
            previousFocus = focused;
        }
    }

    /** Gives the focus back to the pane that had it before the panel took it. */
    private void returnFocus() {
        TabPane target = previousFocus;
        previousFocus = null;
        if (target == null || !NbfxTabPane.tabPanes().contains(target)) {
            target = area.mainPane();
        }
        NbfxTabPane.focusSelectedTab(target);
    }

    // --- The minimized panes -----------------------------------------------------------------

    private void paneMinimized(TabPane pane) {
        bars.get(sideOf(pane)).add(pane);
    }

    private void paneRestored(TabPane pane) {
        if (panel.from == pane) {
            slideOut();
        }
        bars.values().forEach(bar -> bar.remove(pane));
        sides.remove(pane);
    }

    /**
     * The side of the window {@code pane} is nearest to, judged against the editor pane as NetBeans
     * judges a window against the editor area: wholly to its left, the left; wholly to its right,
     * the right; below it, the bottom; above it, the top (a side NetBeans does not slide to);
     * anything else (around it) the left.
     */
    private Side guessSide(TabPane pane) {
        Bounds p = pane.localToScene(pane.getLayoutBounds());
        TabPane main = area.mainPane();
        Bounds m = main.localToScene(main.getLayoutBounds());
        if (p == null || m == null || m.getWidth() <= 0 || m.getHeight() <= 0) {
            return Side.LEFT;
        }
        if (p.getMaxX() <= m.getMinX() + 1) {
            return Side.LEFT;
        }
        if (p.getMinX() >= m.getMaxX() - 1) {
            return Side.RIGHT;
        }
        if (p.getMinY() >= m.getMaxY() - 1) {
            return Side.BOTTOM;
        }
        if (p.getMaxY() <= m.getMinY() + 1) {
            return Side.TOP;
        }
        return Side.LEFT;
    }

    // --- Layout ----------------------------------------------------------------------------------

    @Override
    protected void layoutChildren() {
        super.layoutChildren();
        if (!panel.isVisible() || panel.tab == null || panel.from == null) {
            return;
        }
        double width = getWidth();
        double height = getHeight();
        double left = barSize(Side.LEFT);
        double right = barSize(Side.RIGHT);
        double top = barSize(Side.TOP);
        double bottom = barSize(Side.BOTTOM);
        TabPane pane = panel.from;
        switch (panel.side) {
            case RIGHT -> {
                double size = clampSize(pane, width - left - right);
                panel.resizeRelocate(width - right - size, top, size, height - top - bottom);
            }
            case BOTTOM -> {
                double size = clampSize(pane, height - top - bottom);
                panel.resizeRelocate(left, height - bottom - size, width - left - right, size);
            }
            case TOP -> {
                double size = clampSize(pane, height - top - bottom);
                panel.resizeRelocate(left, top, width - left - right, size);
            }
            default -> {
                double size = clampSize(pane, width - left - right);
                panel.resizeRelocate(left, top, size, height - top - bottom);
            }
        }
    }

    /** The slide-in size of {@code pane} kept within the room the area has for it. */
    private double clampSize(TabPane pane, double available) {
        double max = Math.max(MIN_SLIDE_SIZE, available - MIN_SLIDE_SIZE);
        return Math.clamp(sizeOf(pane), Math.min(MIN_SLIDE_SIZE, max), max);
    }

    /** The thickness of the bar on {@code side}: its preferred width or height, or 0 while it is hidden. */
    private double barSize(Side side) {
        SlideBar bar = bars.get(side);
        if (!bar.isManaged()) {
            return 0;
        }
        return isHorizontal(side) ? bar.prefHeight(-1) : bar.prefWidth(-1);
    }

    // --- Scene ---------------------------------------------------------------------------------

    /**
     * Follows the scene the area is in: the panel slides out when the mouse is pressed outside it
     * (and outside its button), when a slid-in-to-stay tab loses the focus to something outside it,
     * and on Escape from inside it.
     */
    private void watchScene(Scene scene) {
        sceneSubscription.unsubscribe();
        sceneSubscription = Subscription.EMPTY;
        if (scene == null) {
            return;
        }
        javafx.event.EventHandler<MouseEvent> pressed = event -> {
            if (panel.tab != null && event.getTarget() instanceof Node target
                    && !isWithin(target, panel) && !bars.get(panel.side).isButtonOf(panel.tab, target)) {
                slideOut();
            }
        };
        javafx.event.EventHandler<KeyEvent> escape = event -> {
            if (event.getCode() == KeyCode.ESCAPE && panel.tab != null && panel.isFocusWithin()) {
                slideOut();
                returnFocus();
                event.consume();
            }
        };
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, pressed);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, escape);
        Subscription focus = scene.focusOwnerProperty().subscribe(owner -> {
            if (panel.tab != null && panel.sticky && owner != null && !isWithin(owner, panel)) {
                slideOut();
            }
        });
        sceneSubscription = focus.and(() -> {
            scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, pressed);
            scene.removeEventFilter(KeyEvent.KEY_PRESSED, escape);
        });
    }

    private static boolean isWithin(Node node, Node ancestor) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n == ancestor) {
                return true;
            }
        }
        return false;
    }

    /**
     * A flat button showing the glyph of {@code icon} (a style class of {@code .nbfx-glyph}, drawn
     * by the stylesheet) with {@code tooltip}, as the window controls of the tab headers and the
     * sliding sides use.
     */
    static Button iconButton(String icon, String tooltip) {
        Region glyph = new Region();
        glyph.getStyleClass().addAll("nbfx-glyph", icon);
        Button button = new Button();
        button.setGraphic(glyph);
        button.getStyleClass().addAll("nbfx-icon-button", icon);
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    static String message(String key) {
        return NbBundle.getMessage(SlidingArea.class, key);
    }

    // --- The slid-in panel -----------------------------------------------------------------------

    /**
     * The panel a minimized tab slides in to: the {@link PaneRole#SLIDE} tab pane, which borrows the
     * tab while it shows here and gives it back to its group on slide-out - unless the tab left on
     * its own meanwhile (closed with its close button, dragged to another pane or out to a new
     * window, docked), in which case the panel just hides - and a resize handle along the edge
     * facing the area. The tab pane carries the Dock button in its header.
     */
    private final class SlidePanel extends BorderPane {

        final NbfxTabPane pane = new NbfxTabPane(PaneRole.SLIDE);
        private final Region resizer = new Region();
        /** The tab on loan, the minimized pane it came from and its place among that pane's tabs. */
        Tab tab;
        TabPane from;
        int index;
        Side side = Side.LEFT;
        /** Whether the tab was slid in to stay (clicked, or revealed), rather than hovered in. */
        boolean sticky;
        /** Whether the panel itself is moving the tab, as opposed to the user taking it away. */
        private boolean moving;
        private double dragStart;
        private double dragStartSize;

        SlidePanel() {
            getStyleClass().add("nbfx-slide-panel");
            setCenter(pane);
            pane.getTabs().addListener((ListChangeListener<Tab>) change -> {
                while (change.next()) {
                    if (change.wasRemoved() && !moving && tab != null && change.getRemoved().contains(tab)) {
                        tabLeft();
                    }
                }
            });
            resizer.getStyleClass().add("nbfx-slide-resizer");
            resizer.setOnMousePressed(event -> {
                dragStart = isHorizontal(side) ? event.getScreenY() : event.getScreenX();
                dragStartSize = from == null ? 0 : sizeOf(from);
                event.consume();
            });
            resizer.setOnMouseDragged(event -> {
                if (from == null) {
                    return;
                }
                double delta = switch (side) {
                    case RIGHT -> dragStart - event.getScreenX();
                    case BOTTOM -> dragStart - event.getScreenY();
                    case TOP -> event.getScreenY() - dragStart;
                    default -> event.getScreenX() - dragStart;
                };
                sizes.put(from, Math.max(MIN_SLIDE_SIZE, dragStartSize + delta));
                SlidingArea.this.requestLayout();
                event.consume();
            });
        }

        void show(Tab tab, TabPane from, Side side, boolean sticky) {
            this.tab = tab;
            this.from = from;
            this.index = from.getTabs().indexOf(tab);
            this.side = side;
            this.sticky = sticky;
            // On loan: the group still lists the tab, and its pane does not count as empty.
            Docking.setLending(from);
            moving = true;
            try {
                NbfxTabPane.moveTab(pane, tab, 0);
            } finally {
                moving = false;
            }
            pane.getSelectionModel().select(tab);
            setLeft(null);
            setRight(null);
            setTop(null);
            setBottom(null);
            switch (side) {
                case RIGHT -> {
                    resizer.setCursor(Cursor.H_RESIZE);
                    resizer.setPrefWidth(RESIZER_THICKNESS);
                    resizer.setPrefHeight(Region.USE_COMPUTED_SIZE);
                    setLeft(resizer);
                }
                case BOTTOM -> {
                    resizer.setCursor(Cursor.V_RESIZE);
                    resizer.setPrefHeight(RESIZER_THICKNESS);
                    resizer.setPrefWidth(Region.USE_COMPUTED_SIZE);
                    setTop(resizer);
                }
                case TOP -> {
                    resizer.setCursor(Cursor.V_RESIZE);
                    resizer.setPrefHeight(RESIZER_THICKNESS);
                    resizer.setPrefWidth(Region.USE_COMPUTED_SIZE);
                    setBottom(resizer);
                }
                default -> {
                    resizer.setCursor(Cursor.H_RESIZE);
                    resizer.setPrefWidth(RESIZER_THICKNESS);
                    resizer.setPrefHeight(Region.USE_COMPUTED_SIZE);
                    setRight(resizer);
                }
            }
            setVisible(true);
            FadeTransition fade = new FadeTransition(SLIDE_ANIMATION, this);
            fade.setFromValue(0.2);
            fade.setToValue(1);
            fade.playFromStart();
        }

        /** Hides the panel, giving the tab back to its group at the place it had. */
        void hide() {
            Tab lent = tab;
            TabPane group = from;
            int place = index;
            setVisible(false);
            if (lent != null && group != null && pane.getTabs().contains(lent)) {
                moving = true;
                try {
                    NbfxTabPane.moveTab(group, lent, place);
                } finally {
                    moving = false;
                }
                group.getSelectionModel().select(lent);
            }
            clear();
        }

        /** The slid-in tab left the panel on its own: the panel hides and its group is told. */
        private void tabLeft() {
            TabPane group = from;
            Side was = side;
            setVisible(false);
            clear();
            bars.get(was).markSlid(null);
            if (group != null) {
                bars.get(was).refresh(group);
            }
            SlidingArea.this.requestLayout();
            LOG.fine("The slid-in tab left the panel");
        }

        private void clear() {
            tab = null;
            from = null;
            index = 0;
            sticky = false;
            Docking.setLending(null);
        }
    }
}
