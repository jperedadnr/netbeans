package com.gluonhq.netbeans.nbfx.docking;

import com.gluonhq.netbeans.nbfx.docking.DockTree.Leaf;
import com.gluonhq.netbeans.nbfx.docking.DockTree.Split;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

/**
 * A docking area: the region of a window in which <em>panes</em> are arranged, and rearranged by
 * dragging the components they hold around. A pane is any {@link Node} of the host's choosing (the
 * type parameter {@code N}) that holds components which can be moved from one pane to another: a tab
 * pane holding tabs, an accordion holding titled panes, a box holding tool windows... The area holds a
 * set of <em>permanent</em> panes, each known by a role name (say {@code NAVIGATOR} and
 * {@code MAIN}), and any number of <em>docked</em> panes created on demand.
 * <p>
 * Its content is a tree whose leaves are the panes and whose inner nodes are {@link SplitPane}s, in
 * either orientation, nested to any depth - or a single pane, when it is the only one left. The
 * shape of the tree, without the panes' content, is a {@link DockTree}; {@link #tree()} reads it,
 * {@link #applyTree} rebuilds the area to it (to restore a persisted layout), and
 * {@link #printTree()} prints it.
 *
 * <p>Some shapes, as {@link #printTree()} shows them:
 * </p>
 * <pre>{@code
 *  The default: the permanent panes            One pane: the others are hidden
 *  side by side, left to right.                (empty), or were merged into it.
 *
 *  +-----------+----------------------+        +----------------------------------+
 *  | NAVIGATOR | MAIN                 |        | MAIN                             |
 *  |           |                      |        |                                  |
 *  +-----------+----------------------+        +----------------------------------+
 *
 *  H [0.22]                                    P:MAIN
 *  |- P:NAVIGATOR
 *  '- P:MAIN
 *
 *  A component dropped on the bottom band      ... and another dropped on the right
 *  of MAIN: a docked pane below it (the        edge of that docked pane: it joins a
 *  right column becomes a vertical split)      new horizontal split at the bottom.
 *
 *  +-----------+----------------------+        +-----------+----------------------+
 *  | NAVIGATOR | MAIN                 |        | NAVIGATOR | MAIN                 |
 *  |           |                      |        |           |                      |
 *  |           +----------------------+        |           +-----------+----------+
 *  |           | docked               |        |           | docked    | docked   |
 *  +-----------+----------------------+        +-----------+-----------+----------+
 *
 *  H [0.22]                                    H [0.22]
 *  |- P:NAVIGATOR                               |- P:NAVIGATOR
 *  '- V [0.65]                                  '- V [0.65]
 *     |- P:MAIN                                    |- P:MAIN
 *     '- L                                         '- H [0.50]
 *                                                     |- L
 *                                                     '- L
 *
 *  A component dropped along the bottom edge of the whole area: a docked pane spanning it (the
 *  previous tree becomes the top child of a new vertical split).
 *
 *  +-----------+----------------------+        V [0.70]
 *  | NAVIGATOR | MAIN                 |        |- H [0.22]
 *  |           |                      |        |  |- P:NAVIGATOR
 *  +-----------+----------------------+        |  '- P:MAIN
 *  | docked                           |        '- L
 *  +-----------+----------------------+
 * }</pre>
 * Where a dragged component docks is decided by bands along the edges of the hovered pane and of the
 * whole area: the wide right and bottom bands ({@link #SPLIT_ZONE}) and the thin left and top ones
 * ({@link #SPLIT_EDGE_ZONE}) dock on that side of the pane, the thin perimeter of the area docks along
 * that side of the area, and the middle of a pane simply adds the component to it:
 * <pre>{@code
 *  +---------------------------------------+
 *  |  area edge: dock along the whole side |
 *  |  +--------------------------------+   |
 *  |  |  pane top edge: dock above     |   |
 *  |  |--------------------------------|   |
 *  |  ||                       | right ||  |
 *  |  ||   join the pane's     | band: ||  |
 *  |  ||   components          | dock  ||  |
 *  |  ||                       | right ||  |
 *  |  ||-----------------------+-------||  |
 *  |  ||  bottom band: dock below      ||  |
 *  |  +---------------------------------+  |
 *  +---------------------------------------+
 * }</pre>
 * <p>
 * Panes come and go with their components, so the area shows no empty one but the main pane. A docked
 * pane exists only while it holds a component: it is created when one is dropped on an edge (or a
 * persisted layout puts one there) and removed - collapsing the split it was in - once its last
 * component leaves. A permanent pane leaves the area while it is empty and other panes remain, and
 * comes back where it was as soon as it receives a component again. The main pane is the exception:
 * like the editor area in NetBeans it never hides nor moves - emptied by closing, floating or moving
 * its components away it stays, empty, as on a fresh start, rather than letting the other panes fill
 * its place; but if a docked pane sits next to it in its split - directly, or as the nearest leaf of
 * a nested split - that pane's components move into it and the docked pane goes (the main pane
 * taking its slot in the nested split), the way an emptied editor mode gives way to its neighbour.
 * <p>
 * The area never looks inside a pane: the panes come from the {@link DockHost}, which also tells
 * when one is empty and moves the components of one pane into another, and the area only arranges
 * them. Drags are the host's business too: it asks {@link #dropTargetAt} where a drag hovering one of
 * the panes would dock, shows it with {@link #showDropLine}, and on the drop either
 * {@linkplain #split(DropTarget) splits} a new pane there and moves the component into it or, when
 * the component is the last of its pane, {@linkplain #relocate relocates} that pane.
 */
