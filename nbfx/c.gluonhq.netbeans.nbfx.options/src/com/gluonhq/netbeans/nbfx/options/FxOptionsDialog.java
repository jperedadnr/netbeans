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
package com.gluonhq.netbeans.nbfx.options;

import java.util.List;
import java.util.Optional;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.StackPane;
import org.openide.util.NbBundle;

/**
 * The Options dialog: a category list on the left and the selected panel on the right, with
 * OK / Apply / Cancel, the way the NetBeans Options dialog works.
 *
 * @since 1.0
 */
public final class FxOptionsDialog {

    private FxOptionsDialog() {
    }

    /** Shows the Options dialog. Must run on the JavaFX Application Thread. */
    public static void show() {
        List<OptionsRegistration> panels = OptionsRegistry.discover();
        if (panels.isEmpty()) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(message("Options.title"));
        dialog.setResizable(true);
        DialogPane pane = dialog.getDialogPane();
        ButtonType apply = new ButtonType(message("Options.apply"), ButtonBar.ButtonData.APPLY);
        pane.getButtonTypes().addAll(ButtonType.OK, apply, ButtonType.CANCEL);
        pane.setPrefSize(780, 540);

        ListView<OptionsRegistration> list = new ListView<>(FXCollections.observableArrayList(panels));
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(OptionsRegistration item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.displayName());
            }
        });
        StackPane content = new StackPane();
        list.getSelectionModel().selectedItemProperty().addListener((observable, old, now) -> {
            if (now != null) {
                now.panel().load();
                content.getChildren().setAll(now.panel().getComponent());
            }
        });
        list.getSelectionModel().selectFirst();

        SplitPane split = new SplitPane(list, content);
        split.setDividerPositions(0.3);
        pane.setContent(split);

        Button applyButton = (Button) pane.lookupButton(apply);
        applyButton.addEventFilter(ActionEvent.ACTION, event -> {
            applyAll(panels);
            event.consume();
        });

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.orElse(ButtonType.CANCEL) == ButtonType.CANCEL) {
            for (OptionsRegistration registration : panels) {
                registration.panel().cancel();
            }
        } else {
            applyAll(panels);
        }
    }

    private static void applyAll(List<OptionsRegistration> panels) {
        for (OptionsRegistration registration : panels) {
            if (registration.panel().isValid()) {
                registration.panel().apply();
            }
        }
    }

    private static String message(String key) {
        return NbBundle.getMessage(FxOptionsDialog.class, key);
    }
}
