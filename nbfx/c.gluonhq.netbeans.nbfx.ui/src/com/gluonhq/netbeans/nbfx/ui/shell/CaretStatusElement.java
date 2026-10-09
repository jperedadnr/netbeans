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

import com.gluonhq.netbeans.nbfx.api.editor.CaretInfo;
import com.gluonhq.netbeans.nbfx.statusbar.FxStatusElement;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import org.openide.util.NbBundle;

/**
 * Shows the caret position of the document being edited. Created and driven by the main window; not
 * an exported API.
 */
public final class CaretStatusElement implements FxStatusElement {

    private final Label caretPosition = new Label();
    private final HBox group;

    /** Creates the element. Must be called on the JavaFX Application Thread. */
    public CaretStatusElement() {
        caretPosition.getStyleClass().add("status-bar-caret");
        caretPosition.setTooltip(new Tooltip(
                NbBundle.getMessage(CaretStatusElement.class, "StatusBar.caret.tooltip")));
        Separator separator = new Separator(Orientation.VERTICAL);
        separator.getStyleClass().add("status-bar-separator");
        group = new HBox(separator, caretPosition);
        group.getStyleClass().add("status-bar-caret-group");
        group.setAlignment(Pos.CENTER_RIGHT);
        group.setMaxWidth(Region.USE_PREF_SIZE);
        setInfo(null);
    }

    @Override
    public String getId() {
        return "caret";
    }

    @Override
    public Node getNode() {
        return group;
    }

    /** Shows the caret position and selection size, or clears the area when {@code info} is null. */
    public void setInfo(CaretInfo info) {
        StatusElementSupport.runOnFxThread(() -> {
            caretPosition.setText(format(info));
            group.setVisible(info != null);
            group.setManaged(info != null);
        });
    }

    /**
     * The caret position as shown in the status bar: {@code row:column}, and
     * {@code row:column/rows:columns} when there is a selection.
     */
    public static String format(CaretInfo info) {
        if (info == null) {
            return "";
        }
        String caret = info.row() + ":" + info.column();
        return info.hasSelection()
                ? caret + "/" + info.selectedRows() + ":" + info.selectedColumns()
                : caret;
    }
}
