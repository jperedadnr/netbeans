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
package com.gluonhq.netbeans.nbfx.ui.shell;

import com.gluonhq.netbeans.nbfx.statusbar.FxStatusElement;
import java.util.function.Consumer;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.openide.util.NbBundle;

/**
 * Names the line separator of the document being edited and changes it from a popup. Created and
 * driven by the main window; not an exported API.
 */
public final class LineSeparatorStatusElement implements FxStatusElement {

    private final Label lineSeparator = new Label();
    private final HBox group;
    private final ContextMenu menu = new ContextMenu();
    private Consumer<String> onChange;

    /** Creates the element. Must be called on the JavaFX Application Thread. */
    public LineSeparatorStatusElement() {
        lineSeparator.getStyleClass().add("status-bar-line-separator");
        lineSeparator.setTooltip(new Tooltip(
                NbBundle.getMessage(LineSeparatorStatusElement.class, "StatusBar.lineSeparator.tooltip")));
        Separator separator = new Separator(Orientation.VERTICAL);
        separator.getStyleClass().add("status-bar-separator");
        group = new HBox(separator, lineSeparator);
        group.getStyleClass().addAll("status-bar-caret-group", "status-bar-line-separator-group");
        group.setAlignment(Pos.CENTER_RIGHT);
        group.setMaxWidth(Region.USE_PREF_SIZE);
        setSeparator(null);

        for (String value : new String[] {"\n", "\r", "\r\n"}) {
            MenuItem item = new MenuItem(name(value));
            item.setOnAction(_ -> {
                if (onChange != null) {
                    onChange.accept(value);
                }
            });
            menu.getItems().add(item);
        }
        group.setOnMouseClicked(_ -> menu.show(group, Side.TOP, 0, 0));
    }

    @Override
    public String getId() {
        return "line-separator";
    }

    @Override
    public Node getNode() {
        return group;
    }

    /** Names the line separator, or clears the area when {@code separator} is {@code null}. */
    public void setSeparator(String separator) {
        StatusElementSupport.runOnFxThread(() -> {
            String label = name(separator);
            lineSeparator.setText(label == null ? "" : label);
            group.setVisible(label != null);
            group.setManaged(label != null);
        });
    }

    /** Registers the action invoked when the user picks a separator from the popup. */
    public void setOnChange(Consumer<String> onChange) {
        this.onChange = onChange;
    }

    /** The display name of a line separator, or {@code null} when it is {@code null} or unknown. */
    public static String name(String separator) {
        return switch (separator) {
            case "\n" -> NbBundle.getMessage(LineSeparatorStatusElement.class, "StatusBar.lineSeparator.lf");
            case "\r" -> NbBundle.getMessage(LineSeparatorStatusElement.class, "StatusBar.lineSeparator.cr");
            case "\r\n" -> NbBundle.getMessage(LineSeparatorStatusElement.class, "StatusBar.lineSeparator.crlf");
            case null, default -> null;
        };
    }
}
