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
package com.gluonhq.netbeans.nbfx.structure.maven;

import com.gluonhq.netbeans.nbfx.api.Cancellation;
import com.gluonhq.netbeans.nbfx.project.ui.maven.api.MavenGoals;
import com.gluonhq.netbeans.nbfx.run.BuildRunner;
import com.gluonhq.netbeans.nbfx.structure.NavigatorButtons;
import com.gluonhq.netbeans.nbfx.structure.NavigatorIcons;
import com.gluonhq.netbeans.nbfx.structure.ScanningPanel;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.modules.maven.api.NbMavenProject;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.NbBundle;
import org.openide.util.NbPreferences;

/**
 * The Related goals panel, after NetBeans' {@code GoalsPanel}: the goals of the plugins the POM's
 * project uses, one row each as <em>prefix <b>goal</b></em>, with the help goals and the goals an
 * execution binds to the lifecycle hidden unless the two filters along the bottom show them. A goal
 * runs on double-click (or Enter, or <em>Execute Goal</em>); <em>Show Documentation</em> runs
 * {@code help:describe} for it. The list is read again when the project reloads its model.
 */
final class RelatedGoalsUI extends BorderPane implements ScanningPanel<RelatedGoalsUI.Result> {

    /** The scan of a POM: the goals of its project's plugins; no project for a POM outside one. */
    record Result(FileObject file, Project project, List<Mojo> mojos) {
    }

    private static final Logger LOG = Logger.getLogger(RelatedGoalsUI.class.getName());
    private static final String SHOW_HELP_GOALS = "showHelpGoals";
    private static final String SHOW_LIFECYCLE_GOALS = "showLifecycleGoals";

    private final Preferences prefs = NbPreferences.forModule(RelatedGoalsUI.class).node("goalNavigator");
    private final BooleanProperty showHelpGoals = new SimpleBooleanProperty(this, SHOW_HELP_GOALS, false);
    private final BooleanProperty showLifecycleGoals = new SimpleBooleanProperty(this, SHOW_LIFECYCLE_GOALS, false);
    private final ListView<Mojo> list = new ListView<>();
    private final Label wait = new Label(message("LBL_Wait"));
    private final Runnable rescan;
    private final PropertyChangeListener projectListener = e -> {
        if (NbMavenProject.PROP_PROJECT.equals(e.getPropertyName())) {
            projectChanged();
        }
    };
    private Result result;
    private NbMavenProject watched;

