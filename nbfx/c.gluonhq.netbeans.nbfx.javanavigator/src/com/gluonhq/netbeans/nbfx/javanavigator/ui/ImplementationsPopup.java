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
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.Window;

/**
 * The Implementors/Overridders popup of Go to Implementation, after NetBeans' {@code IsOverriddenPopup}:
 * a titled list of the implementations, each with its glyph, its name and, dimmed, what encloses
 * it, shown at the caret or the click. A click or Enter opens the selected one; Escape, or a
 * click elsewhere, dismisses it.
 */
final class ImplementationsPopup {

    private static final int ROW_HEIGHT = 22;
    private static final int VISIBLE_ROWS = 12;
    /** What a row takes besides its two texts: the cell's padding, the glyph, the badge and the gaps between them. */
    private static final double ROW_CHROME = 14 + 16 + 4 + 7 + 4 + 4 + 8;
    private static final double SCROLL_BAR = 16;
    /** The popup showing, if any: a new one replaces it, so two clicks never leave two of them up. */
    private static Popup current;

    private ImplementationsPopup() {
    }

    /**
     * Shows the popup titled {@code title} listing {@code items} at {@code anchor} (screen
     * coordinates; the focused window when {@code null}), calling {@code onChosen} with the one picked.
     */
    static void show(String title, List<GoToResolver.Implementation> items, Point2D anchor,
            Consumer<GoToResolver.Implementation> onChosen) {
        Window owner = Window.getWindows().stream().filter(Window::isFocused).findFirst()
                .orElseGet(() -> Window.getWindows().stream().filter(Window::isShowing).findFirst().orElse(null));
        if (owner == null) {
            return;
        }
        if (current != null) {
            current.hide();
        }
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setHideOnEscape(true);
        current = popup;
        popup.setOnHidden(e -> {
            if (current == popup) {
                current = null;
            }
        });

        Label heading = new Label(title);
        heading.getStyleClass().add("implementations-title");
        heading.setMaxWidth(Double.MAX_VALUE);

        ListView<GoToResolver.Implementation> list = new ListView<>(FXCollections.observableArrayList(items));
        list.setCellFactory(view -> new ImplementationCell());
        list.setFixedCellSize(ROW_HEIGHT);
        Point2D at = anchor != null ? anchor
                : new Point2D(owner.getX() + owner.getWidth() / 3, owner.getY() + owner.getHeight() / 3);
        // Wide enough for the widest row, so no horizontal bar eats into the rows' height; tall
        // enough for all the rows up to VISIBLE_ROWS, so no vertical bar shows before that.
        int rows = items.size();
        boolean scrolls = rows > VISIBLE_ROWS;
        double widest = ROW_CHROME + (scrolls ? SCROLL_BAR : 0) + items.stream()
                .mapToDouble(item -> textWidth(item.name()) + 4 + textWidth("(" + item.enclosing() + ")"))
                .max().orElse(0);
        double available = Screen.getScreensForRectangle(at.getX(), at.getY(), 1, 1).stream()
                .findFirst().map(screen -> screen.getVisualBounds().getMaxX() - at.getX() - 8).orElse(Double.MAX_VALUE);
        boolean clipped = widest > available;
        list.setPrefWidth(Math.min(widest, available));
        list.setPrefHeight(Math.min(rows, VISIBLE_ROWS) * ROW_HEIGHT + 2 + (clipped ? SCROLL_BAR : 0));
        Runnable choose = () -> {
            GoToResolver.Implementation chosen = list.getSelectionModel().getSelectedItem();
            if (chosen != null) {
                popup.hide();
                onChosen.accept(chosen);
            }
        };
        list.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                choose.run();
            }
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                choose.run();
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                e.consume();
            }
        });

        VBox box = new VBox(heading, list);
        box.getStyleClass().add("implementations-popup");
        box.getStylesheets().add(Objects.requireNonNull(NavigatorView.class.getResource("navigator.css")).toExternalForm());
        popup.getContent().add(box);

        popup.show(owner, at.getX(), at.getY());
        list.getSelectionModel().select(0);
        list.requestFocus();
    }

    /** The width of {@code text} in the default font, the rows' font. */
    private static double textWidth(String text) {
        Text measured = new Text(text);
        measured.setFont(Font.getDefault());
        return Math.ceil(measured.getLayoutBounds().getWidth());
    }
}
