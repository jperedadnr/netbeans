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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import org.openide.util.NbBundle;

/**
 * Shows the progress of a long-running activity, with an optional cancel button. Created and driven
 * by the main window; not an exported API.
 */
public final class ProgressStatusElement implements FxStatusElement {

    private final Label legend = new Label();
    private final ProgressBar progressBar = new ProgressBar();
    private final Button cancelButton = new Button();
    private final HBox group;
    private Runnable onCancel;

    /** Creates the element. Must be called on the JavaFX Application Thread. */
    public ProgressStatusElement() {
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        progressBar.getStyleClass().add("status-bar-progress");
        legend.getStyleClass().add("status-bar-legend");
        cancelButton.getStyleClass().add("status-bar-cancel");
        cancelButton.setText("\u2715");
        cancelButton.setFocusTraversable(false);
        cancelButton.setTooltip(new Tooltip(
                NbBundle.getMessage(ProgressStatusElement.class, "StatusBar.cancel.tooltip")));
        cancelButton.setOnAction(_ -> {
            if (onCancel != null) {
                onCancel.run();
            }
        });
        group = new HBox(legend, progressBar, cancelButton);
        group.getStyleClass().add("status-bar-progress-group");
        group.setAlignment(Pos.CENTER);
        setVisible(false);
    }

    @Override
    public String getId() {
        return "progress";
    }

    @Override
    public Node getNode() {
        return group;
    }

    /**
     * Shows the progress area with the given legend, running {@code onCancel} when cancel is pressed;
     * when {@code onCancel} is {@code null} the cancel button is hidden.
     */
    public void show(String legendText, Runnable onCancel) {
        StatusElementSupport.runOnFxThread(() -> {
            this.onCancel = onCancel;
            cancelButton.setVisible(onCancel != null);
            cancelButton.setManaged(onCancel != null);
            legend.setText(legendText);
            setVisible(true);
        });
    }

    /** Hides the progress area. */
    public void hide() {
        StatusElementSupport.runOnFxThread(() -> {
            this.onCancel = null;
            setVisible(false);
        });
    }

    private void setVisible(boolean visible) {
        group.setVisible(visible);
        group.setManaged(visible);
    }
}