    /** @param rescan scans the POM again: the project reloaded its model */
    RelatedGoalsUI(Runnable rescan) {
        this.rescan = rescan;
        showHelpGoals.set(prefs.getBoolean(SHOW_HELP_GOALS, false));
        showLifecycleGoals.set(prefs.getBoolean(SHOW_LIFECYCLE_GOALS, false));
        showHelpGoals.subscribe(on -> {
            prefs.putBoolean(SHOW_HELP_GOALS, on);
            refresh();
        });
        showLifecycleGoals.subscribe(on -> {
            prefs.putBoolean(SHOW_LIFECYCLE_GOALS, on);
            refresh();
        });

        list.setMinHeight(0);
        list.getStyleClass().add("navigator-tree");
        list.setCellFactory(view -> new MojoCell());
        list.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                execute(list.getSelectionModel().getSelectedItem());
            }
        });
        list.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                execute(list.getSelectionModel().getSelectedItem());
                e.consume();
            }
        });
        MenuItem executeItem = new MenuItem(message("ACT_Execute"));
        executeItem.setOnAction(e -> execute(list.getSelectionModel().getSelectedItem()));
        MenuItem documentationItem = new MenuItem(message("ACT_Execute_help"));
        documentationItem.setOnAction(e -> describe(list.getSelectionModel().getSelectedItem()));
        ContextMenu contextMenu = new ContextMenu(executeItem, new SeparatorMenuItem(), documentationItem);
        contextMenu.setOnShowing(e -> {
            boolean none = list.getSelectionModel().getSelectedItem() == null;
            executeItem.setDisable(none);
            documentationItem.setDisable(none);
        });
        list.setContextMenu(contextMenu);

        wait.getStyleClass().add("navigator-empty");
        wait.setVisible(false);
        StackPane center = new StackPane(list, wait);
        StackPane.setAlignment(wait, Pos.CENTER);
        center.setMinHeight(0);
        setCenter(center);
        setBottom(createFilterBar());
        setMinHeight(0);
    }

    private Node createFilterBar() {
        ToggleButton help = NavigatorButtons.toggle(NavigatorIcons.view(RelatedGoalsUI.class, "icons/help.png"),
                message("LBL_Show_Help"));
        help.selectedProperty().bindBidirectional(showHelpGoals);
        ToggleButton lifecycle = NavigatorButtons.toggle(
                NavigatorIcons.view(RelatedGoalsUI.class, "icons/thread_running_16.png"), message("LBL_Show_Lifecycle"));
        lifecycle.selectedProperty().bindBidirectional(showLifecycleGoals);
        return NavigatorButtons.filterBar(help, lifecycle);
    }

    @Override
    public Node getNode() {
        return this;
    }

    @Override
    public Result scan(FileObject file, String text, Cancellation cancellation) throws IOException {
        Platform.runLater(() -> wait.setVisible(true));
        Project project = null;
        FileObject folder = file.getParent();
        if (folder != null) {
            try {
                project = ProjectManager.getDefault().findProject(folder);
            } catch (IOException | IllegalArgumentException ex) {
                LOG.log(Level.FINE, "No project for " + file.getPath(), ex);
            }
        }
        if (project == null) {
            return new Result(file, null, List.of());
        }
        List<Mojo> mojos = MojoScanner.scan(project, cancellation);
        return mojos == null ? null : new Result(file, project, mojos);
    }

    @Override
    public void show(Result newResult) {
        wait.setVisible(false);
        result = newResult;
        watch(newResult == null || newResult.project() == null ? null : newResult.project().getLookup().lookup(NbMavenProject.class));
        refresh();
    }

    @Override
    public void clear() {
        wait.setVisible(false);
        result = null;
        watch(null);
        list.getItems().clear();
    }

    @Override
    public void selectAt(int offset) {
        // Goals have no place in the POM: the caret selects none.
    }

    private void refresh() {
        List<Mojo> shown = new ArrayList<>();
        if (result != null) {
            for (Mojo mojo : result.mojos()) {
                if (mojo.isHelp() && !showHelpGoals.get() || mojo.lifecycleBound() && !showLifecycleGoals.get()) {
                    continue;
                }
                shown.add(mojo);
            }
        }
        list.getItems().setAll(shown);
    }

    private void projectChanged() {
        Platform.runLater(rescan);
    }

    /** Follows {@code project}'s model: a reload lists the goals again. */
    private void watch(NbMavenProject project) {
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

    /** Runs {@code mojo} in its project, in a console of its own. */
    private void execute(Mojo mojo) {
        run(mojo, List.of(mojo == null ? "" : mojo.qualifiedGoal()), Map.of(), mojo == null ? "" : mojo.prefix() + ":" + mojo.goal());
    }

    /** Runs {@code help:describe} for {@code mojo}: Maven prints the goal's documentation to the console. */
    private void describe(Mojo mojo) {
        if (mojo != null) {
            run(mojo, List.of("help:describe"), Map.of("cmd", mojo.qualifiedGoal(), "detail", "true"),
                    "help:describe " + mojo.prefix() + ":" + mojo.goal());
        }
    }

    private void run(Mojo mojo, List<String> goals, Map<String, String> properties, String title) {
        if (mojo == null || result == null || result.project() == null) {
            return;
        }
        File directory = FileUtil.toFile(result.project().getProjectDirectory());
        if (directory == null) {
            return;
        }
        Path dir = directory.toPath();
        String consoleName = title + " " + ProjectUtils.getInformation(result.project()).getDisplayName();
        BuildRunner.run(dir, consoleName, (output, progress) -> MavenGoals.start(dir, goals, properties, output, progress));
    }

    static String message(String key, Object... args) {
        return NbBundle.getMessage(RelatedGoalsUI.class, key, args);
    }

    /** A row: the help, lifecycle or plain goal icon, the prefix and the goal in bold; the nodes are reused. */
    private static final class MojoCell extends ListCell<Mojo> {

        private final ImageView icon = new ImageView();
        private final Label prefix = new Label();
        private final Label goal = new Label();
        private final HBox row;
        private final Tooltip tooltip = new Tooltip();

        MojoCell() {
            getStyleClass().add("navigator-cell");
            icon.setFitWidth(NavigatorIcons.SIZE);
            icon.setFitHeight(NavigatorIcons.SIZE);
            icon.setPreserveRatio(true);
            prefix.getStyleClass().add("navigator-text");
            goal.getStyleClass().add("navigator-text");
            goal.setStyle("-fx-font-weight: bold;");
            HBox label = new HBox(prefix, goal);
            label.setAlignment(Pos.BASELINE_LEFT);
            row = new HBox(4, icon, label);
            row.setAlignment(Pos.CENTER_LEFT);
            setText(null);
        }

        @Override
        protected void updateItem(Mojo mojo, boolean empty) {
            super.updateItem(mojo, empty);
            if (empty || mojo == null) {
                setGraphic(null);
                setTooltip(null);
                return;
            }
            prefix.setText(mojo.prefix() + " ");
            goal.setText(mojo.goal());
            icon.setImage(NavigatorIcons.image(RelatedGoalsUI.class,
                    mojo.isHelp() ? "icons/help.png" : mojo.lifecycleBound() ? "icons/thread_running_16.png" : "icons/mojo.png"));
            setGraphic(row);
            String tip = message("TOOLTIP_Mojo", mojo.pluginId(), mojo.goal(), mojo.prefix());
            tooltip.setText(mojo.lifecycleBound() ? tip + "\n" + message("TOOLTIP_Mojo_lifecycle") : tip);
            setTooltip(tooltip);
        }
    }
}
