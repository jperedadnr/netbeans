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
package com.gluonhq.netbeans.nbfx.structure.gradle;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.project.ui.gradle.api.GradleTasks;
import com.gluonhq.netbeans.nbfx.run.BuildRunner;
import com.gluonhq.netbeans.nbfx.structure.NavigatorIcons;
import com.gluonhq.netbeans.nbfx.structure.ScanningPanel;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.gradle.api.GradleBaseProject;
import org.netbeans.modules.gradle.api.NbGradleProject;
import org.netbeans.modules.gradle.api.NbGradleProject.Quality;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;

/**
 * The Related tasks panel, after NetBeans' {@code TasksPanel}: a tree with the tasks of the build
 * script's project under <em>Tasks of project</em>, one folder per task group (<em>Other Tasks</em>
 * last for the tasks without one), a task running on double-click, Enter or <em>Run Task</em>. A
 * project not yet evaluated is asked to load, the tree filling once it has; the tree is read again
 * whenever the project reloads its model.
 */
final class GradleTasksUI extends BorderPane implements ScanningPanel<GradleTasksUI.Result> {

    /**
     * The scan of a build script: the tasks of its project by group, with the project's display
     * name and root directory; no project for a script outside a Gradle project; {@code loading}
     * while the project is being evaluated for the panel.
     */
    record Result(FileObject file, Project project, String projectName, File rootDir, List<TaskGroups.Group> groups,
            boolean loading) {
    }

    /** The value of the tree's root row. */
    private record Root(String name) {
    }

    private static final Logger LOG = Logger.getLogger(GradleTasksUI.class.getName());

    private final TreeView<Object> tree = new TreeView<>();
    private final Label wait = new Label(message("LBL_Wait"));
    private final Label empty = new Label(message("LBL_NoProject"));
    private final Runnable rescan;
    private final PropertyChangeListener projectListener = e -> {
        if (NbGradleProject.PROP_PROJECT_INFO.equals(e.getPropertyName())) {
            projectChanged();
        }
    };
    /** The groups the user collapsed, by name: a rebuilt tree keeps them so. */
    private final Set<String> collapsed = new HashSet<>();
    private Result result;
    private NbGradleProject watched;
    /** The project this panel asked to evaluate, asked once: a load that fails must not loop. */
    private volatile Project requested;

