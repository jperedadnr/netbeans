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
import javafx.scene.Node;
import javafx.scene.control.Label;

/**
 * Names the selected project - the one the project-scoped actions apply to. Created and driven by
 * the main window; not an exported API.
 */
public final class ProjectStatusElement implements FxStatusElement {

    private final Label label = new Label();

    /** Creates the element. Must be called on the JavaFX Application Thread. */
    public ProjectStatusElement() {
        label.getStyleClass().add("status-bar-project");
        setProject(null);
    }

    @Override
    public String getId() {
        return "project";
    }

    @Override
    public Node getNode() {
        return label;
    }

    /** Names the selected project, or clears the area when {@code name} is {@code null}. */
    public void setProject(String name) {
        StatusElementSupport.runOnFxThread(() -> {
            label.setText(name == null ? "" : name);
            label.setVisible(name != null);
            label.setManaged(name != null);
        });
    }
}