public final class DockArea<N extends Node> extends StackPane {

    private static final Logger LOG = Logger.getLogger(DockArea.class.getName());

    /** Default share of the split pane a newly docked pane takes from the pane it splits. */
    public static final double DEFAULT_SHARE = 0.35;
    /**
     * The share of a pane's width (height) - from its right (bottom) edge - over which a dragged
     * component is offered docking to the right of (below) the pane rather than joining the pane.
     */
    public static final double SPLIT_ZONE = 0.3;
    /**
     * The thin share of a pane's width (height) - from its left (top) edge - over which a dragged
     * component is offered docking to the left of (above) the pane, and the share of the whole area
     * around its perimeter over which it is offered docking along that side of the area.
     */
    public static final double SPLIT_EDGE_ZONE = 0.02;

    private static final String STYLE_CLASS = "nbfx-dock-area";
    private static final String SPLIT_STYLE_CLASS = "nbfx-dock-split";
    private static final String DROP_LINE_STYLE_CLASS = "nbfx-drop-line";
    private static final PseudoClass VERTICAL = PseudoClass.getPseudoClass("vertical");
    private static final PseudoClass FRAME = PseudoClass.getPseudoClass("frame");
    private static final double DROP_LINE_THICKNESS = 4;
    private static final PseudoClass DOCK_FOCUSED = PseudoClass.getPseudoClass("dock-focused");

    /** Every area created, so a pane's area can be looked up. */
    private static final List<DockArea<?>> AREAS = new ArrayList<>();

    private final DockHost<N> host;
    /** The permanent panes by role, in default left-to-right order. */
    private final SequencedMap<String, N> primaries = new LinkedHashMap<>();
    private final List<Double> defaultDividers;
    private Node root;
    /** Where each hidden permanent pane goes back: next to this leaf, on this side; absent while it shows. */
    private final Map<N, Anchor<N>> primaryAnchors = new HashMap<>();
    /**
     * Each pane's emptiness, from the host, held here for as long as the pane is in the area: a
     * binding (say over a tab pane's tab list) is only weakly referenced by what it observes, so without
     * a strong reference it would be collected and its subscribers never called again.
     */
    private final Map<N, ObservableValue<Boolean>> emptiness = new HashMap<>();

    private record Anchor<N>(N leaf, Side side) {}
    /**
     * The line marking the edge a dropped component would dock on. Unmanaged and drawn over the panes, so
     * showing it never moves them (a border would, and the mouse leaving the moved edge would loop).
     */
    private final Region dropLine = new Region();

    /**
     * Creates the area holding the given permanent panes, laid out in the {@linkplain #defaultTree()
     * default shape}: side by side, left to right, in the order given, the dividers between them at
     * {@code defaultDividers} (one fewer than panes; equal shares when empty). The last pane is the
     * <em>main</em> one: where a reshaped area collects the components of the panes it disposes of, and
     * the one shown as focused when the focus owner is not in any pane.
     *
     * @param host creates, disposes of and moves components between the panes, and tells when one
     *        is empty; the area never looks inside a pane itself
     * @param primaries the permanent panes keyed by role name - a word (letters, digits and
     *        underscores), as it is written into persisted {@link DockTree}s
     * @param defaultDividers the divider positions of the default shape, or empty for equal shares
     */
    public DockArea(DockHost<N> host, SequencedMap<String, N> primaries, List<Double> defaultDividers) {
        this.host = Objects.requireNonNull(host);
        if (primaries.isEmpty()) {
            throw new IllegalArgumentException("An area needs a permanent pane");
        }
        if (!defaultDividers.isEmpty() && defaultDividers.size() != primaries.size() - 1) {
            throw new IllegalArgumentException(defaultDividers.size() + " dividers for " + primaries.size() + " panes");
        }
        primaries.forEach((role, primary) -> {
            if (!role.matches("\\w+") || this.primaries.containsValue(primary)) {
                throw new IllegalArgumentException("Not a distinct permanent pane with a word as role: " + role);
            }
            this.primaries.put(role, Objects.requireNonNull(primary));
            // Only a component arriving brings a hidden pane back: a restored layout may leave the pane
            // out of the tree while its components are still being moved to the panes that replace it,
            // and the removals must not put it back in - which they cannot, as the pane stays empty.
            emptinessOf(primary).subscribe((_, empty) -> {
                if (Boolean.TRUE.equals(empty)) {
                    Platform.runLater(() -> hidePrimaryIfEmpty(primary));
                } else {
                    showPrimary(primary);
                }
            });
        });
        if (defaultDividers.isEmpty()) {
            List<Double> shares = new ArrayList<>();
            for (int i = 0; i < primaries.size(); i++) {
                shares.add(1.0 / primaries.size());
            }
            this.defaultDividers = DockTrees.dividersOf(shares);
        } else {
            this.defaultDividers = List.copyOf(defaultDividers);
        }
        dropLine.getStyleClass().add(DROP_LINE_STYLE_CLASS);
        dropLine.setManaged(false);
        dropLine.setMouseTransparent(true);
        dropLine.setVisible(false);
        getStyleClass().add(STYLE_CLASS);
        setRoot(build(defaultTree()));
        AREAS.add(this);
    }

