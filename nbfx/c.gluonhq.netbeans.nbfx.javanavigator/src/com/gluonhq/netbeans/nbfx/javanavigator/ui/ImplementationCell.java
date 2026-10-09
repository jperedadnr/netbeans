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

import com.gluonhq.netbeans.nbfx.javanavigator.model.GoToResolver;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * A row: the glyph of the element with the down-arrow badge, its name, and what encloses it, dimmed.
 */
final class ImplementationCell extends ListCell<GoToResolver.Implementation> {

    private static final PseudoClass UP = PseudoClass.getPseudoClass("up");

    private final ImageView icon;
    /** The badge: a down arrow for an implementation, an up one (the {@code up} pseudo-class) for an ancestor. */
    private final Region arrow;
    private final Label name;
    private final Label enclosing;
    private final HBox row;

    ImplementationCell() {
        icon = new ImageView();
        icon.setFitWidth(NavigatorIcons.SIZE);
        icon.setFitHeight(NavigatorIcons.SIZE);
        icon.setPreserveRatio(true);
        arrow = new Region();
        arrow.getStyleClass().add("implementation-arrow");
        name = new Label();
        name.getStyleClass().add("implementation-name");
        enclosing = new Label();
        enclosing.getStyleClass().add("implementation-enclosing");
        row = new HBox(4, icon, arrow, name, enclosing);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(Region.USE_PREF_SIZE);
        getStyleClass().add("implementation-cell");
        setText(null);
    }

    @Override
    protected void updateItem(GoToResolver.Implementation item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setGraphic(null);
            return;
        }
        icon.setImage(NavigatorIcons.elementImage(item.kind(), item.modifiers()));
        arrow.pseudoClassStateChanged(UP, item.ancestor());
        name.setText(item.name());
        enclosing.setText(item.enclosing().isEmpty() ? "" : "(" + item.enclosing() + ")");
        setGraphic(row);
    }
}
