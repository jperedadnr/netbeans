package com.gluonhq.netbeans.nbfx.ui.shell;

import com.gluonhq.netbeans.nbfx.docking.DockArea;
import com.gluonhq.netbeans.nbfx.docking.DropTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

/**
 * A keyboard-driven move of a tab, or of a whole pane, within the main window's {@link DockArea}: the
 * places the tab (pane) could go - joining another pane, docking next to one on any side, docking
 * along a side of the whole area - are shown one at a time with the area's drop line, the arrow keys
 * step to the nearest place in that direction, Enter moves there, Escape (or a mouse press) cancels.
 * Started from the tab context menu's Move / Move Group.
 */
final class TabMover {

    private static final Logger LOG = Logger.getLogger(TabMover.class.getName());
    /** How much farther a candidate may be sideways than ahead and still count as "in that direction". */
    private static final double SIDEWAYS_TOLERANCE = 1.5;

    /** One place to move to: next to {@code pane} on {@code side}, joining it ({@code side == null}), or along the area ({@code pane == null}). */
    private record Candidate(TabPane pane, Side side, Bounds bounds) {

        boolean isJoin() {
            return pane != null && side == null;
        }

        DropTarget<TabPane> target() {
            return new DropTarget<>(pane, side);
        }

        double centerX() {
            return bounds.getCenterX();
        }

        double centerY() {
            return bounds.getCenterY();
        }
    }

    private static TabMover active;

    private final DockArea<TabPane> area;
    private final List<Candidate> candidates;
    private final Consumer<Candidate> onConfirm;
    private final Scene scene;
    private int current;

    private TabMover(DockArea<TabPane> area, List<Candidate> candidates, Consumer<Candidate> onConfirm) {
        this.area = area;
        this.candidates = candidates;
        this.onConfirm = onConfirm;
        this.scene = area.getScene();
    }

    /** Starts moving {@code tab}: to another pane, or to a new pane on one side of a pane or of the area. */
    static void moveTab(Tab tab) {
        DockArea<TabPane> area = Docking.area();
        TabPane source = tab.getTabPane();
        if (area == null || source == null) {
            return;
        }
        boolean alone = source.getTabs().size() == 1;
        List<Candidate> candidates = candidates(area, pane -> pane != source, pane -> !(alone && pane == source));
        start(area, candidates, candidate -> {
            if (candidate.isJoin()) {
                NbfxTabPane.moveTab(candidate.pane(), tab, candidate.pane().getTabs().size());
            } else {
                NbfxTabPane.dock(tab, candidate.target());
            }
            NbfxTabPane.selectTabAndMoveToFront(tab);
        });
    }

    /** Starts moving {@code pane} with all its tabs: into another pane, or next to one, or along the area. */
    static void movePane(TabPane pane) {
        DockArea<TabPane> area = Docking.area();
        if (area == null || pane.getTabs().isEmpty()) {
            return;
        }
        List<Candidate> candidates = candidates(area, other -> other != pane, other -> other != pane);
        Tab selected = pane.getSelectionModel().getSelectedItem();
        start(area, candidates, candidate -> {
            if (candidate.isJoin()) {
                moveAll(pane, candidate.pane());
            } else if (area.contains(pane)) {
                area.relocate(pane, candidate.target());
            } else {
                // A detached window's pane: its tabs join a new pane of the area, and the window closes.
                moveAll(pane, area.split(candidate.target()));
            }
            if (selected != null) {
                NbfxTabPane.selectTabAndMoveToFront(selected);
            }
        });
    }

    private static void moveAll(TabPane from, TabPane into) {
        for (Tab tab : List.copyOf(from.getTabs())) {
            NbfxTabPane.moveTab(into, tab, into.getTabs().size());
        }
    }

    private static void start(DockArea<TabPane> area, List<Candidate> candidates, Consumer<Candidate> onConfirm) {
        cancelActive();
        if (candidates.isEmpty() || area.getScene() == null) {
            return;
        }
        active = new TabMover(area, candidates, onConfirm);
        active.begin();
    }