    @Override
    public String getUserAgentStylesheet() {
        return DockArea.class.getResource("docking.css").toExternalForm();
    }

    /** The default shape: the permanent panes side by side, in order, at the default dividers. */
    public DockTree defaultTree() {
        List<DockTree> leaves = new ArrayList<>();
        for (String role : primaries.keySet()) {
            leaves.add(DockTree.primary(role));
        }
        if (leaves.size() == 1) {
            return leaves.getFirst();
        }
        return new DockTree.Split(Orientation.HORIZONTAL, leaves, defaultDividers);
    }

    /** Whether {@code pane} is one of this area's permanent panes. */
    public boolean isPrimary(Node pane) {
        return primaries.containsValue(pane);
    }

    /** Whether the permanent pane {@code primary} is currently out of the area, being empty. */
    public boolean isPrimaryHidden(N primary) {
        return isPrimary(primary) && root != primary && parentSplitOf(root, primary) == null;
    }

    /** The permanent pane playing {@code role}, or {@code null} if this area has none. */
    public N primary(String role) {
        return primaries.get(role);
    }

    /** The role of the permanent pane {@code primary}, or {@code null} if it is not one. */
    public String roleOf(N primary) {
        for (Map.Entry<String, N> entry : primaries.entrySet()) {
            if (entry.getValue() == primary) {
                return entry.getKey();
            }
        }
        return null;
    }

    /** The main pane: the last permanent one (see the constructor). */
    public N mainPane() {
        return primaries.lastEntry().getValue();
    }

    /** Whether {@code pane} is currently one of this area's panes, permanent or docked (a hidden permanent pane is not). */
    public boolean contains(Node pane) {
        return leaves().contains(pane);
    }

    /** Every pane of the area in depth-first order - the order {@link #tree()} lists its leaves in. */
    public List<N> leaves() {
        List<N> leaves = new ArrayList<>();
        collectLeaves(root, leaves);
        return leaves;
    }

    private void collectLeaves(Node node, List<N> leaves) {
        if (isSplit(node)) {
            ((SplitPane) node).getItems().forEach(item -> collectLeaves(item, leaves));
        } else {
            leaves.add(leaf(node));
        }
    }

    /** Whether {@code node} is one of the splits of this area (rather than a pane, whatever its class). */
    private static boolean isSplit(Node node) {
        return node instanceof SplitPane && node.getStyleClass().contains(SPLIT_STYLE_CLASS);
    }

    /** {@code node}, a leaf of this area, as the pane it is. */
    @SuppressWarnings("unchecked")
    private N leaf(Node node) {
        return (N) node;
    }

    /** The current shape of the area. */
    public DockTree tree() {
        return treeOf(root);
    }

    /** A multi-line print-out of the current shape for debugging; see {@link #printTree(Function)}. */
    public String printTree() {
        return DockTrees.print(tree());
    }

    /**
     * A multi-line print-out of the current shape for debugging, each leaf followed by what
     * {@code paneDetail} says of its pane - what a pane holds is the host's business, so the host
     * passes e.g. {@code pane -> pane.getTabs().stream().map(MyTabs::titleOf).toList()}; see
     * {@link DockTrees#print(DockTree, java.util.function.IntFunction)}.
     */
    public String printTree(Function<N, ?> paneDetail) {
        List<N> leaves = leaves();
        return DockTrees.print(tree(), index -> String.valueOf(paneDetail.apply(leaves.get(index))));
    }

    private DockTree treeOf(Node node) {
        if (isSplit(node)) {
            SplitPane split = (SplitPane) node;
            List<DockTree> children = split.getItems().stream().map(this::treeOf).toList();
            List<Double> dividers = new ArrayList<>();
            for (double position : split.getDividerPositions()) {
                dividers.add(position);
            }
            return new Split(split.getOrientation(), children, dividers);
        }
        return isPrimary(node) ? DockTree.primary(roleOf(leaf(node))) : DockTree.DOCKED;
    }

