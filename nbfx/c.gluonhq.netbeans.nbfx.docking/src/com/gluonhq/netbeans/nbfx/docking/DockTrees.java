package com.gluonhq.netbeans.nbfx.docking;

import com.gluonhq.netbeans.nbfx.docking.DockTree.Leaf;
import com.gluonhq.netbeans.nbfx.docking.DockTree.Split;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import javafx.geometry.Orientation;

/**
 * Utilities around {@link DockTree}s: the text form they are persisted in, a readable print-out for
 * debugging, structural comparison, the shape of last resort, and the arithmetic between divider
 * positions and shares.
 * <p>
 * Text form: a leaf is {@code P:ROLE} (a permanent pane) or {@code L} (a docked one); a split is
 * {@code V[dividers](children)} or {@code H[dividers](children)}, dividers and children
 * comma-separated, e.g. {@code H[0.2200](P:NAVIGATOR,V[0.6500](P:MAIN,L))}.
 */
public final class DockTrees {

    private DockTrees() {}

    /** The text form of {@code tree}; see the class comment. */
    public static String format(DockTree tree) {
        StringBuilder text = new StringBuilder();
        format(tree, text);
        return text.toString();
    }

    private static void format(DockTree tree, StringBuilder text) {
        switch (tree) {
            case Leaf leaf -> text.append(leaf.isPrimary() ? "P:" + leaf.primary() : "L");
            case Split split -> {
                text.append(split.orientation() == Orientation.VERTICAL ? 'V' : 'H').append('[');
                for (int i = 0; i < split.dividers().size(); i++) {
                    if (i > 0) {
                        text.append(',');
                    }
                    text.append(String.format(Locale.ROOT, "%.4f", split.dividers().get(i)));
                }
                text.append("](");
                for (int i = 0; i < split.children().size(); i++) {
                    if (i > 0) {
                        text.append(',');
                    }
                    format(split.children().get(i), text);
                }
                text.append(')');
            }
        }
    }

    /** Parses the text form of a tree, returning {@code null} if it is malformed. */
    public static DockTree parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            int[] pos = {0};
            DockTree tree = parse(text.trim(), pos);
            return pos[0] == text.trim().length() ? tree : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static DockTree parse(String text, int[] pos) {
        char c = text.charAt(pos[0]++);
        switch (c) {
            case 'P' -> {
                expect(text, pos, ':');
                int start = pos[0];
                while (pos[0] < text.length() && (Character.isLetterOrDigit(text.charAt(pos[0])) || text.charAt(pos[0]) == '_')) {
                    pos[0]++;
                }
                if (pos[0] == start) {
                    throw new IllegalArgumentException("Missing role");
                }
                return new Leaf(text.substring(start, pos[0]));
            }
            case 'L' -> {
                return DockTree.DOCKED;
            }
            case 'V', 'H' -> {
                expect(text, pos, '[');
                List<Double> dividers = new ArrayList<>();
                for (String field : text.substring(pos[0], text.indexOf(']', pos[0])).split(",")) {
                    if (!field.isBlank()) {
                        dividers.add(Double.parseDouble(field.trim()));
                    }
                }
                pos[0] = text.indexOf(']', pos[0]) + 1;
                expect(text, pos, '(');
                List<DockTree> children = new ArrayList<>();
                while (true) {
                    children.add(parse(text, pos));
                    char next = text.charAt(pos[0]++);
                    if (next == ')') {
                        break;
                    }
                    if (next != ',') {
                        throw new IllegalArgumentException("Unexpected '" + next + "'");
                    }
                }
                return new Split(c == 'V' ? Orientation.VERTICAL : Orientation.HORIZONTAL, children, dividers);
            }
            default -> throw new IllegalArgumentException("Unexpected '" + c + "'");
        }
    }

