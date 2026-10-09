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
package com.gluonhq.netbeans.nbfx.javanavigator.ui;

import com.gluonhq.netbeans.nbfx.api.view.CellBreadth;
import com.gluonhq.netbeans.nbfx.javanavigator.model.MemberNode;
import com.gluonhq.netbeans.nbfx.javanavigator.model.MemberNode.Segment;
import java.util.List;
import java.util.function.BooleanSupplier;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

/**
 * Renders a {@link MemberNode}: its element icon, then its label with the type names muted
 * (style class {@code member-type}), the name struck through when deprecated, and the whole row
 * muted when inherited (pseudo-class {@code inherited}), as NetBeans' Navigator does.
 * <p>
 * The nodes are built once and reused: cells are recycled by the tree, so {@link #updateItem} only
 * swaps the icon's image and the text runs' content and styles. The label is a row of {@link Text}
 * nodes rather than a {@code TextFlow}: a flow wraps when the cell is measured narrower than its
 * content, so cell heights would change as the tree scrolls and the scroll position would keep
 * correcting itself. The row never shrinks below its content.
 */
final class MemberTreeCell extends TreeCell<MemberNode> {

    private static final PseudoClass INHERITED = PseudoClass.getPseudoClass("inherited");
    private static final String TYPE_CLASS = "member-type";
    private static final String TEXT_CLASS = "member-text";
    private static final String DEPRECATED_CLASS = "member-deprecated";

    private final BooleanSupplier fullyQualifiedNames;
    private final CellBreadth breadth;
    private final HBox row;
    private final ImageView icon;
    private final HBox labelBox;

    /**
     * @param fullyQualifiedNames whether the label shows fully qualified type names
     * @param breadth             the width the tree's cells share
     */
    MemberTreeCell(BooleanSupplier fullyQualifiedNames, CellBreadth breadth) {
        this.fullyQualifiedNames = fullyQualifiedNames;
        this.breadth = breadth;

        icon = new ImageView();
        icon.setFitWidth(NavigatorIcons.SIZE);
        icon.setFitHeight(NavigatorIcons.SIZE);
        icon.setPreserveRatio(true);
        // Takes no room while there is no glyph for the member's kind.
        icon.managedProperty().bind(icon.imageProperty().isNotNull());
        icon.visibleProperty().bind(icon.imageProperty().isNotNull());

        labelBox = new HBox();
        labelBox.setAlignment(Pos.BASELINE_LEFT);
        labelBox.setMinWidth(Region.USE_PREF_SIZE);
        labelBox.getStyleClass().add("member-label");

        row = new HBox(4, icon, labelBox);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(Region.USE_PREF_SIZE);

        getStyleClass().add("member-cell");
        setText(null);
    }

    @Override
    protected void updateItem(MemberNode node, boolean empty) {
        super.updateItem(node, empty);
        if (empty || node == null) {
            setGraphic(null);
            pseudoClassStateChanged(INHERITED, false);
            return;
        }
        pseudoClassStateChanged(INHERITED, node.isInherited());
        icon.setImage(NavigatorIcons.elementImage(node));
        updateLabel(node);
        setGraphic(row);
    }

    /** Reuses the text runs of the previous row: one per segment, grown or trimmed as needed. */
    private void updateLabel(MemberNode node) {
        List<Segment> segments = node.getLabel(fullyQualifiedNames.getAsBoolean());
        List<Node> runs = labelBox.getChildren();
        while (runs.size() < segments.size()) {
            runs.add(new Text());
        }
        if (runs.size() > segments.size()) {
            runs.subList(segments.size(), runs.size()).clear();
        }
        for (int i = 0; i < segments.size(); i++) {
            Segment segment = segments.get(i);
            Text text = (Text) runs.get(i);
            text.setText(segment.text());
            List<String> styles = text.getStyleClass();
            styles.clear();
            styles.add(segment.type() ? TYPE_CLASS : TEXT_CLASS);
            if (i == 0 && node.isDeprecated()) {
                styles.add(DEPRECATED_CLASS);
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