    /** @param rescan scans the script again: the project (re)loaded its model */
    GradleTasksUI(Runnable rescan) {
        this.rescan = rescan;

        tree.setMinHeight(0);
        tree.getStyleClass().add("navigator-tree");
        tree.setShowRoot(true);
        tree.setCellFactory(view -> new TaskCell());
        tree.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                run(selectedTask());
            }
        });
        tree.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                run(selectedTask());
                e.consume();
            }
        });
        MenuItem runItem = new MenuItem(message("ACT_Execute"));
        runItem.setOnAction(e -> run(selectedTask()));
        ContextMenu contextMenu = new ContextMenu(runItem);
        contextMenu.setOnShowing(e -> runItem.setDisable(selectedTask() == null));
        tree.setContextMenu(contextMenu);

        wait.getStyleClass().add("navigator-empty");
        wait.setVisible(false);
        empty.getStyleClass().add("navigator-empty");
        empty.setVisible(false);
        StackPane center = new StackPane(tree, wait, empty);
        StackPane.setAlignment(wait, Pos.CENTER);
        StackPane.setAlignment(empty, Pos.CENTER);
        center.setMinHeight(0);
        setCenter(center);
        setMinHeight(0);
    }

    @Override
    public Node getNode() {
        return this;
    }

    @Override
    public Result scan(FileObject file, String text, Cancellation cancellation) throws IOException {
        Project project = projectOf(file);
        NbGradleProject gradle = project == null ? null : NbGradleProject.get(project);
        if (gradle == null) {
            return new Result(file, null, null, null, List.of(), false);
        }
        boolean loading = false;
        if (gradle.getQuality().worseThan(Quality.EVALUATED) && requested != project) {
            // The project has not run its build script yet (nothing opened it, or the IDE just
            // started): ask for it, and scan again once it has, whether or not the load succeeded.
            requested = project;
            loading = true;
            gradle.toQuality(message("LBL_LoadReason"), Quality.EVALUATED, false)
                    .whenComplete((p, failure) -> Platform.runLater(rescan));
        }
        GradleBaseProject base = GradleBaseProject.get(project);
        List<TaskGroups.Task> tasks = new ArrayList<>();
        if (base != null) {
            base.getTasks().forEach(task -> tasks.add(TaskGroups.Task.of(task)));
        }
        if (cancellation.isCancelled()) {
            return null;
        }
        String name = ProjectUtils.getInformation(project).getDisplayName();
        File rootDir = base == null ? null : base.getRootDir();
        return new Result(file, project, name, rootDir, TaskGroups.of(tasks), loading);
    }

    private static Project projectOf(FileObject file) {
        FileObject folder = file.getParent();
        if (folder == null) {
            return null;
        }
        try {
            return ProjectManager.getDefault().findProject(folder);
        } catch (IOException | IllegalArgumentException ex) {
            LOG.log(Level.FINE, "No project for " + file.getPath(), ex);
            return null;
        }
    }

    @Override
    public void show(Result newResult) {
        result = newResult;
        watch(newResult == null || newResult.project() == null ? null : NbGradleProject.get(newResult.project()));
        wait.setVisible(newResult != null && newResult.loading() && newResult.groups().isEmpty());
        empty.setVisible(newResult != null && newResult.project() == null);
        rebuild();
    }

    @Override
    public void clear() {
        result = null;
        watch(null);
        wait.setVisible(false);
        empty.setVisible(false);
        tree.setRoot(null);
    }

    @Override
    public void selectAt(int offset) {
        // Tasks have no place in the script: the caret selects none.
    }

    /** Builds the tree from {@code result}, keeping the collapsed groups and the selected task. */
    private void rebuild() {
        if (result == null || result.project() == null) {
            tree.setRoot(null);
            return;
        }
        TaskGroups.Task selected = selectedTask();
        TreeItem<Object> root = new TreeItem<>(new Root(message("LBL_Tasks", result.projectName())));
        TreeItem<Object> reselect = null;
        for (TaskGroups.Group group : result.groups()) {
            TreeItem<Object> groupItem = new TreeItem<>(group);
            for (TaskGroups.Task task : group.tasks()) {
                TreeItem<Object> taskItem = new TreeItem<>(task);
                groupItem.getChildren().add(taskItem);
                if (selected != null && selected.path().equals(task.path())) {
                    reselect = taskItem;
                }
            }
            groupItem.setExpanded(!collapsed.contains(group.name()));
            groupItem.expandedProperty().subscribe(expanded -> {
                if (expanded) {
                    collapsed.remove(group.name());
                } else {
                    collapsed.add(group.name());
                }
            });
            root.getChildren().add(groupItem);
        }
        root.setExpanded(true);
        tree.setRoot(root);
        if (reselect != null) {
            tree.getSelectionModel().select(reselect);
        }
    }

    private TaskGroups.Task selectedTask() {
        TreeItem<Object> item = tree.getSelectionModel().getSelectedItem();
        return item != null && item.getValue() instanceof TaskGroups.Task task ? task : null;
    }

    private void projectChanged() {
        Platform.runLater(rescan);
    }

    /** Follows {@code project}'s model: a reload lists the tasks again. */
    private void watch(NbGradleProject project) {
        if (project == watched) {
            return;
        }
        if (watched != null) {
            watched.removePropertyChangeListener(projectListener);
        }
        watched = project;
        if (watched != null) {
            watched.addPropertyChangeListener(projectListener);
        }
    }

    /**
     * Runs {@code task} by its path from the build's root directory (where the wrapper is), in a
     * console of its own.
     */
    private void run(TaskGroups.Task task) {
        if (task == null || result == null || result.project() == null) {
            return;
        }
        File directory = result.rootDir() != null ? result.rootDir() : FileUtil.toFile(result.project().getProjectDirectory());
        if (directory == null) {
            return;
        }
        Path dir = directory.toPath();
        String consoleName = task.name() + " " + result.projectName();
        BuildRunner.run(dir, consoleName, (output, progress) -> GradleTasks.start(dir, List.of(task.path()), output, progress));
    }

    static String message(String key, Object... args) {
        return NbBundle.getMessage(GradleTasksUI.class, key, args);
    }

    /** A row: the Gradle, folder or task icon and the name; the task's description as the tooltip. The nodes are reused. */
    private static final class TaskCell extends TreeCell<Object> {

        private final ImageView icon = new ImageView();
        private final Label name = new Label();
        private final HBox row;
        private final Tooltip tooltip = new Tooltip();

        TaskCell() {
            getStyleClass().add("navigator-cell");
            icon.setFitWidth(NavigatorIcons.SIZE);
            icon.setFitHeight(NavigatorIcons.SIZE);
            icon.setPreserveRatio(true);
            name.getStyleClass().add("navigator-text");
            row = new HBox(4, icon, name);
            row.setAlignment(Pos.CENTER_LEFT);
            setText(null);
        }

        @Override
        protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            String tip = null;
            switch (item) {
                case Root root -> {
                    name.setText(root.name());
                    icon.setImage(NavigatorIcons.image(GradleTasksUI.class, "icons/gradle.png"));
                }
                case TaskGroups.Group group -> {
                    name.setText(group.other() ? message("LBL_PrivateTasks") : TaskGroups.capitalize(group.name()));
                    icon.setImage(NavigatorIcons.image(GradleTasksUI.class, "icons/folder.png"));
                }
                case TaskGroups.Task task -> {
                    name.setText(task.name());
                    icon.setImage(NavigatorIcons.image(GradleTasksUI.class, "icons/task.png"));
                    String description = task.description();
                    tip = description == null || description.isBlank()
                            ? message("TOOLTIP_Task", task.path())
                            : message("TOOLTIP_Task", task.path()) + "\n" + description;
                }
                default -> name.setText(String.valueOf(item));
            }
            setGraphic(row);
            if (tip == null) {
                setTooltip(null);
            } else {
                tooltip.setText(tip);
                setTooltip(tooltip);
            }
        }
    }
}
