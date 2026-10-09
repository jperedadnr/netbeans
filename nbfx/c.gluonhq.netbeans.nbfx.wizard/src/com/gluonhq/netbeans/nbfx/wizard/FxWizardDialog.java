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
package com.gluonhq.netbeans.nbfx.wizard;

import java.util.List;
import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.openide.util.NbBundle;

/**
 * Runs an {@link FxWizard} in a modal dialog: pages with Back / Next / Finish / Cancel, gating
 * Next and Finish on page validity, the way the NetBeans wizard framework does.
 *
 * @since 1.0
 */
public final class FxWizardDialog {

    private static final ButtonType BACK = new ButtonType(
            NbBundle.getMessage(FxWizardDialog.class, "Wizard.back"), ButtonBar.ButtonData.BACK_PREVIOUS);
    private static final ButtonType NEXT = new ButtonType(
            NbBundle.getMessage(FxWizardDialog.class, "Wizard.next"), ButtonBar.ButtonData.NEXT_FORWARD);
    private static final ButtonType FINISH = new ButtonType(
            NbBundle.getMessage(FxWizardDialog.class, "Wizard.finish"), ButtonBar.ButtonData.FINISH);
    private static final ButtonType CANCEL = ButtonType.CANCEL;

    private FxWizardDialog() {
    }

    /** Shows {@code wizard} and, if the user presses Finish, runs it. Must run on the FX thread. */
    public static void show(FxWizard wizard) {
        List<FxWizardPanel> pages = wizard.getPages();
        if (pages == null || pages.isEmpty()) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(wizard.getDisplayName());
        dialog.setResizable(true);
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(BACK, NEXT, FINISH, CANCEL);

        Label title = new Label();
        title.getStyleClass().add("wizard-page-title");
        StackPane content = new StackPane();
        content.setMinSize(440, 260);
        BorderPane root = new BorderPane(content);
        root.setTop(title);
        BorderPane.setMargin(title, new Insets(0, 0, 8, 0));
        pane.setContent(root);

        int[] index = {0};
        boolean[] visited = new boolean[pages.size()];
        Runnable update = () -> {
            FxWizardPanel page = pages.get(index[0]);
            visited[index[0]] = true;
            title.setText(page.getTitle());
            content.getChildren().setAll(page.getComponent());
            button(pane, BACK).setDisable(index[0] == 0);
            button(pane, NEXT).setDisable(index[0] >= pages.size() - 1 || !page.isValid());
            button(pane, FINISH).setDisable(!allValid(pages, visited));
        };

        button(pane, BACK).addEventFilter(ActionEvent.ACTION, event -> {
            if (index[0] > 0) {
                index[0]--;
                update.run();
            }
            event.consume();
        });
        button(pane, NEXT).addEventFilter(ActionEvent.ACTION, event -> {
            if (index[0] < pages.size() - 1 && pages.get(index[0]).isValid()) {
                index[0]++;
                update.run();
            }
            event.consume();
        });

        update.run();
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.orElse(null) == FINISH) {
            wizard.finish();
        }
    }

    private static boolean allValid(List<FxWizardPanel> pages, boolean[] visited) {
        for (int i = 0; i < pages.size(); i++) {
            if (visited[i] && !pages.get(i).isValid()) {
                return false;
            }
        }
        return true;
    }

    private static Button button(DialogPane pane, ButtonType type) {
        Node node = pane.lookupButton(type);
        return node instanceof Button button ? button : new Button();
    }
}