    /**
     * Rebuilds the area in the shape of {@code tree}, whose permanent leaves must name distinct roles
     * this area has a pane for - a permanent pane the tree leaves out is hidden. Every docked pane the
     * area had is disposed and its components moved to the {@linkplain #mainPane() main pane} first,
     * unless the area already has that shape, in which case only the dividers are set. The docked
     * panes created here are empty and remove themselves on the next pulse unless a component is
     * placed in them.
     *
     * @return the panes of the new shape, in depth-first order (see {@link #leaves()})
     */
    public List<N> applyTree(DockTree tree) {
        if (!accepts(tree)) {
            throw new IllegalArgumentException("Not a shape of this area's permanent panes: " + DockTrees.format(tree));
        }
        if (!DockTrees.sameShape(tree(), tree)) {
            N keeper = mainPane();
            for (N pane : leaves()) {
                if (!isPrimary(pane)) {
                    host.moveContent(pane, keeper);
                    dispose(pane);
                }
            }
            primaryAnchors.clear();
            setRoot(build(tree));
        }
        applyDividers(root, tree);
        Node built = root;
        Platform.runLater(() -> applyDividers(built, tree));
        return leaves();
    }

    /** Whether {@code tree} names only permanent roles this area has panes for, each at most once. */
    public boolean accepts(DockTree tree) {
        if (tree == null || !tree.hasDistinctPrimaries()) {
            return false;
        }
        return tree.leaves().stream()
                .noneMatch(leaf -> leaf.isPrimary() && primaryOf(leaf) == null);
    }

    private N primaryOf(Leaf leaf) {
        return primaries.get(leaf.primary());
    }

    private void setRoot(Node node) {
        root = node;
        getChildren().setAll(node, dropLine);
    }

    private Node build(DockTree tree) {
        return switch (tree) {
            case Leaf leaf -> leaf.isPrimary() ? primaryOf(leaf) : newDockPane();
            case Split split -> {
                SplitPane pane = newSplit(split.orientation());
                split.children().forEach(child -> pane.getItems().add(build(child)));
                yield pane;
            }
        };
    }

    private static void applyDividers(Node node, DockTree tree) {
        if (isSplit(node) && tree instanceof Split shape) {
            SplitPane split = (SplitPane) node;
            split.setDividerPositions(shape.dividers().stream()
                    .mapToDouble(Double::doubleValue)
                    .toArray());
            for (int i = 0; i < split.getItems().size() && i < shape.children().size(); i++) {
                applyDividers(split.getItems().get(i), shape.children().get(i));
            }
        }
    }

    /**
     * Docks a new (empty) pane next to {@code target} - one of this area's panes - on its
     * {@code side}, taking {@link #DEFAULT_SHARE} of the target's space. When the target already sits
     * in a split of the matching orientation the pane joins that split; otherwise the target is
     * replaced by a new split of the two. The pane removes itself on the next pulse unless a component
     * is placed in it.
     */
    public N split(N target, Side side) {
        if (!contains(Objects.requireNonNull(target))) {
            throw new IllegalArgumentException("Not a pane of this area");
        }
        N pane = newDockPane();
        splitNode(target, side, pane, DEFAULT_SHARE);
        return pane;
    }

    /** Docks a new (empty) pane at {@code target}; see {@link #split(Node, Side)} and {@link #splitArea}. */
    public N split(DropTarget<N> target) {
        return target.isArea() ? splitArea(target.side()) : split(target.pane(), target.side());
    }

    /**
     * Docks a new (empty) pane along the whole area's {@code side}, taking {@link #DEFAULT_SHARE} of
     * its space; see {@link #split(Node, Side)}.
     */
    public N splitArea(Side side) {
        N pane = newDockPane();
        splitNode(root, side, pane, DEFAULT_SHARE);
        return pane;
    }

    /**
     * The docked pane along the whole area's {@code side}, created if there is none. A pane created
     * here must receive a component before the next pulse, or it removes itself again.
     */
    public N edgePane(Side side) {
        boolean horizontal = side == Side.LEFT || side == Side.RIGHT;
        if (isSplit(root) && ((SplitPane) root).getOrientation() == (horizontal ? Orientation.HORIZONTAL : Orientation.VERTICAL)) {
            List<Node> items = ((SplitPane) root).getItems();
            Node edge = side == Side.LEFT || side == Side.TOP ? items.getFirst() : items.getLast();
            if (!isSplit(edge) && !isPrimary(edge)) {
                return leaf(edge);
            }
        }
        return splitArea(side);
    }