    private static void cancelActive() {
        if (active != null) {
            active.end();
        }
    }

    /**
     * Every place in {@code area}: for each pane passing {@code joinable}, joining it; for each pane
     * passing {@code splittable}, its four sides; and the four sides of the area (unless a single
     * pane fills it, in which case they would change nothing).
     */
    private static List<Candidate> candidates(DockArea<TabPane> area,
                                              Predicate<TabPane> joinable,
                                              Predicate<TabPane> splittable) {
        List<Candidate> candidates = new ArrayList<>();
        List<TabPane> leaves = area.leaves();
        for (TabPane pane : leaves) {
            Bounds b = area.sceneToLocal(pane.localToScene(pane.getBoundsInLocal()));
            if (joinable.test(pane)) {
                candidates.add(new Candidate(pane, null, b));
            }
            if (splittable.test(pane)) {
                for (Side side : Side.values()) {
                    candidates.add(new Candidate(pane, side, edge(b, side)));
                }
            }
        }
        if (leaves.size() > 1) {
            for (Side side : Side.values()) {
                candidates.add(new Candidate(null, side, edge(area.getLayoutBounds(), side)));
            }
        }
        return candidates;
    }

    /** A thin strip along {@code side} of {@code b}. */
    private static Bounds edge(Bounds b, Side side) {
        double t = 4;
        return switch (side) {
            case TOP -> new BoundingBox(b.getMinX(), b.getMinY(), b.getWidth(), t);
            case BOTTOM -> new BoundingBox(b.getMinX(), b.getMaxY() - t, b.getWidth(), t);
            case LEFT -> new BoundingBox(b.getMinX(), b.getMinY(), t, b.getHeight());
            case RIGHT -> new BoundingBox(b.getMaxX() - t, b.getMinY(), t, b.getHeight());
        };
    }

    private final EventHandler<KeyEvent> keyFilter = this::onKey;
    private final EventHandler<MouseEvent> mouseFilter = e -> end();

    private void begin() {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, mouseFilter);
        if (scene.getWindow() != null) {
            scene.getWindow().requestFocus();
        }
        current = 0;
        show();
        LOG.fine(() -> "Move started with " + candidates.size() + " places");
    }

    private void end() {
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, keyFilter);
        scene.removeEventFilter(MouseEvent.MOUSE_PRESSED, mouseFilter);
        area.hideDropLine();
        if (active == this) {
            active = null;
        }
    }

    private void onKey(KeyEvent e) {
        switch (e.getCode()) {
            case LEFT -> step(-1, 0);
            case RIGHT -> step(1, 0);
            case UP -> step(0, -1);
            case DOWN -> step(0, 1);
            case ENTER -> {
                Candidate chosen = candidates.get(current);
                end();
                onConfirm.accept(chosen);
            }
            case ESCAPE -> end();
            default -> {
                return;
            }
        }
        e.consume();
    }

    /** Steps to the nearest candidate lying in direction {@code (dx, dy)} from the current one, if any. */
    private void step(int dx, int dy) {
        Candidate from = candidates.get(current);
        int best = -1;
        double bestScore = Double.MAX_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            if (i == current) {
                continue;
            }
            Candidate c = candidates.get(i);
            double ahead = dx != 0 ? (c.centerX() - from.centerX()) * dx : (c.centerY() - from.centerY()) * dy;
            double sideways = dx != 0 ? Math.abs(c.centerY() - from.centerY()) : Math.abs(c.centerX() - from.centerX());
            if (ahead <= 0.5 || sideways > ahead * SIDEWAYS_TOLERANCE) {
                continue;
            }
            double score = ahead + sideways * 2;
            if (score < bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best >= 0) {
            current = best;
            show();
        }
    }

    private void show() {
        Candidate c = candidates.get(current);
        if (c.isJoin()) {
            area.showDropFrame(c.pane());
        } else {
            area.showDropLine(c.target());
        }
    }
}
