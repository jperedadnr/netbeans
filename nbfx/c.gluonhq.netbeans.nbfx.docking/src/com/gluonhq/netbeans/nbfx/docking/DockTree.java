package com.gluonhq.netbeans.nbfx.docking;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import javafx.geometry.Orientation;

/**
 * The shape of a {@link DockArea}: how the window is split into panes, without the panes' content.
 * A tree is either a {@link Leaf} (one pane: a permanent one, named by its role, or a pane docked
 * next to them) or a {@link Split} of two or more child trees along one orientation, with the
 * divider positions between them. Leaves are identified by their position in depth-first order,
 * which is the order {@link DockArea#leaves()} lists the panes in, and the order the session layout
 * persists them in. {@link DockTrees} reads and writes the text form the layout persists, and prints
 * a tree for debugging.
 */
public sealed interface DockTree {

    /**
     * One pane: a permanent one - {@code primary} names its role, and each role appears at most once
     * in a tree, not at all while its pane is hidden - or, with {@code null}, a docked pane.
     */
    record Leaf(String primary) implements DockTree {

        /** Whether this is a permanent pane. */
        public boolean isPrimary() {
            return primary != null;
        }
    }

    /** A docked (non-permanent) pane. */
    DockTree DOCKED = new Leaf(null);

    /** The permanent pane playing {@code role}. */
    static DockTree primary(String role) {
        return new Leaf(Objects.requireNonNull(role));
    }

    /** Two or more trees side by side ({@link Orientation#HORIZONTAL}) or stacked ({@link Orientation#VERTICAL}). */
    record Split(Orientation orientation, List<DockTree> children, List<Double> dividers) implements DockTree {

        public Split {
            children = List.copyOf(children);
            dividers = List.copyOf(dividers);
            if (children.size() < 2) {
                throw new IllegalArgumentException("A split needs at least two children");
            }
            if (dividers.size() != children.size() - 1) {
                throw new IllegalArgumentException("A split of " + children.size() + " needs " + (children.size() - 1) + " dividers");
            }
        }
    }

    /** The number of leaves (panes) in this tree. */
    default int leafCount() {
        return switch (this) {
            case Leaf _ -> 1;
            case Split split -> split.children().stream().mapToInt(DockTree::leafCount).sum();
        };
    }

    /** The leaves in depth-first order. */
    default List<Leaf> leaves() {
        List<Leaf> leaves = new ArrayList<>();
        visitLeaves(this, leaves::add);
        return leaves;
    }

    /** The depth-first index of the leaf of the permanent pane playing {@code role}, or {@code -1} if there is none. */
    default int indexOf(String role) {
        List<Leaf> leaves = leaves();
        return leaves.stream()
                .filter(leaf -> role.equals(leaf.primary()))
                .mapToInt(leaves::indexOf)
                .findFirst()
                .orElse(-1);
    }

    /** Whether no permanent role appears more than once. */
    default boolean hasDistinctPrimaries() {
        Set<String> seen = new HashSet<>();
        return leaves().stream()
                .noneMatch(leaf -> leaf.isPrimary() && !seen.add(leaf.primary()));
    }

    /**
     * This tree without the leaves that {@code keep} rejects (by depth-first index): a split left with
     * one child is replaced by that child, and the space of a dropped child is shared among the rest.
     * Returns {@code null} if no leaf is kept.
     */
    default DockTree retainLeaves(IntPredicate keep) {
        int[] counter = {0};
        return retain(this, keep, counter);
    }

    private static DockTree retain(DockTree tree, IntPredicate keep, int[] counter) {
        if (tree instanceof Leaf leaf) {
            return keep.test(counter[0]++) ? leaf : null;
        }
        Split split = (Split) tree;
        double[] sizes = DockTrees.sizesOf(split.dividers());
        List<DockTree> kept = new ArrayList<>();
        List<Double> keptSizes = new ArrayList<>();
        for (int i = 0; i < split.children().size(); i++) {
            DockTree child = retain(split.children().get(i), keep, counter);
            if (child != null) {
                kept.add(child);
                keptSizes.add(sizes[i]);
            }
        }
        if (kept.isEmpty()) {
            return null;
        }
        if (kept.size() == 1) {
            return kept.getFirst();
        }
        return new Split(split.orientation(), kept, DockTrees.dividersOf(keptSizes));
    }

    private static void visitLeaves(DockTree tree, Consumer<Leaf> visitor) {
        switch (tree) {
            case Leaf leaf -> visitor.accept(leaf);
            case Split split -> split.children().forEach(child -> visitLeaves(child, visitor));
        }
    }
}