    /**
     * The docked pane right below the permanent pane {@code primary}, created if there is none (see
     * {@link #edgePane}); a hidden primary is shown again first. Returns {@code null} when
     * {@code primary} is not a permanent pane of this area.
     */
    public N paneBelow(N primary) {
        if (!isPrimary(primary)) {
            return null;
        }
        if (isPrimaryHidden(primary)) {
            showPrimary(primary);
        }
        SplitPane parent = parentSplitOf(root, primary);
        if (parent != null && parent.getOrientation() == Orientation.VERTICAL) {
            int index = parent.getItems().indexOf(primary);
            if (index + 1 < parent.getItems().size()) {
                Node next = parent.getItems().get(index + 1);
                if (!isSplit(next) && !isPrimary(next)) {
                    return leaf(next);
                }
            }
        }
        return split(primary, Side.BOTTOM);
    }

    // --- Drops -----------------------------------------------------------------------------------

    /**
     * Where a component dragged to scene coordinates {@code (sceneX, sceneY)} over {@code pane} - one
     * of this area's panes - would dock, or {@code null} where it would simply join the pane. The
     * outer {@link #SPLIT_EDGE_ZONE} of the whole area docks along that side of the area; then, within
     * the pane, the thin left/top edge and the wider right/bottom band ({@link #SPLIT_ZONE}) dock on
     * that side of the pane, the nearer edge winning where the bands overlap.
     */
    public DropTarget<N> dropTargetAt(N pane, double sceneX, double sceneY) {
        Bounds size = pane.getLayoutBounds();
        if (!contains(pane) || size.getWidth() <= 0 || size.getHeight() <= 0) {
            return null;
        }
        Point2D inArea = sceneToLocal(sceneX, sceneY);
        Side areaSide = edgeSide(inArea.getX(), inArea.getY(), getWidth(), getHeight(),
                SPLIT_EDGE_ZONE, SPLIT_EDGE_ZONE);
        if (areaSide != null) {
            return new DropTarget<>(null, areaSide);
        }
        Point2D inPane = pane.sceneToLocal(sceneX, sceneY);
        Side paneSide = edgeSide(inPane.getX(), inPane.getY(), size.getWidth(), size.getHeight(),
                SPLIT_EDGE_ZONE, SPLIT_ZONE);
        return paneSide == null ? null : new DropTarget<>(pane, paneSide);
    }

    /**
     * The side of a {@code width} x {@code height} box whose edge band {@code (x, y)} falls in, or
     * {@code null} for the middle. Left/top bands are {@code startShare} of the box wide, right/bottom
     * ones {@code endShare}; where bands overlap the one the point is proportionally deeper in wins.
     */
    private static Side edgeSide(double x, double y, double width, double height, double startShare, double endShare) {
        Side best = null;
        double bestDepth = 1;
        double[] depths = {
            x / (width * startShare),                 // LEFT
            y / (height * startShare),                // TOP
            (width - x) / (width * endShare),         // RIGHT
            (height - y) / (height * endShare)        // BOTTOM
        };
        Side[] sides = {Side.LEFT, Side.TOP, Side.RIGHT, Side.BOTTOM};
        for (int i = 0; i < sides.length; i++) {
            if (depths[i] >= 0 && depths[i] < bestDepth) {
                best = sides[i];
                bestDepth = depths[i];
            }
        }
        return best;
    }

    /**
     * Moves {@code pane}, one of this area's panes, out of its place and docks it at {@code target}
     * (see {@link #dropTargetAt}) with its components, taking {@link #DEFAULT_SHARE} of the target's
     * space - what a host does when the pane's only component is dragged there, rather than emptying
     * the pane into a new one. Next to itself, or alone in the area, the pane stays where it is. The
     * main pane never moves: like the editor area in NetBeans it keeps its place, and its components
     * go to a new docked pane at the target instead, leaving it empty.
     */
    public void relocate(N pane, DropTarget<N> target) {
        if (!contains(Objects.requireNonNull(pane))) {
            throw new IllegalArgumentException("Not a pane of this area");
        }
        if (pane == target.pane() || pane == root) {
            return;
        }
        if (pane == mainPane()) {
            host.moveContent(pane, split(target));
            return;
        }
        detach(pane);
        splitNode(target.isArea() ? root : target.pane(), target.side(), pane, DEFAULT_SHARE);
        LOG.fine(() -> "Moved a pane " + target.side() + " of " + (target.isArea() ? "the area" : "a pane"));
    }

    /** Draws the drop line where {@code target} (see {@link #dropTargetAt}) would dock a component. */
    public void showDropLine(DropTarget<N> target) {
        showDropLine(target.isArea() ? this : target.pane(), target.side());
    }

