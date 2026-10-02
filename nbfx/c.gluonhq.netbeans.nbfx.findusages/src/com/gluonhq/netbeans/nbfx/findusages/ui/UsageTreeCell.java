package com.gluonhq.netbeans.nbfx.findusages.ui;

import com.gluonhq.netbeans.nbfx.findusages.model.Usage;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.TreeCell;
import javafx.scene.layout.HBox;

/**
 * Renders a {@link UsageNode}: icon plus text, and for a usage its line rendered by
 * {@link UsageLineRenderer} (line number, lexer colours, bold occurrence).
 */
final class UsageTreeCell extends TreeCell<UsageNode> {

    PseudoClass HEADER_PSEUDO_CLASS = PseudoClass.getPseudoClass("header");

    UsageTreeCell() {
        getStyleClass().add("usage-cell");
    }

    @Override
    protected void updateItem(UsageNode node, boolean empty) {
        pseudoClassStateChanged(HEADER_PSEUDO_CLASS, false);
        super.updateItem(node, empty);
        if (empty || node == null) {
            setText(null);
            setGraphic(null);
            return;
        }
        Usage usage = node.usage();
        if (usage == null) {
            setText(node.text());
            setGraphic(node.createGraphic());
            pseudoClassStateChanged(HEADER_PSEUDO_CLASS, node.kind() == UsageNode.Kind.HEADER);
            return;
        }
        setText(null);
        HBox box = new HBox(4, node.createGraphic(), UsageLineRenderer.render(usage));
        box.setAlignment(Pos.CENTER_LEFT);
        setGraphic(box);
    }
}
