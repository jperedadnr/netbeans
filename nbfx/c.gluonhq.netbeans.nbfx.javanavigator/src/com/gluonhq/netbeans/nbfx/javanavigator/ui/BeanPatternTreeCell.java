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
import com.gluonhq.netbeans.nbfx.javanavigator.model.BeanPattern;
import com.gluonhq.netbeans.nbfx.javanavigator.model.MemberNode.Segment;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TreeCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;

/**
 * Renders a {@link BeanPattern}: the icon of its kind and mode, then its label with the types muted
 * (style class {@code member-type}). The nodes are built once and reused, as cells are recycled; the
 * label is a row of {@link Text} nodes that never shrinks below its content, so cell heights stay put.
 */
final class BeanPatternTreeCell extends TreeCell<BeanPattern> {

    private final CellBreadth breadth;
    private final ImageView icon;
    private final HBox labelBox;
    private final HBox row;

    /** @param breadth the width the tree's cells share */
    BeanPatternTreeCell(CellBreadth breadth) {
        this.breadth = breadth;

        icon = new ImageView();
        icon.setFitWidth(NavigatorIcons.SIZE);
        icon.setFitHeight(NavigatorIcons.SIZE);
        icon.setPreserveRatio(true);
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
    protected void updateItem(BeanPattern pattern, boolean empty) {
        super.updateItem(pattern, empty);
        if (empty || pattern == null) {
            setGraphic(null);
            return;
        }
        icon.setImage(NavigatorIcons.patternImage(pattern));
        updateLabel(pattern);
        setGraphic(row);
    }

    /** Reuses the text runs of the previous row: one per segment, grown or trimmed as needed. */
    private void updateLabel(BeanPattern pattern) {
        List<Segment> segments = pattern.getLabel();
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
            styles.add(segment.type() ? "member-type" : "member-text");
        }
    }

    /** As wide as the widest cell of the tree seen so far, so the horizontal scroll bar does not flicker (see {@link CellBreadth}). */
    @Override
    protected double computePrefWidth(double height) {
        double own = super.computePrefWidth(height);
        return isEmpty() ? own : breadth.widen(own);
    }
}