    /** Puts {@code pane} (a new docked pane, or the returning primary) next to {@code target}, taking {@code share} of its space. */
    private void splitNode(Node target, Side side, N pane, double share) {
        Orientation orientation = side == Side.LEFT || side == Side.RIGHT ? Orientation.HORIZONTAL : Orientation.VERTICAL;
        boolean before = side == Side.LEFT || side == Side.TOP;
        SplitPane parent = target == root ? null : parentSplitOf(root, target);
        if (target == root && isSplit(root) && ((SplitPane) root).getOrientation() == orientation) {
            // Along the whole area, which is already split this way: join at its end.
            SplitPane split = (SplitPane) root;
            double[] sizes = sizesOf(split);
            double[] resized = new double[sizes.length + 1];
            for (int i = 0; i < sizes.length; i++) {
                resized[before ? i + 1 : i] = sizes[i] * (1 - share);
            }
            resized[before ? 0 : sizes.length] = share;
            split.getItems().add(before ? 0 : split.getItems().size(), pane);
            applySizes(split, resized);
        } else if (parent != null && parent.getOrientation() == orientation) {
            // Next to a pane of a split with the right orientation: join it beside the target.
            double[] sizes = sizesOf(parent);
            int index = parent.getItems().indexOf(target);
            double[] resized = new double[sizes.length + 1];
            int insertAt = before ? index : index + 1;
            for (int i = 0, j = 0; i < resized.length; i++) {
                if (i == insertAt) {
                    resized[i] = sizes[index] * share;
                } else {
                    resized[i] = j == index ? sizes[j] * (1 - share) : sizes[j];
                    j++;
                }
            }
            parent.getItems().add(insertAt, pane);
            applySizes(parent, resized);
        } else {
            // Replace the target by a split of it and the new pane.
            SplitPane split = newSplit(orientation);
            replace(target, split, parent);
            split.getItems().addAll(before ? List.of(pane, target) : List.of(target, pane));
            split.setDividerPositions(before ? share : 1 - share);
        }
        LOG.fine(() -> "Docked a pane " + side + " of " + (target == root ? "the area" : "a pane"));
    }

    private void replace(Node old, Node replacement, SplitPane parent) {
        if (old == root) {
            setRoot(replacement);
        } else {
            double[] sizes = sizesOf(parent);
            parent.getItems().set(parent.getItems().indexOf(old), replacement);
            applySizes(parent, sizes);
        }
    }

    private N newDockPane() {
        N pane = host.createDockedPane();
        emptinessOf(pane).subscribe((_, empty) -> {
            if (Boolean.TRUE.equals(empty)) {
                // A reorder removes then re-adds a component within the same pulse: check again later.
                Platform.runLater(() -> removeIfEmpty(pane));
            }
        });
        Platform.runLater(() -> removeIfEmpty(pane));
        return pane;
    }

    private ObservableValue<Boolean> emptinessOf(N pane) {
        return emptiness.computeIfAbsent(pane, host::isEmpty);
    }

    private boolean isEmpty(N pane) {
        return Boolean.TRUE.equals(emptinessOf(pane).getValue());
    }

    private static SplitPane newSplit(Orientation orientation) {
        SplitPane split = new SplitPane();
        split.setOrientation(orientation);
        split.getStyleClass().add(SPLIT_STYLE_CLASS);
        return split;
    }

    private void removeIfEmpty(N pane) {
        if (!contains(pane) || isPrimary(pane) || !isEmpty(pane)) {
            return;
        }
        if (pane == root) {
            // The last docked pane, with every permanent pane hidden: they take the area back.
            primaryAnchors.clear();
            setRoot(build(defaultTree()));
            dispose(pane);
            LOG.fine("Removed the last emptied docked pane");
        } else if (detach(pane)) {
            dispose(pane);
            LOG.fine("Removed an emptied docked pane");
            if (!hasDockedPane()) {
                // A restored layout may have left the main pane out; with the last docked pane gone it comes
                // back, empty, rather than leaving the other permanent panes to fill the area.
                showPrimary(mainPane());
            }
        }
    }

    /** Whether the area holds a pane that is not a permanent one. */
    private boolean hasDockedPane() {
        return leaves().stream().anyMatch(leaf -> !isPrimary(leaf));
    }

    private void dispose(N dockedPane) {
        emptiness.remove(dockedPane);
        host.disposeDockedPane(dockedPane);
    }

