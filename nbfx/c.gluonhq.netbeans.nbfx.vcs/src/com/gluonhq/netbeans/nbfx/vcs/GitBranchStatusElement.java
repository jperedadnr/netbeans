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
package com.gluonhq.netbeans.nbfx.vcs;

import com.gluonhq.netbeans.nbfx.annotations.FxStatusAlignment;
import com.gluonhq.netbeans.nbfx.annotations.FxStatusRegistration;
import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import com.gluonhq.netbeans.nbfx.statusbar.FxStatusElement;
import java.nio.file.Path;
import java.nio.file.Paths;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * Status-bar element showing the current Git branch of the selected project. Self-contained: it
 * follows the {@link ProjectRegistry}'s selection and queries git on a background thread.
 */
@FxStatusRegistration(id = "vcs.branch", alignment = FxStatusAlignment.RIGHT, position = 100)
public final class GitBranchStatusElement implements FxStatusElement {

    private static final RequestProcessor RP = new RequestProcessor("nbfx-git-branch", 1, true, true);

    private final Label label = new Label();
    private final ChangeListener<OpenProject> listener = (observable, old, now) -> refresh(now);

    /** Creates the element. Must be called on the JavaFX Application Thread. */
    public GitBranchStatusElement() {
        label.getStyleClass().add("status-bar-vcs");
        label.setVisible(false);
        label.setManaged(false);
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        if (registry != null) {
            registry.selectedProjectProperty().addListener(listener);
        }
        refresh(registry == null ? null : registry.getSelected());
    }

    @Override
    public String getId() {
        return "vcs.branch";
    }

    @Override
    public Node getNode() {
        return label;
    }

    @Override
    public void dispose() {
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        if (registry != null) {
            registry.selectedProjectProperty().removeListener(listener);
        }
    }

    private void refresh(OpenProject project) {
        Path dir = project == null ? null : Paths.get(project.getPath());
        RP.post(() -> {
            String text = "";
            if (dir != null && GitClient.isRepository(dir)) {
                String branch = GitClient.branch(GitClient.rootOf(dir));
                if (branch != null && !branch.isBlank()) {
                    text = NbBundle.getMessage(GitBranchStatusElement.class, "GitBranchStatusElement.branch", branch);
                }
            }
            String finalText = text;
            Platform.runLater(() -> {
                label.setText(finalText);
                label.setVisible(!finalText.isEmpty());
                label.setManaged(!finalText.isEmpty());
            });
        });
    }
}
