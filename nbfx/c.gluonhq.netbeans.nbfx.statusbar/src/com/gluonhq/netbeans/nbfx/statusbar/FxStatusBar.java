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
package com.gluonhq.netbeans.nbfx.statusbar;

import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * A registry-driven JavaFX status bar with three slots (left, centre, right).
 * <p>
 * The elements are supplied by {@link StatusElementRegistry} (or passed in directly, for tests and
 * detached windows). The bar is a plain JavaFX component; nothing about it is Swing.
 * <p>
 * The no-argument constructor and every method that creates a node must be called on the JavaFX
 * Application Thread.
 *
 * @since 1.0
 */
public class FxStatusBar extends BorderPane {

    private final List<StatusElement> elements;

    /** Builds the status bar from every element registered under {@code NbFx/Status}. */
    public FxStatusBar() {
        this(StatusElementRegistry.discover());
    }

    /**
     * Builds the status bar from {@code elements}.
     *
     * @param elements the elements to place, in discovery order
     */
    public FxStatusBar(List<StatusElement> elements) {
        this.elements = List.copyOf(elements);
        getStyleClass().add("status-bar");

        HBox left = slot(Pos.CENTER_LEFT, "status-bar-left");
        HBox center = slot(Pos.CENTER, "status-bar-center");
        HBox right = slot(Pos.CENTER_RIGHT, "status-bar-right");

        for (StatusElement registered : this.elements) {
            Node node = registered.element().getNode();
            if (node == null) {
                continue;
            }
            switch (registered.alignment()) {
                case LEFT -> left.getChildren().add(node);
                case CENTER -> center.getChildren().add(node);
                case RIGHT -> right.getChildren().add(node);
            }
        }

        setLeft(left);
        setCenter(center);
        setRight(right);
        BorderPane.setAlignment(left, Pos.CENTER_LEFT);
        BorderPane.setAlignment(center, Pos.CENTER);
        BorderPane.setAlignment(right, Pos.CENTER_RIGHT);
    }

    /** The elements this bar was built from, in order. */
    public final List<StatusElement> elements() {
        return elements;
    }

    /** Disposes every element; called when the window using this status bar is closed. */
    public void dispose() {
        for (StatusElement registered : elements) {
            registered.element().dispose();
        }
    }

    private static HBox slot(Pos alignment, String styleClass) {
        HBox box = new HBox();
        box.getStyleClass().add(styleClass);
        box.setAlignment(alignment);
        box.setFillHeight(true);
        if (alignment == Pos.CENTER_LEFT || alignment == Pos.CENTER_RIGHT) {
            box.setMaxWidth(Region.USE_PREF_SIZE);
        } else {
            HBox.setHgrow(box, Priority.ALWAYS);
        }
        return box;
    }
}
