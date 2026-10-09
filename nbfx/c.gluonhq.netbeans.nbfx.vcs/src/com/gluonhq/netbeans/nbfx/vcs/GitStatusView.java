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

import com.gluonhq.netbeans.nbfx.api.ErrorReporter;
import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.project.ProjectRegistry;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;

/**
 * The Git view: the selected project's branch and changed files, with Refresh and Commit. It runs
 * the {@code git} command line off the FX thread and reflects the result in the table.
 *
 * @since 1.0
 */
final class GitStatusView extends BorderPane {

    private static final RequestProcessor RP = new RequestProcessor("nbfx-git", 1);

    private final ObservableList<GitClient.Change> changes = FXCollections.observableArrayList();
    private final TableView<GitClient.Change> table = new TableView<>(changes);
    private final Label branch = new Label();
    private final Button commit = new Button(message("Git.commit"));

    GitStatusView() {
        getStyleClass().add("git-view");

        TableColumn<GitClient.Change, String> status = new TableColumn<>(message("Git.column.status"));
        status.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status()));
        status.setPrefWidth(140);
        TableColumn<GitClient.Change, String> file = new TableColumn<>(message("Git.column.file"));
        file.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().path()));
        table.getColumns().add(status);
        table.getColumns().add(file);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        Button refresh = new Button(message("Git.refresh"));
        refresh.setOnAction(event -> reload());
        commit.setOnAction(event -> commitDialog());
        commit.setDisable(true);
        setTop(new ToolBar(branch, refresh, commit));
        setCenter(table);

        reload();
    }

    /** Reads the selected project's git status off the FX thread and fills the table. */
    void reload() {
        Path dir = selectedProject();
        if (dir == null) {
            branch.setText("");
            changes.clear();
            commit.setDisable(true);
            return;
        }
        RP.post(() -> {
            try {
                if (!GitClient.isRepository(dir)) {
                    Platform.runLater(() -> {
                        branch.setText(message("Git.notRepository"));
                        changes.clear();
                        commit.setDisable(true);
                    });
                    return;
                }
                Path root = GitClient.rootOf(dir);
                String branchName = GitClient.branch(root);
                List<GitClient.Change> status = GitClient.status(root);
                Platform.runLater(() -> {
                    branch.setText(message("Git.branch") + ": " + branchName);
                    changes.setAll(status);
                    commit.setDisable(status.isEmpty());
                });
            } catch (RuntimeException | java.io.IOException ex) {
                Platform.runLater(() -> ErrorReporter.report(message("Git.title"),
                        message("Git.error"), ex.getMessage(), ex));
            }
        });
    }

    private void commitDialog() {
        Path dir = selectedProject();
        if (dir == null) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(message("Git.commit.title"));
        DialogPane pane = dialog.getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        TextArea messageArea = new TextArea();
        messageArea.setPromptText(message("Git.commit.message"));
        messageArea.setPrefRowCount(4);
        VBox.setVgrow(messageArea, Priority.ALWAYS);
        pane.setContent(new VBox(messageArea));
        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK && !messageArea.getText().isBlank()) {
                commit(dir, messageArea.getText().trim());
            }
        });
    }

    private void commit(Path dir, String message) {
        RP.post(() -> {
            Path root = GitClient.rootOf(dir);
            GitClient.Result add = GitClient.addAll(root);
            if (add.exitCode() != 0) {
                Platform.runLater(() -> ErrorReporter.report(message("Git.title"),
                        message("Git.error"), add.output(), null));
                return;
            }
            GitClient.Result commit = GitClient.commit(root, message);
            if (commit.exitCode() != 0) {
                Platform.runLater(() -> ErrorReporter.report(message("Git.title"),
                        message("Git.error"), commit.output(), null));
            }
            Platform.runLater(this::reload);
        });
    }

    private static Path selectedProject() {
        ProjectRegistry registry = Lookup.getDefault().lookup(ProjectRegistry.class);
        OpenProject project = registry == null ? null : registry.getSelected();
        return project == null ? null : Paths.get(project.getPath());
    }

    private static String message(String key) {
        return NbBundle.getMessage(GitStatusView.class, key);
    }
}
