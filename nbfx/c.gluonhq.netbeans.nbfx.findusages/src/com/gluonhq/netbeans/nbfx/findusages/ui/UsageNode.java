package com.gluonhq.netbeans.nbfx.findusages.ui;

import com.gluonhq.netbeans.nbfx.findusages.model.Usage;
import java.util.function.Supplier;
import javafx.scene.Node;

/**
 * One row of the usages tree: a grouping (project, source root, package, file, enclosing element)
 * or a usage. The graphic is created per request, as a cell cannot share a node with another.
 *
 * @param kind    what the row groups, or {@link Kind#USAGE}
 * @param text    the row's text; for usages the line text, whose occurrence the cell emphasises
 * @param graphic creates the row's icon, or {@code null} for none
 * @param usage   the usage of a {@link Kind#USAGE} row, {@code null} otherwise
 */
record UsageNode(Kind kind, String text, Supplier<Node> graphic, Usage usage) {

    enum Kind {
        HEADER, PROJECT, SOURCE_ROOT, PACKAGE, FILE, ELEMENT, USAGE
    }

    static UsageNode group(Kind kind, String text, Supplier<Node> graphic) {
        return new UsageNode(kind, text, graphic, null);
    }

    static UsageNode of(Usage usage) {
        String name = UsagesIcons.usageIconName(usage);
        return new UsageNode(Kind.USAGE, usage.lineText().strip(), () -> UsagesIcons.view(name), usage);
    }

    Node createGraphic() {
        return graphic == null ? null : graphic.get();
    }

    @Override
    public String toString() {
        return text;
    }
}