    /**
     * Takes the empty permanent pane {@code primary} out of the area while other panes remain,
     * remembering the leaf it sat next to so {@link #showPrimary} can put it back there. The main pane
     * never hides: emptied by closing, floating or moving its components away, it stays, empty, as on a
     * fresh start - like the editor area in NetBeans, the other panes do not take its place.
     */
    private void hidePrimaryIfEmpty(N primary) {
        if (!isEmpty(primary) || root == primary) {
            return;
        }
        if (primary == mainPane()) {
            absorbNeighbourInto(primary);
            return;
        }
        SplitPane parent = parentSplitOf(root, primary);
        if (parent == null) {
            return;
        }
        int index = parent.getItems().indexOf(primary);
        boolean horizontal = parent.getOrientation() == Orientation.HORIZONTAL;
        List<N> neighbours = new ArrayList<>();
        if (index > 0) {
            collectLeaves(parent.getItems().get(index - 1), neighbours);
            primaryAnchors.put(primary, new Anchor<>(neighbours.getLast(), horizontal ? Side.RIGHT : Side.BOTTOM));
        } else {
            collectLeaves(parent.getItems().get(index + 1), neighbours);
            primaryAnchors.put(primary, new Anchor<>(neighbours.getFirst(), horizontal ? Side.LEFT : Side.TOP));
        }
        detach(primary);
        LOG.fine(() -> "Hid the empty " + roleOf(primary) + " pane");
    }

    /**
     * Fills the emptied main pane with the components of the docked pane nearest to it in its split
     * (looking after it first, else before), which then goes away. Two content panes side by side
     * with one of them empty is what NetBeans' editor area never shows: the remaining mode takes the
     * emptied one's place. When the neighbour is a single docked pane it simply hands its components
     * over and leaves its room to the main pane. When the neighbour is a split, the main pane moves
     * into it, taking the place of that split's docked leaf nearest to it - so that a column of two
     * editors next to an emptied main pane becomes a column of the main pane and the other editor,
     * rather than leaving an empty pane beside them. Nothing happens when no docked pane is a
     * neighbour - the main pane then stays, empty.
     */
    private void absorbNeighbourInto(N main) {
        SplitPane parent = parentSplitOf(root, main);
        if (parent == null) {
            return;
        }
        int index = parent.getItems().indexOf(main);
        for (int i : new int[] {index + 1, index - 1}) {
            if (i < 0 || i >= parent.getItems().size()) {
                continue;
            }
            Node node = parent.getItems().get(i);
            if (!isSplit(node)) {
                if (!isPrimary(node)) {
                    @SuppressWarnings("unchecked")
                    N neighbour = (N) node;
                    host.moveContent(neighbour, main);
                    LOG.fine("The emptied main pane took over its neighbour's components");
                    return;
                }
                continue;
            }
            List<N> leaves = new ArrayList<>();
            collectLeaves(node, leaves);
            if (i < index) {
                Collections.reverse(leaves);
            }
            for (N leaf : leaves) {
                if (!isPrimary(leaf)) {
                    takePlaceOf(main, leaf);
                    return;
                }
            }
        }
    }

    /** Moves the empty main pane into the docked pane {@code leaf}'s slot, with its components, and disposes of {@code leaf}. */
    private void takePlaceOf(N main, N leaf) {
        detach(main);
        replace(leaf, main, parentSplitOf(root, leaf));
        host.moveContent(leaf, main);
        dispose(leaf);
        LOG.fine("The emptied main pane took the place of the nearest docked pane in the split next to it");
    }

    /**
     * Puts the hidden permanent pane {@code primary} back next to the leaf it left or, if that leaf is
     * gone, along the side of the area the default shape has it on (the first pane along the left,
     * any other along the right), with the share the default shape gives it.
     */
    private void showPrimary(N primary) {
        if (!isPrimaryHidden(primary)) {
            return;
        }
        Anchor<N> anchor = primaryAnchors.remove(primary);
        if (anchor != null && leaves().contains(anchor.leaf())) {
            splitNode(anchor.leaf(), anchor.side(), primary, 1 - DEFAULT_SHARE);
        } else {
            double[] shares = DockTrees.sizesOf(defaultDividers);
            boolean first = primary == primaries.firstEntry().getValue();
            splitNode(root, first ? Side.LEFT : Side.RIGHT, primary, first ? shares[0] : shares[shares.length - 1]);
        }
    }

    /** Removes {@code pane} from the area, collapsing its split if one child is left; false if it is not in a split. */
    private boolean detach(N pane) {
        SplitPane parent = parentSplitOf(root, pane);
        if (parent == null) {
            return false;
        }
        double[] sizes = sizesOf(parent);
        int index = parent.getItems().indexOf(pane);
        int heir = heirOf(parent, index);
        parent.getItems().remove(index);
        if (parent.getItems().size() == 1) {
            Node remaining = parent.getItems().getFirst();
            parent.getItems().clear();
            replace(parent, remaining, parentSplitOf(root, parent));
        } else {
            double[] resized = new double[sizes.length - 1];
            for (int i = 0, j = 0; i < sizes.length; i++) {
                if (i != index) {
                    resized[j++] = sizes[i];
                }
            }
            resized[heir > index ? heir - 1 : heir] += sizes[index];
            applySizes(parent, resized);
        }
        return true;
    }

