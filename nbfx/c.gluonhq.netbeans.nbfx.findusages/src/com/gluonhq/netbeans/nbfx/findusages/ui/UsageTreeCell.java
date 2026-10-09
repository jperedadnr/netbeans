package com.gluonhq.netbeans.nbfx.findusages.ui;

import com.gluonhq.netbeans.nbfx.api.view.CellBreadth;
import com.gluonhq.netbeans.nbfx.findusages.model.Usage;
import com.gluonhq.netbeans.nbfx.findusages.ui.UsageLineRenderer.Run;
import java.util.List;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

/**
 * Renders a {@link UsageNode}: icon plus text for a grouping row, and for a usage its marker and
 * its line as {@link UsageLineRenderer} splits it (line number, lexer colours, bold occurrence).
 * <p>
 * The usage row's nodes are built once and reused: cells are recycled by the tree, so
 * {@link #updateItem} only swaps the marker's image and the text runs' content and styles. The line
 * is a row of {@link Text} nodes rather than a {@code TextFlow}: a flow wraps when the tree measures
 * a cell narrower than the line, so cell heights would change as the tree scrolls and the scroll
 * position would keep correcting itself. The row never shrinks below its content.
 */
final class UsageTreeCell extends TreeCell<UsageNode> {

    private static final PseudoClass HEADER_PSEUDO_CLASS = PseudoClass.getPseudoClass("header");
    private static final String OCCURRENCE_CLASS = "usage-occurrence";

    private final CellBreadth breadth;
    private final ImageView marker;
    private final HBox lineBox;
    private final HBox row;

    /** @param breadth the width the tree's cells share */
    UsageTreeCell(CellBreadth breadth) {
        this.breadth = breadth;
        marker = new ImageView();
        marker.setFitWidth(UsagesIcons.SIZE);
        marker.setFitHeight(UsagesIcons.SIZE);
        marker.setPreserveRatio(true);
        lineBox = new HBox();
        lineBox.setAlignment(Pos.BASELINE_LEFT);
        lineBox.setMinWidth(Region.USE_PREF_SIZE);
        lineBox.getStyleClass().add("usage-line");
        row = new HBox(4, marker, lineBox);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(Region.USE_PREF_SIZE);
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
        marker.setImage(UsagesIcons.image(UsagesIcons.usageIconName(usage)));
        updateLine(UsageLineRenderer.runs(usage));
        setGraphic(row);
    }

    /** Reuses the text nodes of the previous row: one per run, grown or trimmed as needed. */
    private void updateLine(List<Run> runs) {
        List<Node> texts = lineBox.getChildren();
        while (texts.size() < runs.size()) {
            texts.add(new Text());
        }
        if (texts.size() > runs.size()) {
            texts.subList(runs.size(), texts.size()).clear();
        }
        for (int i = 0; i < runs.size(); i++) {
            Run run = runs.get(i);
            Text text = (Text) texts.get(i);
            text.setText(run.text());
            List<String> styles = text.getStyleClass();
            styles.clear();
            styles.add(run.styleClass());
            if (run.occurrence()) {
                styles.add(OCCURRENCE_CLASS);
            }
        }
    }

    /** As wide as the widest cell of the tree seen so far, so the horizontal scroll bar does not flicker (see {@link CellBreadth}). */
    @Override
    protected double computePrefWidth(double height) {
        double own = super.computePrefWidth(height);
        return isEmpty() ? own : breadth.widen(own);
    }
}