    private static void expect(String text, int[] pos, char expected) {
        if (text.charAt(pos[0]++) != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "'");
        }
    }

    /**
     * A multi-line print-out of {@code tree} for debugging: one line per node, splits as
     * {@code H [dividers]} / {@code V [dividers]} with their children indented below, leaves as
     * {@code P:ROLE} or {@code L}.
     */
    public static String print(DockTree tree) {
        return print(tree, _ -> "");
    }

    /**
     * Like {@link #print(DockTree)}, with {@code leafDetail} appended to each leaf line - given the
     * leaf's depth-first index, e.g. to name the tabs of the pane that leaf stands for.
     */
    public static String print(DockTree tree, IntFunction<String> leafDetail) {
        StringBuilder out = new StringBuilder();
        print(tree, "", "", out, new int[1], leafDetail);
        return out.toString();
    }

    private static void print(DockTree tree, String prefix, String childPrefix, StringBuilder out,
                              int[] leafIndex, IntFunction<String> leafDetail) {
        out.append(prefix);
        switch (tree) {
            case Leaf leaf -> {
                out.append(leaf.isPrimary() ? "P:" + leaf.primary() : "L");
                String detail = leafDetail.apply(leafIndex[0]++);
                if (detail != null && !detail.isEmpty()) {
                    out.append("  ").append(detail);
                }
                out.append('\n');
            }
            case Split split -> {
                out.append(split.orientation() == Orientation.VERTICAL ? 'V' : 'H').append(" [");
                for (int i = 0; i < split.dividers().size(); i++) {
                    out.append(i > 0 ? ", " : "").append(String.format(Locale.ROOT, "%.2f", split.dividers().get(i)));
                }
                out.append("]\n");
                List<DockTree> children = split.children();
                for (int i = 0; i < children.size(); i++) {
                    boolean last = i == children.size() - 1;
                    print(children.get(i), childPrefix + (last ? "└─ " : "├─ "),
                            childPrefix + (last ? "   " : "│  "), out, leafIndex, leafDetail);
                }
            }
        }
    }

    /**
     * The given leaves ({@code null} entries for docked panes, roles for permanent ones) stacked
     * vertically in equal shares - the shape of last resort when a persisted tree does not fit.
     */
    public static DockTree stacked(List<String> primaries) {
        if (primaries.size() == 1) {
            return new Leaf(primaries.getFirst());
        }
        List<DockTree> leaves = new ArrayList<>();
        List<Double> sizes = new ArrayList<>();
        for (String primary : primaries) {
            leaves.add(new Leaf(primary));
            sizes.add(1.0 / primaries.size());
        }
        return new Split(Orientation.VERTICAL, leaves, dividersOf(sizes));
    }

    /** Whether two trees have the same structure - same splits and leaves - ignoring divider positions. */
    public static boolean sameShape(DockTree a, DockTree b) {
        if (a instanceof Leaf(String primaryA) && b instanceof Leaf(String primaryB)) {
            return Objects.equals(primaryA, primaryB);
        }
        if (a instanceof Split sa && b instanceof Split sb) {
            if (sa.orientation() != sb.orientation() || sa.children().size() != sb.children().size()) {
                return false;
            }
            return IntStream.range(0, sa.children().size())
                    .allMatch(i -> sameShape(sa.children().get(i), sb.children().get(i)));
        }
        return false;
    }

    /** The share of each child given the divider positions between them. */
    public static double[] sizesOf(List<Double> dividers) {
        double[] sizes = new double[dividers.size() + 1];
        double previous = 0;
        for (int i = 0; i < dividers.size(); i++) {
            double position = Math.clamp(dividers.get(i), previous, 1);
            sizes[i] = position - previous;
            previous = position;
        }
        sizes[dividers.size()] = 1 - previous;
        return sizes;
    }

    /** The divider positions separating children of the given shares (normalised to sum to 1). */
    public static List<Double> dividersOf(List<Double> sizes) {
        double total = sizes.stream().mapToDouble(Double::doubleValue).sum();
        List<Double> dividers = new ArrayList<>();
        double cumulative = 0;
        for (int i = 0; i < sizes.size() - 1; i++) {
            cumulative += total > 0 ? sizes.get(i) / total : 1.0 / sizes.size();
            dividers.add(cumulative);
        }
        return dividers;
    }
}
