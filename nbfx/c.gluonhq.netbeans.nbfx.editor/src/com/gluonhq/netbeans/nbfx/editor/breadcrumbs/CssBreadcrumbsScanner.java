package com.gluonhq.netbeans.nbfx.editor.breadcrumbs;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbElement;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbsContext;
import com.gluonhq.netbeans.nbfx.api.breadcrumbs.SimpleBreadcrumbElement;
import com.gluonhq.netbeans.nbfx.api.elements.SourceElementKind;
import com.gluonhq.netbeans.nbfx.editor.processor.SourceUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Builds the breadcrumb tree of a CSS source with a single character scan (the same
 * technique as the codefolding {@code CssFoldDetector}), mirroring the NetBeans IDE
 * CSS breadcrumbs: a synthetic {@code Rules} root spans the whole stylesheet (its
 * popup lists every top-level rule), and each matched <code>{ ... }</code> block
 * becomes one breadcrumb labeled with its full selector or at-rule header — comma
 * separated selector lists joined into a single line, never truncated — spanning
 * from the selector's first character to the closing brace, with nested blocks as
 * children.
 *
 * <p>Braces and comment markers inside strings or comments are ignored; an unmatched
 * closing brace produces no element, while an unclosed block extends to the end of
 * the file — so breadcrumbs stay useful while the document is mid-edit.</p>
 */
final class CssBreadcrumbsScanner {

    /** Label of the synthetic root that lists all rules of the stylesheet. */
    static final String RULES_LABEL = "Rules";

    private CssBreadcrumbsScanner() {
    }

    /** Mutable rule node used while the scan is in progress. */
    private static final class Node {

        final String label;
        final int start;
        int end = -1;
        final List<Node> children = new ArrayList<>();

        Node(String label, int start) {
            this.label = label;
            this.start = start;
        }
    }

    /**
     * Scans {@code context.documentText()} and returns the single {@code Rules} root
     * (empty list for an empty source), or {@code null} when the scan was canceled.
     */
    static List<BreadcrumbElement> scan(BreadcrumbsContext context, Cancellation cancellation) {
        String source = context.documentText();
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        Node sheetRoot = new Node(RULES_LABEL, 0);
        Deque<Node> openBlocks = new ArrayDeque<>();
        openBlocks.push(sheetRoot);
        StringBuilder selector = new StringBuilder();
        int selectorStart = -1;

        int i = 0;
        int length = source.length();
        while (i < length) {
            if (cancellation.isCancelled()) {
                return null;
            }
            char c = source.charAt(i);
            if (c == '/' && i + 1 < length && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? length : end + 2;
            } else if (c == '"' || c == '\'') {
                int end = SourceUtils.skipString(source, i);
                if (selectorStart == -1) {
                    selectorStart = i;
                }
                selector.append(source, i, Math.min(end, length));
                i = end;
            } else if (c == '{') {
                String label = BreadcrumbsElements.collapse(selector.toString());
                Node block = new Node(label.isEmpty() ? "{" : label,
                        selectorStart >= 0 ? selectorStart : i);
                openBlocks.peek().children.add(block);
                openBlocks.push(block);
                selector.setLength(0);
                selectorStart = -1;
                i++;
            } else if (c == '}') {
                if (openBlocks.peek() != sheetRoot) {
                    openBlocks.pop().end = i + 1;
                }
                selector.setLength(0);
                selectorStart = -1;
                i++;
            } else if (c == ';') {
                // ends a declaration or an at-rule statement (@import …;)
                selector.setLength(0);
                selectorStart = -1;
                i++;
            } else {
                if (selectorStart == -1 && !Character.isWhitespace(c)) {
                    selectorStart = i;
                }
                selector.append(c);
                i++;
            }
        }
        // Unclosed blocks (mid-edit) extend to the end of the file.
        while (openBlocks.peek() != sheetRoot) {
            openBlocks.pop().end = length;
        }
        sheetRoot.end = length;
        return toElements(sheetRoot);
    }

    /** Converts the mutable node tree into the immutable {@code Rules} breadcrumb root. */
    private static List<BreadcrumbElement> toElements(Node sheetRoot) {
        SimpleBreadcrumbElement root = SimpleBreadcrumbElement.root(sheetRoot.label,
                sheetRoot.start, sheetRoot.end, SourceElementKind.PACKAGE, null, 0);
        addChildren(root, sheetRoot);
        return List.of(root);
    }

    private static void addChildren(SimpleBreadcrumbElement parent, Node node) {
        for (Node child : node.children) {
            addChildren(parent.addChild(child.label, child.start, child.end,
                    SourceElementKind.RULE, null, 0), child);
        }
    }
}