    /**
     * The neighbour in {@code parent} that inherits the space of the item at {@code index} when it
     * leaves: a content-holding one - a docked pane, or the main pane - over a side one (a permanent
     * pane other than the main), so that closing the editors next to the navigator hands their room to
     * the remaining editors rather than widening the navigator; the previous neighbour when both
     * (or neither) qualify.
     */
    private int heirOf(SplitPane parent, int index) {
        int before = index - 1;
        int after = index + 1;
        if (before < 0) {
            return after;
        }
        if (after >= parent.getItems().size()) {
            return before;
        }
        return holdsContentPane(parent.getItems().get(before)) || !holdsContentPane(parent.getItems().get(after))
                ? before : after;
    }

    /** Whether {@code node} is, or contains, a docked pane or the main pane. */
    private boolean holdsContentPane(Node node) {
        List<N> leaves = new ArrayList<>();
        collectLeaves(node, leaves);
        return leaves.stream().anyMatch(leaf -> !isPrimary(leaf) || leaf == mainPane());
    }

    /**
     * Draws the drop line along the {@code side} edge of {@code target} - one of this area's panes,
     * or the area itself - over the panes, without moving any of them.
     */
    public void showDropLine(Node target, Side side) {
        Bounds b = target == this ? getLayoutBounds() : sceneToLocal(target.localToScene(target.getBoundsInLocal()));
        double t = DROP_LINE_THICKNESS;
        switch (side) {
            case TOP -> dropLine.resizeRelocate(b.getMinX(), b.getMinY(), b.getWidth(), t);
            case BOTTOM -> dropLine.resizeRelocate(b.getMinX(), b.getMaxY() - t, b.getWidth(), t);
            case LEFT -> dropLine.resizeRelocate(b.getMinX(), b.getMinY(), t, b.getHeight());
            case RIGHT -> dropLine.resizeRelocate(b.getMaxX() - t, b.getMinY(), t, b.getHeight());
        }
        dropLine.pseudoClassStateChanged(VERTICAL, side == Side.LEFT || side == Side.RIGHT);
        dropLine.pseudoClassStateChanged(FRAME, false);
        dropLine.setVisible(true);
    }

    /**
     * Draws the drop line as a frame around {@code pane} - one of this area's panes - marking that a
     * component would join the pane rather than dock next to it (the {@code frame} pseudo-class of the
     * line; see {@link #showDropLine(Node, Side)}).
     */
    public void showDropFrame(N pane) {
        Bounds b = sceneToLocal(pane.localToScene(pane.getBoundsInLocal()));
        dropLine.resizeRelocate(b.getMinX(), b.getMinY(), b.getWidth(), b.getHeight());
        dropLine.pseudoClassStateChanged(VERTICAL, false);
        dropLine.pseudoClassStateChanged(FRAME, true);
        dropLine.setVisible(true);
    }

    /** Hides the drop line. */
    public void hideDropLine() {
        dropLine.setVisible(false);
    }

    private static SplitPane parentSplitOf(Node node, Node child) {
        if (isSplit(node)) {
            SplitPane split = (SplitPane) node;
            if (split.getItems().contains(child)) {
                return split;
            }
            return split.getItems().stream()
                    .map(item -> parentSplitOf(item, child))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    private static double[] sizesOf(SplitPane split) {
        List<Double> dividers = Arrays.stream(split.getDividerPositions()).boxed().toList();
        return DockTrees.sizesOf(dividers);
    }

    private static void applySizes(SplitPane split, double[] sizes) {
        List<Double> list = Arrays.stream(sizes).boxed().toList();
        double[] positions = DockTrees.dividersOf(list).stream()
                .mapToDouble(Double::doubleValue)
                .toArray();
        split.setDividerPositions(positions);
        Platform.runLater(() -> split.setDividerPositions(positions));
    }

    /**
     * Reflects that keyboard focus is (or is no longer) within this area on the pane that actually
     * holds {@code focusOwner} - the main pane when the owner is elsewhere in the window - through
     * the {@code dock-focused} pseudo-class, for the application's stylesheet to highlight.
     */
    public void markFocused(boolean focused, Node focusOwner) {
        List<N> panes = leaves();
        N target = mainPane();
        for (N pane : panes) {
            if (focused && isWithin(focusOwner, pane)) {
                target = pane;
            }
        }
        for (N pane : panes) {
            pane.pseudoClassStateChanged(DOCK_FOCUSED, focused && pane == target);
        }
    }

    private static boolean isWithin(Node node, Node ancestor) {
        return Stream.iterate(node, Objects::nonNull, Node::getParent)
                .anyMatch(n -> n == ancestor);
    }

    /** The area {@code pane} is a leaf of, or {@code null} if it is not docked in one (a detached or hidden pane). */
    public static DockArea<?> of(Node pane) {
        return AREAS.stream()
                .filter(area -> area.contains(pane))
                .findFirst()
                .orElse(null);
    }
}
