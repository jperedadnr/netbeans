package com.gluonhq.netbeans.nbfx.editor.breadcrumbs;

import com.gluonhq.netbeans.nbfx.api.breadcrumbs.BreadcrumbElement;

import java.util.List;
import java.util.Optional;

/**
 * Helpers shared by the language-specific breadcrumb scanners: the caret-to-element
 * descent and the label condensing rules.
 */
final class BreadcrumbsElements {

    /** Labels longer than this are truncated with an ellipsis. */
    static final int MAX_LABEL_LENGTH = 40;

    private BreadcrumbsElements() {
    }

    /**
     * Descends from the root whose span contains {@code caretOffset} to the deepest element whose
     * span still contains it — the port of NetBeans' {@code rootAndSelection} loop.
     */
    static Optional<BreadcrumbElement> select(List<BreadcrumbElement> roots, int caretOffset) {
        BreadcrumbElement current = containing(roots, caretOffset);
        if (current == null) {
            return Optional.empty();
        }
        for (BreadcrumbElement deeper = containing(current.children(), caretOffset); deeper != null;
                deeper = containing(deeper.children(), caretOffset)) {
            current = deeper;
        }
        return Optional.of(current);
    }

    private static BreadcrumbElement containing(List<BreadcrumbElement> elements, int caretOffset) {
        for (BreadcrumbElement element : elements) {
            if (element.startOffset() <= caretOffset && caretOffset <= element.endOffset()) {
                return element;
            }
        }
        return null;
    }

    /** Collapses whitespace runs and truncates over-long labels with an ellipsis. */
    static String condense(String text) {
        String collapsed = collapse(text);
        if (collapsed.length() > MAX_LABEL_LENGTH) {
            return collapsed.substring(0, MAX_LABEL_LENGTH) + "…";
        }
        return collapsed;
    }

    /** Collapses whitespace runs into single spaces without truncating. */
    static String collapse(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
